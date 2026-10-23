package com.alexandrso.amm.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;

import com.alexandrso.amm.AlexMovementsMod;
import com.alexandrso.amm.config.AmmConfig;
import com.alexandrso.amm.config.BlockingRules;
import com.mojang.logging.LogUtils;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Riposte: after a successful block, a riposte weapon held ALONE deals extra damage with the
 * NEXT hit. The countdown starts when the guard is lowered (it is frozen while the guard is up).
 * One riposte per block; lost on a miss or when the window runs out.
 */
@EventBusSubscriber(modid = AlexMovementsMod.MODID)
public final class RiposteHandler {
    private static final Logger LOG = LogUtils.getLogger();
    /** Window opened by a block, but the guard is still up: the countdown has not started yet. */
    private static final long PENDING = Long.MAX_VALUE;
    /** uuid -> game time until which the window is open (or PENDING). */
    private static final Map<UUID, Long> UNTIL = new HashMap<>();

    private RiposteHandler() {
    }

    /** Raising the guard: drops the window. */
    public static void swingMissed(Player player) {
        if (UNTIL.remove(player.getUUID()) != null) {
            LOG.info("[amm][riposte] window closed by swingMissed()", new Throwable("caller"));
        }
    }

    /** Use this from the client "swing at air" packet. Swings during the guard are cancelled anyway. */
    public static void clientSwingMissed(Player player) {
        if (BlockingHandler.isBlocking(player)) return;
        swingMissed(player);
    }

    /** Kept for compatibility: the countdown also arms itself on the player tick. */
    public static void onGuardLowered(Player player) {
        armCountdown(player);
    }

    private static void armCountdown(Player player) {
        Long until = UNTIL.get(player.getUUID());
        if (until == null || until != PENDING) return;
        int duration = AmmConfig.RIPOSTE_DURATION_TICKS.get();
        UNTIL.put(player.getUUID(), player.level().getGameTime() + duration);
        LOG.info("[amm][riposte] guard lowered, window open for {} ticks", duration);
    }

    static void start(Player player, ItemStack sword) {
        boolean enabled = AmmConfig.RIPOSTE_ENABLED.get();
        boolean item = BlockingRules.isRiposteItem(sword);
        LOG.info("[amm][riposte] start(): enabled={} riposteItem={} stack={}", enabled, item, sword);
        if (!enabled || !item) return;
        UNTIL.put(player.getUUID(), PENDING);
        if (AmmConfig.RIPOSTE_NOTIFY.get() && player instanceof ServerPlayer sp) {
            sp.sendOverlayMessage(Component.translatable("amm.riposte.start", AmmConfig.RIPOSTE_DAMAGE_BONUS_PERCENT.get()));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.5F, 1.6F);
        }
    }

    /** Window open AND the player holds a riposte weapon alone, without a shield. */
    public static boolean isActive(Player player) {
        if (!AmmConfig.RIPOSTE_ENABLED.get()) return false;
        Long until = UNTIL.get(player.getUUID());
        if (until == null) return false;
        if (until == PENDING) {
            if (BlockingHandler.isBlocking(player)) return false; // frozen while guarding
            armCountdown(player);
            until = UNTIL.get(player.getUUID());
            if (until == null) return false;
        }
        if (player.level().getGameTime() > until) {
            UNTIL.remove(player.getUUID());
            LOG.info("[amm][riposte] window expired");
            return false;
        }
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (!BlockingRules.isRiposteItem(main)) return false;
        if (BlockingRules.isShield(off)) return false;
        return !BlockingRules.isDualWielding(player);
    }

    /**
     * The first damaging melee hit by the player while the window is open IS the riposte.
     * Everything happens in this one event: bonus damage + optional shield knock-out.
     */
    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
    public static void onDamage(LivingIncomingDamageEvent event) {
        DamageSource src = event.getSource();
        if (!(src.getEntity() instanceof Player player) || src.getDirectEntity() != player) return;
        if (player.level().isClientSide() || event.getEntity() == player) return;
        if (src.is(DamageTypeTags.IS_PROJECTILE) || src.is(DamageTypeTags.IS_EXPLOSION)
                || src.is(DamageTypes.THORNS)) return;
        if (DualHandler.isPerformingOffhand()) return;
        if (!UNTIL.containsKey(player.getUUID())) return;

        if (!isActive(player)) {
            LOG.info("[amm][riposte] hit with a window present but NOT active: blocking={} main={} offhand={} dual={}",
                    BlockingHandler.isBlocking(player), player.getMainHandItem(), player.getOffhandItem(),
                    BlockingRules.isDualWielding(player));
            return;
        }

        UNTIL.remove(player.getUUID()); // one riposte per block: this hit is it
        LivingEntity target = event.getEntity();
        pierceShield(player, target);

        if (event.isCanceled()) {
            LOG.info("[amm][riposte] riposte hit was cancelled by another handler ({})", target);
            return;
        }
        float old = event.getAmount();
        event.setAmount(old * (1.0F + AmmConfig.RIPOSTE_DAMAGE_BONUS_PERCENT.get() / 100.0F));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.2F);
        LOG.info("[amm][riposte] RIPOSTE HIT on {}: damage {} -> {}", target, old, event.getAmount());
    }

    /** A fully charged riposte may knock a raised shield out of the target's hands. */
    private static void pierceShield(Player player, LivingEntity target) {
        if (player.getAttackStrengthScale(0.5F) <= 0.9F) return;
        if (player.getRandom().nextInt(100) >= AmmConfig.RIPOSTE_SHIELD_PIERCE_PERCENT.get()) return;
        if (target instanceof Mob mob) {
            if (MobCombatHandler.hasRaisedShield(mob)) MobCombatHandler.disableShield(mob);
            return;
        }
        if (!(target instanceof Player victim) || !victim.isUsingItem()) return;
        ItemStack active = victim.getUseItem();
        if (!BlockingRules.isShield(active)) return;
        victim.getCooldowns().addCooldown(active, 100);
        victim.stopUsingItem();
        player.level().broadcastEntityEvent(victim, (byte) 30); // shield-break animation + sound
    }

    /** Starts the countdown as soon as the guard is down. */
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || UNTIL.isEmpty()) return;
        if (!BlockingHandler.isBlocking(player)) armCountdown(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UNTIL.remove(event.getEntity().getUUID());
    }
}