package com.alexandrso.amm.combat;

import com.alexandrso.amm.AlexMovementsMod;
import com.alexandrso.amm.config.BlockingRules;
import com.alexandrso.amm.movement.AmmAttachments;
import com.alexandrso.amm.movement.CrawlHandler;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server-authoritative weapon guard (port of BlockingServerHandler's block part).
 * While the guard is up: no interactions, slowed movement, and incoming hits are rolled
 * against the block chance. Mob guards come with the mob phase (MobCombatHandler).
 */
@EventBusSubscriber(modid = AlexMovementsMod.MODID)
public final class BlockingHandler {
    private static final Identifier SLOW_ID = Identifier.fromNamespaceAndPath(AlexMovementsMod.MODID, "blocking_slow");

    private BlockingHandler() {
    }

    /** Players AND mobs: the synced BLOCKING attachment is the single source of truth. */
    public static boolean isBlocking(Entity entity) {
        return entity.getData(AmmAttachments.BLOCKING.get());
    }

    /** Weapon guard of a mob (the mob goal in MobCombatHandler drives it). */
    public static void setMobBlocking(LivingEntity mob, boolean value) {
        if (isBlocking(mob) != value) mob.setData(AmmAttachments.BLOCKING.get(), value);
    }

    /** Sneaking, but not crawling or swimming. */
    public static boolean polearmStance(Player player) {
        return player.isShiftKeyDown() && !player.isVisuallyCrawling() && !player.isSwimming()
                && !CrawlHandler.isManualCrawling(player);
    }

    private static boolean handCanGuard(Player player, ItemStack stack) {
        if (!BlockingRules.canBlock(stack)) return false;
        return !BlockingRules.isPolearm(stack) || polearmStance(player);
    }

    public static boolean canBlockNow(Player player) {
        if (player.isSpectator() || !player.isAlive() || player.isUsingItem()) return false;
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (BlockingRules.isShield(main) || BlockingRules.isShield(off)) return false;
        return handCanGuard(player, main) || handCanGuard(player, off);
    }

    public static void setBlocking(Player player, boolean value) {
        if (isBlocking(player) == value) return;
        player.setData(AmmAttachments.BLOCKING.get(), value);
        if (player.level().isClientSide()) return;

        // Old client-side moveForward *= 0.3 -> a movement-speed modifier, applied on the server and
        // synced to the client like any other attribute (so prediction stays correct).
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SLOW_ID);
            if (value) {
                speed.addTransientModifier(new AttributeModifier(SLOW_ID, -0.7D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
        if (value) {
            RiposteHandler.swingMissed(player); // raising the guard drops a pending riposte
            DualHandler.onGuardRaised(player);  // ... the queued off-hand strike and the dual combo
        }
        // Lowering the guard needs nothing here: RiposteHandler starts its countdown on the player tick.
    }

    // ── no interactions while guarding ──────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isBlocking(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isBlocking(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isBlocking(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (isBlocking(event.getEntity())) event.setCanceled(true);
    }

    /** Swapped the weapon away / started eating / died: the guard drops. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (isBlocking(player) && !canBlockNow(player)) setBlocking(player, false);
    }

    // ── the block roll ──────────────────────────────────────────────────────────

    private static boolean isMelee(DamageSource source) {
        if (source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.BYPASSES_SHIELD)
                || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) {
            return false;
        }
        return source.getEntity() instanceof LivingEntity;
    }

    private static boolean isBlockableProjectile(DamageSource source) {
        if (!source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.BYPASSES_SHIELD)
                || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) {
            return false;
        }
        return source.getDirectEntity() != null;
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity player = event.getEntity(); // a player OR a guarding mob
        if (player.level().isClientSide() || !isBlocking(player)) return;

        DamageSource source = event.getSource();
        boolean melee = isMelee(source);
        boolean projectile = !melee && isBlockableProjectile(source);
        if (!melee && !projectile) return;

        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (BlockingRules.isShield(main) || BlockingRules.isShield(off)) return;

        // Dual style: both hands guard, chance = best weapon + bonus. Projectiles only stop in dual style.
        boolean dual = BlockingRules.isDualWielding(player);
        if (projectile && !dual) return;

        ItemStack blockingStack = null;
        int chance;
        if (dual) {
            blockingStack = main;
            chance = BlockingRules.dualChance(main, off);
        } else {
            if (BlockingRules.canBlock(main)) blockingStack = main;
            else if (BlockingRules.canBlock(off)) blockingStack = off;
            if (blockingStack == null) return;
            chance = BlockingRules.chance(blockingStack);
        }

        // The player has to be facing the attack.
        Vec3 look = player.getViewVector(1.0F);
        if (projectile) {
            // Direction of travel; at impact time the projectile is already inside the player.
            Vec3 travel = source.getDirectEntity().getDeltaMovement();
            travel = new Vec3(travel.x, 0.0D, travel.z);
            if (travel.lengthSqr() > 1.0E-6D) {
                if (travel.normalize().dot(new Vec3(look.x, 0.0D, look.z)) >= 0.0D) return;
            }
            chance = BlockingRules.projectileChance(chance);
        } else {
            Vec3 origin = source.getSourcePosition();
            if (origin == null && source.getEntity() != null) origin = source.getEntity().position();
            if (origin != null) {
                Vec3 away = player.position().subtract(origin).normalize();
                away = new Vec3(away.x, 0.0D, away.z);
                if (away.dot(look) >= 0.0D) return;
            }
        }

        // A spear in the attacker's hand cuts through part of the guard.
        if (melee && source.getEntity() instanceof LivingEntity attacker) {
            chance = Math.max(0, chance - BlockingRules.blockPierce(attacker.getMainHandItem()));
        }

        float damage = event.getAmount();
        boolean success = player.getRandom().nextInt(100) < chance;

        if (success) {
            event.setCanceled(true);
            // Riposte: a solo sword (no shield, no second weapon) that just blocked a melee hit.
            if (melee && !dual && player instanceof Player p) RiposteHandler.start(p, blockingStack);
            damageBlockers(player, dual, Math.max(1, (int) damage));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
        } else if (melee) {
            // A failed melee guard hurts the weapon(s); a missed projectile just hits.
            damageBlockers(player, dual, Math.max(1, (int) (damage * 2.0F)));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 0.8F, 1.2F);
        }
    }

    /** Wears the blocking weapon; when dual-wielding the load is shared between both hands. */
    private static void damageBlockers(LivingEntity player, boolean dual, int amount) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (!dual) {
            if (BlockingRules.canBlock(main)) main.hurtAndBreak(amount, player, EquipmentSlot.MAINHAND);
            else off.hurtAndBreak(amount, player, EquipmentSlot.OFFHAND);
            return;
        }
        int half = Math.max(1, amount / 2);
        main.hurtAndBreak(half, player, EquipmentSlot.MAINHAND);
        off.hurtAndBreak(half, player, EquipmentSlot.OFFHAND);
    }
}