package com.alexandrso.amm.movement;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import com.alexandrso.amm.AlexMovementsMod;
import com.alexandrso.amm.config.AmmConfig;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Spiders that crawl on a wall lie belly-to-wall. A hitbox is always an axis-aligned box and
 * cannot be rotated, so instead of rotating it we RESHAPE it to match the lying pose (identically
 * on server and client):
 * <pre>
 *   upright spider : width x height x width        (1.4 x 0.9 x 1.4)
 *   on a wall      : depth(normal) = height (0.9)
 *                    span(along wall) = width (1.4)
 *                    vertical = width (1.4)
 * </pre>
 * The visual tilt is done by the render phase from the state kept here.
 */
@EventBusSubscriber(modid = AlexMovementsMod.MODID)
public final class SpiderWallHandler {
    /** Ticks the spider may lose wall contact before it is considered to have left the wall. */
    private static final int RELEASE_DELAY_TICKS = 6;
    private static final float TRANSITION_TICKS = 3.0F;

    /** Per-spider state. Server and client keep SEPARATE maps (singleplayer has both worlds in one JVM). */
    public static final class Data {
        /** Horizontal direction from the spider TOWARD the wall it is hugging; null = not on a wall. */
        public Direction wall;
        /** Last non-null wall, kept so the render tilt can ease back out after the spider leaves. */
        public Direction renderWall;
        public int lastContactTick;
        /** False until the first tick after the spider was created or loaded. */
        public boolean settled;

        // render data
        public float progress, prevProgress; // 0 = upright, 1 = fully belly-to-wall
        public float theta, prevTheta;       // radians in the wall plane, 0 = head pointing up
        public double lastX, lastY, lastZ;
        public boolean hasLast;

        // Extra leg-walk animation for movement along the wall: vanilla only counts horizontal
        // distance, so a spider crawling straight up kept its legs still.
        public float walkSpeed, prevWalkSpeed;
        public float walkPos;
    }

    private static final Map<Entity, Data> SERVER = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Entity, Data> CLIENT = Collections.synchronizedMap(new WeakHashMap<>());

    private SpiderWallHandler() {
    }

    public static Data get(Entity e) {
        return (e.level().isClientSide() ? CLIENT : SERVER).get(e);
    }

    private static Data getOrCreate(Entity e) {
        return (e.level().isClientSide() ? CLIENT : SERVER).computeIfAbsent(e, k -> new Data());
    }

    private static boolean isSpider(Entity e) {
        return e instanceof Mob && (e.getType() == EntityTypes.SPIDER || e.getType() == EntityTypes.CAVE_SPIDER);
    }

    @SubscribeEvent
    static void onTickPre(EntityTickEvent.Pre event) {
        Entity e = event.getEntity();
        if (!isSpider(e)) return;
        Mob spider = (Mob) e;
        Data d = getOrCreate(spider);

        if (!d.settled) {
            d.settled = true;
            if (!spider.level().isClientSide()) freeFromBlocks(spider);
        }

        if (isEligible(spider)) {
            updateWall(spider, d);
        } else if (d.wall != null) {
            release(spider, d);
        }
        updateRenderData(spider, d);
    }

    /** Moving rebuilds the upright box from the dimensions; put the lying one back at the end of the tick. */
    @SubscribeEvent
    static void onTickPost(EntityTickEvent.Post event) {
        Entity e = event.getEntity();
        if (!isSpider(e)) return;
        Data d = get(e);
        if (d != null && d.wall != null) applyBox(e, d.wall);
    }

    /** The lying box sits closer to the wall, so vanilla's suffocation check can fire: ignore it while on a wall. */
    @SubscribeEvent
    static void onIncoming(LivingIncomingDamageEvent event) {
        if (!event.getSource().is(DamageTypes.IN_WALL) || !isSpider(event.getEntity())) return;
        Data d = get(event.getEntity());
        if (d != null && d.wall != null) event.setCanceled(true);
    }

    // ── logic ───────────────────────────────────────────────────────────────────

