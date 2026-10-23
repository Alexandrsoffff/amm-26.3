package com.alexandrso.amm.entity;

import java.util.List;
import java.util.Set;

import com.alexandrso.amm.AlexMovementsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Invisible seat. Players sit on it with G; idle mobs (skeletons, illagers, villagers, witches -
 * see the mob phase) sit on the same seat and the seat itself stands them up again.
 * Port of EntitySittingSeat: the 1.12 "mounted y offset" per rider type is now a single
 * positionRider override.
 */
public class SittingSeat extends Entity {
    /** Hostile mobs notice a player closer than this (blocks) and stand up. */
    private static final double HOSTILE_NOTICE_DISTANCE = 5.0D;
    /** Peaceful mobs (villager) only get up when a player stays this close for a while... */
    private static final double PEACEFUL_NOTICE_DISTANCE = 2.5D;
    /** ...or at once (after a short moment) when the player is practically touching them. */
    private static final double PEACEFUL_TOUCH_DISTANCE = 1.3D;
    /** A peaceful mob only turns its head towards a player closer than this. */
    private static final double PEACEFUL_LOOK_DISTANCE = 6.0D;
    private static final double SNEAK_FACTOR = 0.6D;
    /** How close a hostile mob has to be to make a seated peaceful mob panic and stand. */
    private static final double THREAT_DISTANCE = 6.0D;
    /** Ticks a seated mob ignores a freshly found (not yet attacking) target before it stands up. */
    private static final int MOB_TARGET_GRACE_TICKS = 60;

    /** Villagers, witches and illagers are drawn smaller: they sit a bit higher than everyone else. */
    private static final Set<EntityType<?>> SMALL_SITTERS = Set.of(EntityTypes.VILLAGER, EntityTypes.WITCH,
            EntityTypes.VINDICATOR, EntityTypes.PILLAGER, EntityTypes.EVOKER, EntityTypes.ILLUSIONER);

    /** Who may sit down is decided by the KIND of mob: skeletons (all variants), illagers, villagers, witches. Zombies never sit. */
    public static boolean isSittingKind(Mob mob) {
        return mob.is(net.minecraft.tags.EntityTypeTags.SKELETONS) || SMALL_SITTERS.contains(mob.getType());
    }

    private int sitTicks;
    private final int sitLimit;
    private int alertTicks;
    private int alertNeeded = -1;

    public SittingSeat(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.sitLimit = 300 + this.random.nextInt(600); // 15 - 45 seconds for a mob
    }

    // ── spawning (G key) ────────────────────────────────────────────────────────

    /** Server side of the sit key. Returns true when the player now sits. */
    public static boolean trySit(Player player) {
        if (player.level().isClientSide() || player.isPassenger() || !player.onGround() || player.isSpectator()) return false;
        if (player.isInWater() || player.isUnderWater()) return false;

        boolean hasGround = false;
        for (double dy = 0.1D; dy <= 1.5D; dy += 0.25D) {
            BlockPos p = BlockPos.containing(player.getX(), player.getY() - dy, player.getZ());
            BlockState st = player.level().getBlockState(p);
            if (!st.isAir() && !st.getCollisionShape(player.level(), p).isEmpty()) {
                hasGround = true;
                break;
            }
        }
        if (!hasGround) return false;
        return seat(player);
    }

    /** Puts any entity (player or mob) on a fresh seat at its current position. */
    public static boolean seat(Entity rider) {
        Level level = rider.level();
        if (level.isClientSide() || rider.isInWater()) return false;
        SittingSeat seat = new SittingSeat(AmmEntities.SITTING_SEAT.get(), level);
        seat.setPos(rider.getX(), rider.getY(), rider.getZ());
        boolean added = level.addFreshEntity(seat);
        boolean riding = rider.startRiding(seat, true, true);
        AlexMovementsMod.LOGGER.info("[amm] sit: added={} riding={}", added, riding);
        if (!riding) seat.discard();
        return riding;
    }

    // ── riding position ─────────────────────────────────────────────────────────

    @Override
    protected void positionRider(Entity passenger, MoveFunction moveFunction) {
        // Hip just above the ground: feet sit below the seat. Small sitters a bit higher.
        double offset = SMALL_SITTERS.contains(passenger.getType()) ? -0.5D : -0.65D;
        moveFunction.accept(passenger, getX(), getY() + offset, getZ());
    }

    // ── per tick ────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;

