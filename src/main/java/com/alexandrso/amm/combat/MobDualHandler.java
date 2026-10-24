package com.alexandrso.amm.combat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.alexandrso.amm.AlexMovementsMod;
import com.alexandrso.amm.config.AmmConfig;
import com.alexandrso.amm.config.BlockingRules;
import com.alexandrso.amm.network.DualSwingPayload;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Dual style for mobs: a mob holding a blockable weapon in BOTH hands follows each main-hand hit
 * with a second strike from the off hand after dualOffhandDelayTicks, like a dual-wielding player.
 * <p>
 * The off-hand strike is vanilla's own {@link Mob#doHurtTarget}: the off-hand stack (and its attribute
 * modifiers) sit in the main-hand slot for the duration of the call, so the off-hand weapon's damage,
 * enchantments, knockback and special effects (an axe knocking a shield out) all apply as for a normal hit.
 * Clients draw the off arm swinging through the same payload the player's dual style uses.
 */
@EventBusSubscriber(modid = AlexMovementsMod.MODID)
public final class MobDualHandler {
    private static final class Pending {
        final Mob mob;
        final int targetId;
        float remaining;

        Pending(Mob mob, int targetId, float remaining) {
            this.mob = mob;
            this.targetId = targetId;
            this.remaining = remaining;
        }
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    /** True while the off-hand strike runs, so it is never mistaken for a new main-hand attack. */
    private static boolean performing;

    private MobDualHandler() {
    }

    private static boolean isActiveFor(Mob mob) {
        return AmmConfig.MOB_DUAL_STRIKE_ENABLED.get() && BlockingRules.isDualWielding(mob);
    }

    // ── damage penalty + queueing the off-hand strike ───────────────────────────

    /**
     * Every melee hit of a dual-wielding mob (both hands) deals only dualDamagePercent of its damage.
     * A main-hand hit also queues the off-hand strike and tells clients to animate it.
     * During the off-hand strike the victim's invulnerability frames are cleared here, right before
     * vanilla checks them.
     */
    @SubscribeEvent
    static void onDualMobDamage(LivingIncomingDamageEvent event) {
        DamageSource src = event.getSource();
        if (!src.is(DamageTypes.MOB_ATTACK)) return;
        if (!(src.getEntity() instanceof Mob attacker) || src.getDirectEntity() != attacker) return;
        if (attacker.level().isClientSide() || !isActiveFor(attacker)) return;

        if (performing) DualHandler.clearHurtFrames(event.getEntity());

        int percent = BlockingRules.dualDamagePercent(attacker.getMainHandItem());
        if (percent < 100) {
            event.setAmount(event.getAmount() * percent / 100.0F);
        }

        // The off-hand strike itself passes through here too: never queue from it.
        if (!performing) queueOffhand(attacker, event.getEntity());
    }

    private static void queueOffhand(Mob mob, LivingEntity target) {
        if (target == null || !target.isAlive() || BlockingHandler.isBlocking(mob)) return;
        for (Pending p : PENDING) {
            if (p.mob == mob) return; // one queued strike per mob
        }

        float delay = AmmConfig.DUAL_OFFHAND_DELAY_TICKS.get().floatValue();
        PENDING.add(new Pending(mob, target.getId(), delay));
        PacketDistributor.sendToPlayersTrackingEntity(mob, new DualSwingPayload(mob.getId(), delay));
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        List<Pending> due = new ArrayList<>();
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (!p.mob.isAlive() || p.mob.isRemoved()) {
                it.remove();
                continue;
            }
            p.remaining -= 1.0F;
            if (p.remaining > 0.0F) continue;
            it.remove();
            due.add(p);
        }
        for (Pending p : due) strike(p);
    }

    // ── the off-hand strike ─────────────────────────────────────────────────────

    /** Vanilla attack reach of a mob (the squared distance its melee goal uses), widened by the dual leeway. */
    private static boolean inReach(Mob mob, LivingEntity target) {
        double reach = Math.sqrt(mob.getBbWidth() * 2.0F * mob.getBbWidth() * 2.0F + target.getBbWidth())
                + AmmConfig.DUAL_OFFHAND_REACH_LEEWAY.get();
        return mob.distanceToSqr(target) <= reach * reach;
    }

    private static void strike(Pending p) {
        Mob mob = p.mob;
        if (!(mob.level() instanceof ServerLevel level) || !isActiveFor(mob)) return;
        if (BlockingHandler.isBlocking(mob) || mob.isUsingItem()) return;

        Entity e = level.getEntity(p.targetId);
        if (!(e instanceof LivingEntity target) || !target.isAlive()) return;
        if (!inReach(mob, target) || !mob.hasLineOfSight(target)) return;

        ItemStack main = mob.getMainHandItem();
        ItemStack off = mob.getOffhandItem();
        final boolean guarded = target.isBlocking();
        final float healthBefore = target.getHealth();
        performing = true;
        try {
            mob.setItemSlot(EquipmentSlot.MAINHAND, off);
            mob.setItemSlot(EquipmentSlot.OFFHAND, main);
            swapModifiers(mob, main, off);

            // The main-hand hit started the target's invulnerability frames, which would swallow this one
            // (cleared again in onDualMobDamage, right before vanilla checks them).
            DualHandler.clearHurtFrames(target);
            mob.doHurtTarget(level, target);

            // An axe in the off hand knocks a raised shield out, like an axe in the main hand does.
            if (guarded && BlockingRules.isAxe(off) && target.isBlocking()) ShieldBreaker.disable(target);
        } catch (RuntimeException ex) {
            AlexMovementsMod.LOGGER.error("[amm] mob off-hand strike failed", ex);
        } finally {
            swapModifiers(mob, off, main);
            mob.setItemSlot(EquipmentSlot.MAINHAND, main);
            mob.setItemSlot(EquipmentSlot.OFFHAND, off);
            performing = false;
        }

        if (target.isAlive() && target.getHealth() >= healthBefore) {
            AlexMovementsMod.LOGGER.info("[amm] mob off-hand strike dealt no damage to {}: invulnerableTime={} hurtTime={} guarded={}",
                    target, target.invulnerableTime, target.hurtTime, guarded);
        }
    }

    /** Moves a stack's main-hand attribute modifiers from {@code from} to {@code to} on the mob (what the game does on an equipment change). */
    private static void swapModifiers(LivingEntity mob, ItemStack from, ItemStack to) {
        from.forEachModifier(EquipmentSlot.MAINHAND, (holder, m) -> {
            AttributeInstance i = mob.getAttribute(holder);
            if (i != null) i.removeModifier(m.id());
        });
        to.forEachModifier(EquipmentSlot.MAINHAND, (holder, m) -> {
            AttributeInstance i = mob.getAttribute(holder);
            if (i != null) {
                i.removeModifier(m.id());
                i.addTransientModifier(m);
            }
        });
    }
}