    private static boolean isEligible(Mob spider) {
        if (!AmmConfig.SPIDER_WALL_TILT_ENABLED.get()) return false;
        if (!spider.isAlive() || spider.isVehicle() || spider.isPassenger() || spider.isInWater()) return false;
        String namespace = BuiltInRegistries.ENTITY_TYPE.getKey(spider.getType()).getNamespace();
        for (String mod : AmmConfig.SPIDER_WALL_TILT_EXCLUDED_MODS.get()) {
            if (mod.equalsIgnoreCase(namespace)) return false;
        }
        return true;
    }

    /**
     * The "climbing" flag is synced from the server with a delay, so on the client upward
     * movement also counts as a sign that the spider is climbing.
     */
    private static boolean wantsToClimb(Mob s) {
        if (s.level().isClientSide()) {
            return s.onClimbable() || (!s.onGround() && s.getY() - s.yOld > 0.02D);
        }
        return s.horizontalCollision || s.onClimbable();
    }

    private static void updateWall(Mob spider, Data d) {
        int now = spider.tickCount;
        Direction contact = findWall(spider, d.wall);

        if (d.wall == null) {
            if (contact != null && wantsToClimb(spider)) activate(spider, d, contact, now);
            return;
        }

        if (contact == d.wall) {
            d.lastContactTick = now;
        } else if (contact != null) {
            // Moved onto a different wall (around a corner): drop the old pose, take the new one.
            release(spider, d);
            if (wantsToClimb(spider)) activate(spider, d, contact, now);
            return;
        } else if (now - d.lastContactTick > RELEASE_DELAY_TICKS) {
            release(spider, d);
            return;
        }
        applyBox(spider, d.wall);
    }

    /** Which horizontal side (if any) has a solid block touching the spider's body. */
    private static Direction findWall(Mob spider, Direction preferred) {
        AABB b = spider.getBoundingBox();
        // Trim top/bottom a little so floor/ceiling contact isn't mistaken for a wall.
        AABB core = new AABB(b.minX, b.minY + 0.1D, b.minZ, b.maxX, b.maxY - 0.05D, b.maxZ);

        if (preferred != null && touches(spider, core, preferred)) return preferred;
        Direction looking = spider.getDirection();
        if (looking != preferred && touches(spider, core, looking)) return looking;
        for (Direction f : Direction.Plane.HORIZONTAL) {
            if (f != preferred && f != looking && touches(spider, core, f)) return f;
        }
        return null;
    }

    private static boolean touches(Mob spider, AABB core, Direction f) {
        AABB probe = core.expandTowards(f.getStepX() * 0.1D, 0.0D, f.getStepZ() * 0.1D);
        return spider.level().getBlockCollisions(spider, probe).iterator().hasNext();
    }

    private static boolean fits(Mob spider, AABB box) {
        return !spider.level().getBlockCollisions(spider, box.deflate(0.001D)).iterator().hasNext();
    }