        if (getPassengers().isEmpty()) {
            discard();
            return;
        }
        Entity rider = getPassengers().get(0);

        BlockPos below = BlockPos.containing(getX(), getY() - 0.01D, getZ());
        BlockState state = level().getBlockState(below);
        VoxelShape shape = state.getCollisionShape(level(), below);

        // Air, water, plants, anything without a solid top: nowhere to sit. An empty shape has an
        // infinite max(), which used to teleport the rider to -Infinity and hang the server.
        boolean inFluid = !level().getFluidState(BlockPos.containing(getX(), getY(), getZ())).isEmpty();
        if (shape.isEmpty() || inFluid || rider.isInWater()) {
            eject(rider, getY() + 0.1D);
            return;
        }

        final double standY = state.getBlock() instanceof StairBlock
                ? getY() + 0.01D
                : below.getY() + shape.max(Direction.Axis.Y) + 0.01D;

        setDeltaMovement(0.0D, 0.0D, 0.0D);

        if (!(rider instanceof Player) && mobShouldStand(rider)) {
            eject(rider, standY);
            return;
        }
        if (rider.isShiftKeyDown()) {
            eject(rider, standY);
        }
    }

    private void eject(Entity rider, double standY) {
        if (!Double.isFinite(standY)) standY = getY() + 0.1D;
        rider.stopRiding();
        rider.dismountTo(getX(), standY, getZ());
        rider.fallDistance = 0;
        discard();
    }

    /**
     * A seated mob stands up when it is hurt, burning, in water, when a threat shows up or after a
     * while. A nearby player does NOT make it jump up instantly: it first turns its head towards
     * the player and only stands after a short reaction time; peaceful mobs are far more relaxed,
     * and a trading villager never gets up because of the player.
     */
    private boolean mobShouldStand(Entity rider) {
        if (++sitTicks > sitLimit) return true;
        if (!(rider instanceof Mob mob) || !mob.isAlive()) return true;
        if (mob.getLastHurtByMob() != null || mob.hurtTime > 0) return true; // hurt: up at once
        if (mob.isOnFire() || mob.isInWater()) return true;

        final boolean peaceful = !(mob instanceof Enemy);
        if (alertNeeded < 0) {
            alertNeeded = peaceful ? 40 + random.nextInt(31) : 8 + random.nextInt(13);
        }

        if (peaceful && sitTicks % 10 == 0 && hostileNearby(mob)) return true; // a zombie / illager nearby: run!

        final boolean trading = mob instanceof Villager v && v.getTradingPlayer() != null;
        final double lookRange = peaceful ? PEACEFUL_LOOK_DISTANCE : HOSTILE_NOTICE_DISTANCE;
        Player player = trading ? null : level().getNearestPlayer(mob, lookRange);
        boolean alerted = false;
        if (player != null) {
            double dist = player.distanceTo(mob);
            double sneak = player.isShiftKeyDown() ? SNEAK_FACTOR : 1.0D;
            mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
            if (peaceful) {
                if (dist <= PEACEFUL_TOUCH_DISTANCE) {
                    alertTicks += 3;
                    alerted = true;
                } else if (dist <= PEACEFUL_NOTICE_DISTANCE * sneak) {
                    alertTicks += 1;
                    alerted = true;
                }
            } else if (dist <= HOSTILE_NOTICE_DISTANCE * sneak && (dist <= 2.0D || mob.hasLineOfSight(player))) {
                alertTicks += 1;
                alerted = true;
            }
        }
        if (!alerted) {
            alertTicks = Math.max(0, alertTicks - 2); // calms down again when the player goes away
        } else if (alertTicks >= alertNeeded) {
            return true;
        }

        // Hostile mobs: a target noticed from afar does not make it jump up instantly.
        return !peaceful && sitTicks > MOB_TARGET_GRACE_TICKS && mob.getTarget() != null;
    }

    private boolean hostileNearby(Mob mob) {
        AABB box = mob.getBoundingBox().inflate(THREAT_DISTANCE, 3.0D, THREAT_DISTANCE);
        List<Mob> list = level().getEntitiesOfClass(Mob.class, box);
        for (Mob e : list) {
            if (e == mob || !(e instanceof Enemy) || !e.isAlive() || e.isPassenger()) continue;
            if (mob.hasLineOfSight(e)) return true;
        }
        return false;
    }

    // ── boilerplate ─────────────────────────────────────────────────────────────

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvisible() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}