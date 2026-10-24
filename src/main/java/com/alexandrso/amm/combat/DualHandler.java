package com.alexandrso.amm.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.alexandrso.amm.AlexMovementsMod;
import com.alexandrso.amm.config.AmmConfig;
import com.alexandrso.amm.config.BlockingRules;
import com.alexandrso.amm.network.DualSwingPayload;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Dual style: a blockable weapon in BOTH hands. The main hand hits normally; a second strike
 * from the off hand follows after dualOffhandDelayTicks. Every strike deals dualDamagePercent,
 * attack speed is that of the slowest weapon (+ penalty), and unbroken double strikes build a
 * combo that speeds attacks up.
 * <p>
 * The off-hand strike is vanilla's own {@link Player#attack}: the off-hand stack (and its attribute
 * modifiers) are swapped into the main hand for the duration of the call, so crits, sweeping,
 * knockback, enchantments, durability and other mods' on-hit effects all behave exactly like a
 * normal hit with that weapon.
 * <p>
 * A swing at thin air also queues an off-hand strike: when its delay is up, whatever stands under
 * the crosshair (within the off-hand reach) is hit.
 * <p>
 * If vanilla's attack bails out before it ever tries to hurt the target (no attack/damage event seen,
 * nothing cancelled), the strike falls back to a direct hurt with the off-hand weapon's damage.
 */
@EventBusSubscriber(modid = AlexMovementsMod.MODID)
public final class DualHandler {
    private static final Identifier DUAL_SPEED_ID = Identifier.fromNamespaceAndPath(AlexMovementsMod.MODID, "dual_speed");
    private static final double MIN_OFFHAND_REACH = 0.5D;

    /** Logs every off-hand strike's pipeline (events seen / cancelled). Set to false once dual works. */
    private static final boolean DEBUG = true;

    /** True while we run Player#attack for the off hand: every other attack listener must ignore that event. */
    private static boolean performing;

    // Pipeline probes of the strike in progress (filled by the listeners at the bottom).
    private static LivingEntity strikeTarget;
    private static boolean attackSeen;
    private static boolean attackCancelled;
    private static boolean damageSeen;
    private static boolean damageCancelled;
    private static float damageAmount;

    /** LivingEntity#lastHurt: the strength of the last hit. While damage frames run, vanilla only lets a LATER hit through by the difference to it. */
    private static final java.lang.reflect.Field LAST_HURT = findLastHurt();

    private static java.lang.reflect.Field findLastHurt() {
        try {
            java.lang.reflect.Field f = LivingEntity.class.getDeclaredField("lastHurt");
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            AlexMovementsMod.LOGGER.error("[amm] cannot reach LivingEntity#lastHurt: same-strength off-hand strikes will be swallowed", ex);
            return null;
        }
    }

    /** Wipes the damage frames: the invulnerability timer AND the remembered strength of the last hit. */
    static void clearHurtFrames(LivingEntity e) {
        e.invulnerableTime = 0;
        if (LAST_HURT == null) return;
        try {
            LAST_HURT.setFloat(e, 0.0F);
        } catch (IllegalAccessException | RuntimeException ignored) {
            // logged once at startup if the field is unreachable
        }
    }

    private DualHandler() {
    }

    public static boolean isPerformingOffhand() {
        return performing;
    }

    // ── damage penalty ──────────────────────────────────────────────────────────

    /**
     * Every melee hit of a dual-wielder (main hand AND the off-hand strike, during which the
     * off-hand weapon sits in the main-hand slot) deals only dualDamagePercent of its damage.
     * During the off-hand strike the victim's invulnerability frames are cleared here too: this is
     * the last moment before vanilla checks them, so nothing can re-arm them in between.
     */
    @SubscribeEvent
    static void onDualDamage(LivingIncomingDamageEvent event) {
        DamageSource src = event.getSource();
        if (!src.is(DamageTypes.PLAYER_ATTACK)) return;
        if (!(src.getEntity() instanceof Player player) || src.getDirectEntity() != player) return;
        if (player.level().isClientSide()) return;
        if (performing && event.getEntity() != player) clearHurtFrames(event.getEntity());
        if (!BlockingRules.isDualWielding(player)) return;

        int percent = BlockingRules.dualDamagePercent(player.getMainHandItem());
        if (percent >= 100) return;
        event.setAmount(event.getAmount() * percent / 100.0F);
    }

    // ── attack speed: slowest weapon ────────────────────────────────────────────

