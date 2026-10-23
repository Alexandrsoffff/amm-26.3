package com.alexandrso.amm.combat;

import com.alexandrso.amm.AlexMovementsMod;
import com.alexandrso.amm.config.AmmConfig;
import com.alexandrso.amm.movement.AmmAttachments;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * "Mercy save": a lethal hit on an adult zombie has a chance to leave it at low health in a slow,
 * crawling, wounded state instead (once per zombie, ever).
 * <p>
 * Compared to 1.12.2 there is no UUID set, no handwritten NBT flags and no sync packets: the
 * "crawling" and "used mercy" flags are data attachments stored with the entity (they survive
 * saving and reloading) and the crawl one is synced to everyone who sees the zombie.
 */
@EventBusSubscriber(modid = AlexMovementsMod.MODID)
public final class WoundedZombieHandler {
    private static final Identifier CRAWL_SPEED_ID = Identifier.fromNamespaceAndPath(AlexMovementsMod.MODID, "crawl_slow");
    public static final float CRAWL_WIDTH = 0.6F;
    public static final float CRAWL_HEIGHT = 1.2F;

    private WoundedZombieHandler() {
    }

    /** Zombie family that can be wounded: not drowned (it swims) and not the giant. */
    private static boolean isWoundable(Entity e) {
        return e instanceof Mob mob && mob.is(EntityTypeTags.ZOMBIES)
                && mob.getType() != EntityTypes.DROWNED && mob.getType() != EntityTypes.GIANT;
    }

    public static boolean isCrawling(Entity e) {
        return isWoundable(e) && e.getData(AmmAttachments.WOUNDED_CRAWL.get());
    }

    private static void applyCrawlSpeedModifier(LivingEntity zombie) {
        AttributeInstance speed = zombie.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(CRAWL_SPEED_ID) == null) {
            speed.addTransientModifier(new AttributeModifier(CRAWL_SPEED_ID,
                    -(1.0D - AmmConfig.WOUNDED_SPEED_MULTIPLIER.get()), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    // ── mercy save ──────────────────────────────────────────────────────────────

    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || !AmmConfig.WOUNDED_ZOMBIE_ENABLED.get() || !isWoundable(victim)) return;
        // Babies never crawl: the pose is tuned for an adult-sized model. Already crawling: this hit may kill.
        if (victim.isBaby() || victim.getData(AmmAttachments.WOUNDED_CRAWL.get())) return;
        if (event.getNewDamage() < victim.getHealth()) return;
        if (victim.getData(AmmAttachments.USED_MERCY.get())) return; // one mercy save per zombie, ever
        if (victim.getRandom().nextInt(100) >= AmmConfig.WOUNDED_MERCY_CHANCE_PERCENT.get()) return;

        // Swallow the hit and set health directly: more reliable than shrinking the amount.
        event.setNewDamage(0.0F);
        victim.setHealth(Math.min(AmmConfig.WOUNDED_HEALTH.get().floatValue(), victim.getMaxHealth()));
        victim.setData(AmmAttachments.USED_MERCY.get(), true);
        victim.setData(AmmAttachments.WOUNDED_CRAWL.get(), true);

        // A crawler cannot hold a guard: drop it (and any twirl) at once.
        BlockingHandler.setMobBlocking(victim, false);
        victim.setData(AmmAttachments.SPINNING.get(), false);
        applyCrawlSpeedModifier(victim);
    }

    /** Back from disk: transient attribute modifiers are not saved, so the slowdown is re-applied. */
    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !isCrawling(event.getEntity())) return;
        LivingEntity zombie = (LivingEntity) event.getEntity();
        if (zombie.isBaby()) {
            zombie.setData(AmmAttachments.WOUNDED_CRAWL.get(), false);
            return;
        }
        applyCrawlSpeedModifier(zombie);
    }

    // ── hitbox ──────────────────────────────────────────────────────────────────

    /**
     * Shrinks the box to a lying-down one. Both sides: the server box drives movement and collision,
     * and what a swing hits is decided against each CLIENT's copy. Moving a mob rebuilds its box
     * from the standing dimensions, so the crawl box is put back before AND after every tick.
     */
    private static void applyCrawlBox(Entity e) {
        if (!isCrawling(e) || ((LivingEntity) e).isBaby()) return;
        double half = CRAWL_WIDTH / 2.0D;
        e.setBoundingBox(new AABB(e.getX() - half, e.getY(), e.getZ() - half,
                e.getX() + half, e.getY() + CRAWL_HEIGHT, e.getZ() + half));
    }

    @SubscribeEvent
    static void onTickPre(EntityTickEvent.Pre event) {
        applyCrawlBox(event.getEntity());
    }

    @SubscribeEvent
    static void onTickPost(EntityTickEvent.Post event) {
        applyCrawlBox(event.getEntity());
    }

    // ── damage rules ────────────────────────────────────────────────────────────

    /**
     * Eyes sit at a fixed height above the feet, which with the low box can end up inside a
     * one-block ceiling: a crawling zombie is lying down, so it takes no suffocation damage.
     * Damage dealt BY a crawling zombie is weakened.
     */
    @SubscribeEvent
    static void onIncoming(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getSource().is(DamageTypes.IN_WALL) && isCrawling(event.getEntity())) {
            event.setCanceled(true);
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (attacker != null && isCrawling(attacker)) {
            event.setAmount(event.getAmount() * AmmConfig.WOUNDED_ATTACK_MULTIPLIER.get().floatValue());
        }
    }
}