    /**
     * A spider that was saved while hugging a wall is stored with its centre shifted toward that wall, but loads with the
     * wider upright box, which then sits inside the wall. Moves it along the smallest horizontal offset (up to 0.8 blocks)
     * at which the upright box fits again.
     */
    private static void freeFromBlocks(Mob spider) {
        AABB box = spider.getBoundingBox();
        if (fits(spider, box)) return;
        for (double r = 0.05D; r <= 0.8D; r += 0.05D) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    double len = Math.sqrt(dx * dx + dz * dz);
                    double ox = dx * r / len;
                    double oz = dz * r / len;
                    if (fits(spider, box.move(ox, 0.0D, oz))) {
                        spider.setPos(spider.getX() + ox, spider.getY(), spider.getZ() + oz);
                        return;
                    }
                }
            }
        }
    }

    private static void activate(Mob s, Data d, Direction wall, int now) {
        // The lying box is shallower than the upright one: slide the centre toward the wall to keep
        // the wall-side face where it was (otherwise the spider would lose contact and slip).
        double shift = s.getBbWidth() / 2.0D - s.getBbHeight() / 2.0D;
        double sx = s.getX() + wall.getStepX() * shift;
        double sz = s.getZ() + wall.getStepZ() * shift;
        AABB box = tiltedBox(s, wall, sx, sz);

        if (!fits(s, box)) return; // the taller lying box would not fit (low ceiling etc)

        if (!s.level().isClientSide()) {
            s.setPos(sx, s.getY(), sz); // only the server moves the spider; clients follow its updates
            s.setBoundingBox(box);
        } else {
            s.setBoundingBox(tiltedBox(s, wall, s.getX(), s.getZ()));
        }
        d.wall = wall;
        d.renderWall = wall;
        d.lastContactTick = now;
    }

    private static void release(Mob s, Data d) {
        Direction old = d.wall;
        d.wall = null;
        if (old == null) return;

        double ox = s.getX(), oy = s.getY(), oz = s.getZ();
        s.setPos(ox, oy, oz); // rebuilds the normal upright box around the current position
        if (s.level().isClientSide()) return;

        if (!fits(s, s.getBoundingBox())) {
            // The wider upright box pokes into the wall: back the spider off the wall.
            double shift = s.getBbWidth() / 2.0D - s.getBbHeight() / 2.0D + 0.02D;
            s.setPos(ox - old.getStepX() * shift, oy, oz - old.getStepZ() * shift);
        }
    }

    public static void applyBox(Entity s, Direction wall) {
        s.setBoundingBox(tiltedBox(s, wall, s.getX(), s.getZ()));
    }

    private static AABB tiltedBox(Entity s, Direction wall, double cx, double cz) {
        double halfDepth = s.getBbHeight() / 2.0D; // along the wall normal (belly -> back)
        double halfSpan = s.getBbWidth() / 2.0D;   // along the wall plane
        double hx = wall.getAxis() == Direction.Axis.X ? halfDepth : halfSpan;
        double hz = wall.getAxis() == Direction.Axis.Z ? halfDepth : halfSpan;
        return new AABB(cx - hx, s.getY(), cz - hz, cx + hx, s.getY() + s.getBbWidth(), cz + hz);
    }

    // ── render data ─────────────────────────────────────────────────────────────

    private static void updateRenderData(Mob s, Data d) {
        d.prevProgress = d.progress;
        d.prevTheta = d.theta;
        d.prevWalkSpeed = d.walkSpeed;

        // vertical crawl speed -> leg animation (same smoothing as vanilla's WalkAnimationState)
        float vertical = 0.0F;
        if (d.hasLast && d.wall != null) vertical = (float) Math.abs(s.getY() - d.lastY);
        float walkTarget = Math.min(vertical * 4.0F, 1.0F);
        d.walkSpeed += (walkTarget - d.walkSpeed) * 0.4F;
        d.walkPos += d.walkSpeed;

        if (d.wall != null) {
            d.renderWall = d.wall;
            d.progress = Math.min(1.0F, d.progress + 1.0F / TRANSITION_TICKS);
        } else {
            d.progress = Math.max(0.0F, d.progress - 1.0F / TRANSITION_TICKS);
            if (d.progress <= 0.0F) {
                d.theta = 0.0F;
                d.prevTheta = 0.0F;
            }
        }

        // Point the head where the spider is actually crawling (within the wall plane).
        if (d.hasLast && d.wall != null) {
            double dy = s.getY() - d.lastY;
            double dx = s.getX() - d.lastX;
            double dz = s.getZ() - d.lastZ;
            double nx = -d.wall.getStepX();
            double nz = -d.wall.getStepZ();
            double dt = dx * (-nz) + dz * nx; // movement along the wall tangent
            if (dy * dy + dt * dt > 0.0004D) {
                float target = (float) Math.atan2(dt, dy);
                d.theta += wrapRadians(target - d.theta) * 0.3F;
                d.theta = wrapRadians(d.theta);
            }
        }
        d.lastX = s.getX();
        d.lastY = s.getY();
        d.lastZ = s.getZ();
        d.hasLast = true;
    }

    public static float wrapRadians(float a) {
        while (a > Math.PI) a -= (float) (2 * Math.PI);
        while (a < -Math.PI) a += (float) (2 * Math.PI);
        return a;
    }
}