    /**
     * Value of an attribute as if {@code other} were held in the main hand instead of {@code main}:
     * main's modifiers are removed, other's added, everything else (potions, armor, other mods) stays.
     */
    private static double attributeWithItem(Player player, Holder<Attribute> attribute, ItemStack main, ItemStack other) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) return Double.NaN;

        Set<Identifier> mainIds = new HashSet<>();
        mainIds.add(DUAL_SPEED_ID); // never count our own dual attack-speed modifier
        main.forEachModifier(EquipmentSlot.MAINHAND, (h, m) -> {
            if (h.value() == attribute.value()) mainIds.add(m.id());
        });
        List<AttributeModifier> mods = new ArrayList<>();
        for (AttributeModifier m : inst.getModifiers()) {
            if (!mainIds.contains(m.id())) mods.add(m);
        }
        other.forEachModifier(EquipmentSlot.MAINHAND, (h, m) -> {
            if (h.value() == attribute.value()) mods.add(m);
        });

        double value = inst.getBaseValue();
        for (AttributeModifier m : mods) if (m.operation() == AttributeModifier.Operation.ADD_VALUE) value += m.amount();
        double result = value;
        for (AttributeModifier m : mods) if (m.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) result += value * m.amount();
        for (AttributeModifier m : mods) if (m.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) result *= 1.0D + m.amount();
        return result;
    }

    /**
     * Normal dual style: slowest weapon, charge time made dualCooldownPenaltyPercent longer.
     * Combo active: the penalty is removed first, then dualComboSpeedBonusPercent is added.
     */
    private static void refreshDualSpeed(Player player) {
        AttributeInstance inst = player.getAttribute(Attributes.ATTACK_SPEED);
        if (inst == null) return;

        double wanted = 0.0D;
        if (BlockingRules.isDualWielding(player)) {
            ItemStack main = player.getMainHandItem();
            ItemStack off = player.getOffhandItem();
            double mainSpeed = attributeWithItem(player, Attributes.ATTACK_SPEED, main, main);
            double offSpeed = attributeWithItem(player, Attributes.ATTACK_SPEED, main, off);
            if (!Double.isNaN(mainSpeed) && !Double.isNaN(offSpeed) && mainSpeed > 0.0D && offSpeed > 0.0D) {
                double slowest = Math.min(mainSpeed, offSpeed);
                double target = isComboActive(player)
                        ? slowest * (1.0D + AmmConfig.DUAL_COMBO_SPEED_BONUS_PERCENT.get() / 100.0D)
                        : slowest / (1.0D + AmmConfig.DUAL_COOLDOWN_PENALTY_PERCENT.get() / 100.0D);
                wanted = target / mainSpeed - 1.0D;
                if (Math.abs(wanted) < 1.0E-6D) wanted = 0.0D;
            }
        }

        AttributeModifier existing = inst.getModifier(DUAL_SPEED_ID);
        if (existing != null && Math.abs(existing.amount() - wanted) < 1.0E-6D) return;
        inst.removeModifier(DUAL_SPEED_ID);
        if (wanted != 0.0D) {
            inst.addTransientModifier(new AttributeModifier(DUAL_SPEED_ID, wanted, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        tickCombo(player);
        refreshDualSpeed(player);
    }

    // ── combo ───────────────────────────────────────────────────────────────────

    private static final class Combo {
        int strikes;
        boolean active;
        int lastTick;
    }

    private static final Map<UUID, Combo> COMBOS = new HashMap<>();

    public static boolean isComboActive(Player player) {
        Combo c = COMBOS.get(player.getUUID());
        return c != null && c.active;
    }

    private static void comboStrike(Player player) {
        if (!AmmConfig.DUAL_COMBO_ENABLED.get() || player.level().isClientSide()) return;
        Combo c = COMBOS.computeIfAbsent(player.getUUID(), id -> new Combo());
        c.lastTick = player.tickCount;
        c.strikes++;
        if (!c.active && c.strikes >= AmmConfig.DUAL_COMBO_PAIRS.get() * 2) {
            c.active = true;
            refreshDualSpeed(player);
            if (AmmConfig.DUAL_COMBO_NOTIFY.get() && player instanceof ServerPlayer) {
                ((ServerPlayer) player).sendOverlayMessage(Component.translatable("amm.dual.combo.start"));
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4F, 1.8F);
            }
        }
    }

    /** A miss, guarding, weapon swap or a pause: the combo is lost. */
    public static void comboBreak(Player player) {
        if (player.level().isClientSide()) return;
        Combo c = COMBOS.remove(player.getUUID());
        if (c == null) return;
        if (c.active) {
            refreshDualSpeed(player);
            if (AmmConfig.DUAL_COMBO_NOTIFY.get() && player instanceof ServerPlayer) {
                ((ServerPlayer) player).sendOverlayMessage(Component.translatable("amm.dual.combo.end"));
            }
        }
    }

    private static void tickCombo(Player player) {
        Combo c = COMBOS.get(player.getUUID());
        if (c == null) return;
        if (!AmmConfig.DUAL_COMBO_ENABLED.get() || !BlockingRules.isDualWielding(player)
                || player.tickCount - c.lastTick > AmmConfig.DUAL_COMBO_TIMEOUT_TICKS.get()) {
            comboBreak(player);
        }
    }

    // ── queued off-hand strike ──────────────────────────────────────────────────

    private static final class Pending {
        final Player player;
        /** Target entity id, or -1 for a swing at thin air (the target is looked up at strike time). */
        final int targetId;
        /** Attack-strength scale (0..1) read at the main-hand hit, before it reset the cooldown. */
        final float charge;
        /** Distance to the hitbox when the main hand connected (the game accepted it). */
        final double startDistance;
        /** Target hitbox at the main-hand hit, before knockback moved it (null for an air swing). */
        final AABB boxAtHit;
        final boolean comboEligible;
        /** Fall distance at the main-hand hit (crit and mace smash read it). */
        final double fallDistance;
        /** Whether the player stood on the ground at the main-hand hit (crit and sweep read it). */
        final boolean onGround;
        /** Whether the player was sprinting at the main-hand hit (sprint knockback reads it). */
        final boolean sprinting;
        /** A swing at thin air: strikes whatever is under the crosshair when the delay is up, never touches the combo. */
        final boolean air;
        float remaining;

        Pending(Player player, int targetId, float charge, double startDistance, AABB boxAtHit, float remaining,
                boolean comboEligible, double fallDistance, boolean onGround, boolean sprinting, boolean air) {
            this.player = player;
            this.targetId = targetId;
            this.charge = charge;
            this.startDistance = startDistance;
            this.boxAtHit = boxAtHit;
            this.remaining = remaining;
            this.comboEligible = comboEligible;
            this.fallDistance = fallDistance;
            this.onGround = onGround;
            this.sprinting = sprinting;
            this.air = air;
        }
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    private static void sendSwing(Player player, float delay) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new DualSwingPayload(player.getId(), delay));
    }

    /** Drops the queued off-hand strike (if any) and tells clients to stop its swing animation. */
    public static void cancelOffhandStrike(Player player) {
        if (!PENDING.removeIf(p -> p.player == player)) return;
        comboBreak(player);
        sendSwing(player, -1.0F);
    }

    /**
     * A swing at thin air: the off hand follows after the delay and hits whatever is under the crosshair
     * by then (nothing there: a show swing, no combo change).
     */
    public static void onAirSwing(Player player) {
        if (player.level().isClientSide() || !BlockingRules.isDualWielding(player) || BlockingHandler.isBlocking(player)) return;
        if (BlockingRules.dualDamagePercent(player.getOffhandItem()) <= 0) return;
        for (Pending p : PENDING) {
            if (p.player == player) return;
        }

        float delay = AmmConfig.DUAL_OFFHAND_DELAY_TICKS.get().floatValue();
        PENDING.add(new Pending(player, -1, player.getAttackStrengthScale(0.5F), 0.0D, null, delay,
                false, player.fallDistance, player.onGround(), player.isSprinting(), true));
        sendSwing(player, delay);
    }

    /** Called when the guard goes up. */
    public static void onGuardRaised(Player player) {
        cancelOffhandStrike(player);
        comboBreak(player);
    }

    /** The main hand hits normally (vanilla); queue the second strike from the off hand. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onMainHit(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (performing || player.level().isClientSide()) return;
        if (!(event.getTarget() instanceof LivingEntity target) || !target.isAttackable()) return;
        if (!BlockingRules.isDualWielding(player)) return;
        if (BlockingRules.dualDamagePercent(player.getOffhandItem()) <= 0) return;
        // A real hit replaces a queued air-swing strike (it would hit the same target twice).
        PENDING.removeIf(p -> p.player == player && p.air);
        for (Pending p : PENDING) {
            if (p.player == player) return; // one queued strike per player
        }

        // Read now: the main-hand hit is about to reset the cooldown.
        float charge = player.getAttackStrengthScale(0.5F);
        boolean cleanHit = charge >= AmmConfig.DUAL_COMBO_MIN_CHARGE_PERCENT.get() / 100.0F;
        if (cleanHit) comboStrike(player); // the main-hand half of a double strike
        else comboBreak(player);

        float delay = AmmConfig.DUAL_OFFHAND_DELAY_TICKS.get().floatValue();
        PENDING.add(new Pending(player, target.getId(), charge, distanceToBox(player, target.getBoundingBox()),
                target.getBoundingBox(), delay, cleanHit, player.fallDistance,
                player.onGround(), player.isSprinting(), false));
        sendSwing(player, delay);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        Iterator<Pending> it = PENDING.iterator();
        List<Pending> due = new ArrayList<>();
        while (it.hasNext()) {
            Pending p = it.next();
            p.remaining -= 1.0F;
            if (p.remaining > 0.0F) continue;
            it.remove();
            due.add(p);
        }
        for (Pending p : due) {
            int result = tryOffhandStrike(p);
            if (result > 0) {
                if (p.comboEligible) comboStrike(p.player);
            } else if (result < 0) {
                comboBreak(p.player);
            }
        }
    }

    private static double distanceToBox(Player player, AABB box) {
        Vec3 eyes = player.getEyePosition();
        double x = Mth.clamp(eyes.x, box.minX, box.maxX);
        double y = Mth.clamp(eyes.y, box.minY, box.maxY);
        double z = Mth.clamp(eyes.z, box.minZ, box.maxZ);
        return eyes.distanceTo(new Vec3(x, y, z));
    }

    /** The crosshair ray at strike time must still pass through the (widened) hitbox: turning away cancels the hit. */
    private static boolean isAimedAt(Player player, AABB rawBox, double reach) {
        Vec3 eyes = player.getEyePosition();
        Vec3 end = eyes.add(player.getViewVector(1.0F).scale(reach));
        AABB box = rawBox.inflate(AmmConfig.DUAL_OFFHAND_AIM_LEEWAY.get());
        return box.contains(eyes) || box.clip(eyes, end).isPresent();
    }

    private static int reject(String why) {
        AlexMovementsMod.LOGGER.info("[amm] off-hand strike skipped: {}", why);
        return -1;
    }

    /** Fallback aim test: the target centre is within ~40 degrees of the view direction (it moved/knocked back a bit). */
    private static boolean isFacing(Player player, LivingEntity target) {
        Vec3 to = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
        if (to.lengthSqr() < 1.0E-6D) return true;
        return to.normalize().dot(player.getViewVector(1.0F)) > 0.76D;
    }

    /** Reach = distance the main hand connected at, +/- the difference in the off-hand weapon's reach attribute. */
    private static double offhandReach(Player player, ItemStack main, ItemStack off, double startDistance) {
        double diff = 0.0D;
        AttributeInstance inst = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (inst != null) {
            double offValue = attributeWithItem(player, Attributes.ENTITY_INTERACTION_RANGE, main, off);
            if (!Double.isNaN(offValue)) diff = offValue - inst.getValue();
        }
        return Math.max(MIN_OFFHAND_REACH, startDistance + diff);
    }

    /** Reach of an air swing (nothing connected to measure from): the off-hand weapon's own interaction range. */
    private static double airReach(Player player, ItemStack main, ItemStack off) {
        AttributeInstance inst = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (inst == null) return 3.0D;
        double offValue = attributeWithItem(player, Attributes.ENTITY_INTERACTION_RANGE, main, off);
        return Math.max(MIN_OFFHAND_REACH, Double.isNaN(offValue) ? inst.getValue() : offValue);
    }

    /**
     * Target of an air-swing strike: the nearest living entity the crosshair ray passes through (hitbox widened
     * by the aim leeway), within the off-hand reach (+ leeway) and in line of sight. Null when nothing is there.
     */
    private static LivingEntity findAirTarget(Player player, ItemStack main, ItemStack off) {
        double reach = airReach(player, main, off) + AmmConfig.DUAL_OFFHAND_REACH_LEEWAY.get();
        Vec3 eyes = player.getEyePosition();
        Vec3 end = eyes.add(player.getViewVector(1.0F).scale(reach));
        double aim = AmmConfig.DUAL_OFFHAND_AIM_LEEWAY.get();

        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity c : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach + 1.0D))) {
            if (c == player || !c.isAlive() || c.isSpectator() || !c.isAttackable() || player.isAlliedTo(c)) continue;
            double d = distanceToBox(player, c.getBoundingBox());
            if (d > reach || d >= bestDist) continue;
            AABB box = c.getBoundingBox().inflate(aim);
            if (!box.contains(eyes) && box.clip(eyes, end).isEmpty()) continue;
            if (!player.hasLineOfSight(c)) continue;
            best = c;
            bestDist = d;
        }
        return best;
    }

    /** Moves a stack's main-hand attribute modifiers from {@code from} to {@code to} on the player (what the game does on an equipment change). */
    private static void swapModifiers(Player player, ItemStack from, ItemStack to) {
        from.forEachModifier(EquipmentSlot.MAINHAND, (holder, m) -> {
            AttributeInstance i = player.getAttribute(holder);
            if (i != null) i.removeModifier(m.id());
        });
        to.forEachModifier(EquipmentSlot.MAINHAND, (holder, m) -> {
            AttributeInstance i = player.getAttribute(holder);
            if (i != null) {
                i.removeModifier(m.id());
                i.addTransientModifier(m);
            }
        });
    }

    /** @return 1 when the off-hand strike landed, -1 when it failed, 0 when it is irrelevant. */
    private static int tryOffhandStrike(Pending p) {
        Player player = p.player;
        if (player.level().isClientSide() || !player.isAlive()) return 0;
        // A show swing that finds nothing never costs the combo.
        final int fail = p.air ? 0 : -1;
        if (BlockingHandler.isBlocking(player) || player.isUsingItem()) {
            AlexMovementsMod.LOGGER.info("[amm] off-hand strike skipped: blocking={} usingItem={}",
                    BlockingHandler.isBlocking(player), player.isUsingItem());
            return fail;
        }
        if (!BlockingRules.isDualWielding(player)) {
            AlexMovementsMod.LOGGER.info("[amm] off-hand strike skipped: no longer dual wielding");
            return fail;
        }

        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        LivingEntity target;
        if (p.air) {
            // Swung at thin air: hit whatever the crosshair is on NOW.
            target = findAirTarget(player, main, off);
            if (target == null) return 0;
        } else {
            Entity e = player.level().getEntity(p.targetId);
            // The main-hand hit already killed the target: the double strike was a success, not a miss.
            if (!(e instanceof LivingEntity t) || !t.isAlive()) return 1;
            target = t;

            double reach = offhandReach(player, main, off, p.startDistance) + AmmConfig.DUAL_OFFHAND_REACH_LEEWAY.get();
            // Knockback moved the target since the main-hand hit: judge against where it WAS and where it IS.
            AABB nowBox = target.getBoundingBox();
            if (distanceToBox(player, p.boxAtHit) > reach && distanceToBox(player, nowBox) > reach) {
                return reject("out of reach");
            }
            if (!isAimedAt(player, p.boxAtHit, reach) && !isAimedAt(player, nowBox, reach)
                    && !isFacing(player, target)) {
                return reject("not aimed at target");
            }
            if (!player.hasLineOfSight(target)) return reject("no line of sight");
        }

        final float healthBefore = target.getHealth();
        // Whether the target holds a raised guard (vanilla shield or this mod's weapon guard) before the strike.
        final boolean guarded = target.isBlocking() || BlockingHandler.isBlocking(target);
        // Live state, put back after the strike.
        final boolean realOnGround = player.onGround();
        final boolean realSprinting = player.isSprinting();

        // Strike AS the off-hand weapon: it sits in the main-hand slot (with its attribute modifiers)
        // while vanilla's Player#attack runs, and everything is put back in `finally`, in the same tick.
        Inventory inv = player.getInventory();
        int slot = inv.getSelectedSlot();
        final double realFall = player.fallDistance;
        boolean usedFallback = false;
        strikeTarget = target;
        attackSeen = false;
        attackCancelled = false;
        damageSeen = false;
        damageCancelled = false;
        damageAmount = -1.0F;
        performing = true;
        try {
            // Put the player back into the state of the main-hand hit, so vanilla judges this strike by the same
            // crit, sprint knockback and mace smash conditions.
            player.fallDistance += p.fallDistance - realFall;
            player.setOnGround(p.onGround);
            player.setSprinting(p.sprinting);
            inv.setItem(slot, off);
            inv.setItem(Inventory.SLOT_OFFHAND, main);
            swapModifiers(player, main, off);

            // Re-create the charge of the main-hand hit for the NEW weapon's attack delay.
            float delay = player.getCurrentItemAttackStrengthDelay();
            player.attackStrengthTicker = Math.max(0, (int) Math.ceil(p.charge * delay - 0.5F));

            // The main-hand hit started the target's invulnerability frames, which would swallow this one
            // (cleared again in onDualDamage, right before vanilla checks them).
            clearHurtFrames(target);
            if (BlockingRules.isSword(off)) clearSweepFrames(player, target);
            if (DEBUG) {
                AlexMovementsMod.LOGGER.info("[amm][dbg] strike start: target={} dmgAttr={} atkSpeed={} ticker={} delay={} invTime={} hurtTime={} off={}",
                        target, player.getAttributeValue(Attributes.ATTACK_DAMAGE),
                        player.getAttributeValue(Attributes.ATTACK_SPEED), player.attackStrengthTicker, delay,
                        target.invulnerableTime, target.hurtTime, off);
            }
            player.attack(target);

            // Vanilla bailed out before it even tried to hurt the target (no damage event, nothing cancelled): hurt directly.
            if (target.isAlive() && target.getHealth() >= healthBefore && !guarded
                    && !attackCancelled && !damageSeen) {
                float dmg = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
                dmg *= 0.2F + p.charge * p.charge * 0.8F;
                clearHurtFrames(target);
                boolean hurt = target.hurtOrSimulate(player.damageSources().playerAttack(player), dmg);
                usedFallback = true;
                AlexMovementsMod.LOGGER.info("[amm] off-hand strike: vanilla attack never reached the damage step (attackSeen={}), fallback hurt dmg={} -> {}",
                        attackSeen, dmg, hurt);
            }

            // An axe in the off hand knocks a raised shield out, like an axe in the main hand does.
            if (guarded && BlockingRules.isAxe(off) && target.isBlocking()) ShieldBreaker.disable(target);
        } catch (RuntimeException ex) {
            AlexMovementsMod.LOGGER.error("[amm] off-hand strike failed", ex);
        } finally {
            player.fallDistance += realFall - player.fallDistance;
            player.setOnGround(realOnGround);
            player.setSprinting(realSprinting);
            swapModifiers(player, off, main);
            inv.setItem(slot, main);
            inv.setItem(Inventory.SLOT_OFFHAND, off);
            performing = false;
            strikeTarget = null;
        }

        boolean landed = target.getHealth() < healthBefore || !target.isAlive();
        if (!landed) {
            AlexMovementsMod.LOGGER.info("[amm] off-hand strike dealt no damage to {}: invTime={} hurtTime={} guarded={} charge={} air={} | attackEvent seen={} cancelled={} | damageEvent seen={} cancelled={} amount={}",
                    target, target.invulnerableTime, target.hurtTime, guarded, p.charge, p.air,
                    attackSeen, attackCancelled, damageSeen, damageCancelled, damageAmount);
        } else if (DEBUG) {
            AlexMovementsMod.LOGGER.info("[amm][dbg] off-hand strike landed (fallback={}): {} -> {}",
                    usedFallback, healthBefore, target.getHealth());
        }
        return landed ? 1 : fail;
    }

    /**
     * Resets the invulnerability frames of everything in the sweep area around the target (the same box vanilla's sweep uses).
     * The main-hand sweep has just hurt those entities, and their frames would swallow the off-hand sweep.
     */
    private static void clearSweepFrames(Player player, LivingEntity target) {
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(1.0D, 0.25D, 1.0D))) {
            if (e == player || e == target || player.isAlliedTo(e)) continue;
            clearHurtFrames(e);
        }
    }

    // ── pipeline probes (the last word of every listener, cancelled events included) ──

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    static void probeAttack(AttackEntityEvent event) {
        if (!performing) return;
        attackSeen = true;
        attackCancelled = event.isCanceled();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    static void probeDamage(LivingIncomingDamageEvent event) {
        if (!performing || event.getEntity() != strikeTarget) return;
        damageSeen = true;
        damageCancelled = event.isCanceled();
        damageAmount = event.getAmount();
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player leaving = event.getEntity();
        PENDING.removeIf(p -> p.player == leaving);
        COMBOS.remove(leaving.getUUID());
    }
}