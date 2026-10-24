package com.alexandrso.amm.combat;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.alexandrso.amm.AlexMovementsMod;
import com.alexandrso.amm.config.AmmConfig;
import com.alexandrso.amm.config.BlockingRules;
import com.alexandrso.amm.entity.SittingSeat;
import com.alexandrso.amm.movement.AmmAttachments;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;

/**
 * Simple weapon AI for mobs: ANY Mob holding a blockable weapon or a shield.
 * <ul>
 *   <li><b>Guard</b>: when its target is close and swings, the mob raises its weapon for about a
 *       second (stands still, hits nobody, incoming melee goes through the SAME block-chance roll
 *       as for players - BlockingHandler) or, with a shield, raises the shield.</li>
 *   <li><b>Twirl</b>: idle "smart" mobs (not zombies) sometimes spin their weapon.</li>
 *   <li><b>Sit</b>: idle skeletons, illagers, villagers and witches sit down on a SittingSeat.</li>
 * </ul>
 * Compared to 1.12.2: shield blocking (facing arc, arrows, knockback, axes disabling the shield) is
 * vanilla now (blocks_attacks) and works for mobs as well, so those ~100 hand-written lines are gone.
 * State is shared with clients through the synced BLOCKING / SPINNING attachments.
 */
@EventBusSubscriber(modid = AlexMovementsMod.MODID)
public final class MobCombatHandler {
    /** Mobs currently holding a SHIELD up (vanilla item-use mechanics, not our weapon guard). */
    private static final Set<UUID> SHIELD_GUARD = new HashSet<>();
    /** Mob -> game time until which its shield is knocked out. */
    private static final Map<UUID, Long> SHIELD_DISABLED_UNTIL = new HashMap<>();
    /** Mob -> head yaw at the end of its previous tick while it guards with a shield (turn speed limit). */
    private static final Map<UUID, Float> SHIELD_HEAD_YAW = new HashMap<>();

    private MobCombatHandler() {
    }

    // ── wiring ──────────────────────────────────────────────────────────────────

    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) return;

        boolean hasGuard = false;
        boolean hasSpin = false;
        boolean hasSit = false;
        for (var wrapped : mob.goalSelector.getAvailableGoals()) {
            Goal g = wrapped.getGoal();
            if (g instanceof GuardGoal) hasGuard = true;
            if (g instanceof SpinGoal) hasSpin = true;
            if (g instanceof SitGoal) hasSit = true;
        }
        if (!hasGuard && AmmConfig.MOB_BLOCK_ENABLED.get()) mob.goalSelector.addGoal(0, new GuardGoal(mob));
        if (!hasSpin && AmmConfig.MOB_SPIN_ENABLED.get() && canTwirlKind(mob)) mob.goalSelector.addGoal(1, new SpinGoal(mob));
        if (!hasSit && AmmConfig.MOB_SIT_ENABLED.get() && SittingSeat.isSittingKind(mob)) mob.goalSelector.addGoal(2, new SitGoal(mob));
    }

    /** A guarding mob hits nothing: its melee damage is cancelled while the guard is up. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onGuardingMobAttack(LivingIncomingDamageEvent event) {
        DamageSource src = event.getSource();
        if (!(src.getEntity() instanceof Mob attacker) || attacker.level().isClientSide()) return;
        if (src.getDirectEntity() != attacker) return; // melee only
        if (BlockingHandler.isBlocking(attacker) || SHIELD_GUARD.contains(attacker.getUUID())) {
            event.setCanceled(true);
        }
    }

    /**
     * A mob with a raised shield blocks here, with no vanilla block delay: any melee hit or projectile
     * coming from the side the mob faces is cancelled, the shield wears, and an axe knocks it out.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onShieldMobHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) return;
        if (!hasRaisedShield(mob)) return;

        DamageSource src = event.getSource();
        if (src.is(DamageTypeTags.BYPASSES_SHIELD)) return;

        Vec3 origin = src.getSourcePosition();
        if (origin == null && src.getEntity() != null) origin = src.getEntity().position();
        if (origin == null) return; // no direction (fall, drowning...): a shield does not help

        // The mob must face the attack: within 90 degrees of where its head looks.
        double yaw = Math.toRadians(mob.getYHeadRot());
        Vec3 view = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 away = new Vec3(mob.getX() - origin.x, 0.0D, mob.getZ() - origin.z);
        if (away.lengthSqr() > 1.0E-6D && away.normalize().dot(view) >= 0.0D) return;

        float amount = event.getAmount();
        event.setCanceled(true);

        // Like vanilla: the shield only wears from hits of 3 or more damage.
        if (amount >= 3.0F) {
            EquipmentSlot slot = mob.getUsedItemHand() == InteractionHand.OFF_HAND
                    ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
            mob.getUseItem().hurtAndBreak(1 + (int) Math.floor(amount), mob, slot);
        }

        boolean axeHit = !src.is(DamageTypeTags.IS_PROJECTILE)
                && src.getDirectEntity() instanceof LivingEntity attacker
                && BlockingRules.isAxe(attacker.getMainHandItem());
        if (axeHit) {
            disableShield(mob); // shield down for 5 s, break sound
        } else {
            mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                    SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0F, 0.8F + mob.getRandom().nextFloat() * 0.4F);
        }
    }

    /**
     * A mob holding a raised shield turns its head slowly (mobShieldTurnSpeedDegrees per tick),
     * whatever the AI asks - so it can be outflanked. Vanilla's shield-facing check uses the body/head direction.
     */
    @SubscribeEvent
    static void onShieldMobTurn(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) return;
        UUID id = mob.getUUID();
        if (!SHIELD_GUARD.contains(id)) {
            if (!SHIELD_HEAD_YAW.isEmpty()) SHIELD_HEAD_YAW.remove(id);
            return;
        }
        float cur = mob.getYHeadRot();
        Float last = SHIELD_HEAD_YAW.get(id);
        if (last != null) {
            float max = AmmConfig.MOB_SHIELD_TURN_SPEED_DEGREES.get();
            float d = Mth.clamp(Mth.wrapDegrees(cur - last), -max, max);
            cur = last + d;
            mob.setYHeadRot(cur);
        }
        SHIELD_HEAD_YAW.put(id, cur);
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player) return;
        UUID id = event.getEntity().getUUID();
        SHIELD_GUARD.remove(id);
        SHIELD_DISABLED_UNTIL.remove(id);
        SHIELD_HEAD_YAW.remove(id);
        // BLOCKING / SPINNING attachments disappear with the entity.
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    /** Mobs that never twirl a weapon: zombies and giants (not smart enough), villagers, witches and illagers. */
    private static final Set<net.minecraft.world.entity.EntityType<?>> NO_TWIRL = Set.of(
            EntityTypes.VILLAGER, EntityTypes.WANDERING_TRADER, EntityTypes.WITCH,
            EntityTypes.VINDICATOR, EntityTypes.PILLAGER, EntityTypes.EVOKER, EntityTypes.ILLUSIONER);

    private static boolean canTwirlKind(Mob mob) {
        return !mob.is(EntityTypeTags.ZOMBIES) && mob.getType() != EntityTypes.GIANT
                && !NO_TWIRL.contains(mob.getType());
    }

    private static boolean canSitNow(Mob mob) {
        if (!SittingSeat.isSittingKind(mob)) return false;
        return !(mob instanceof Villager v) || v.getTradingPlayer() == null;
    }

    private static InteractionHand shieldHand(Mob mob) {
        if (BlockingRules.isShield(mob.getOffhandItem())) return InteractionHand.OFF_HAND;
        if (BlockingRules.isShield(mob.getMainHandItem())) return InteractionHand.MAIN_HAND;
        return null;
    }

    /** A wounded zombie lying on the ground cannot raise a guard. */
    private static boolean isCrawlingZombie(Mob mob) {
        return mob.getData(AmmAttachments.WOUNDED_CRAWL.get());
    }

    private static boolean isBow(ItemStack stack) {
        return stack.is(Items.BOW) || stack.is(Items.CROSSBOW);
    }

    /** What an idle mob may twirl: a blockable weapon/tool or a bow. */
    private static boolean holdsTwirlItem(Mob mob) {
        return holdsGuardWeapon(mob) || isBow(mob.getMainHandItem());
    }

    /** A blockable weapon/tool in the main hand and no shield in the other. */
    private static boolean holdsGuardWeapon(Mob mob) {
        ItemStack main = mob.getMainHandItem();
        if (!BlockingRules.canBlock(main)) return false;
        ItemStack off = mob.getOffhandItem();
        return off.isEmpty() || !BlockingRules.isShield(off);
    }

    /** An arrow / thrown projectile in flight that is heading for this mob (raises the shield in time). */
    private static boolean incomingProjectile(Mob mob) {
        AABB area = mob.getBoundingBox().inflate(8.0D, 4.0D, 8.0D);
        List<Projectile> list = mob.level().getEntitiesOfClass(Projectile.class, area);
        for (Projectile e : list) {
            if (!e.isAlive()) continue;
            Vec3 v = e.getDeltaMovement();
            double speedSq = v.lengthSqr();
            if (speedSq < 0.04D) continue; // stuck in a block / barely moving
            Vec3 to = new Vec3(mob.getX() - e.getX(), mob.getY() + mob.getBbHeight() * 0.5D - e.getY(), mob.getZ() - e.getZ());
            double dist = to.length();
            if (dist < 0.5D) continue;
            if (v.dot(to) / (Math.sqrt(speedSq) * dist) > 0.85D) return true;
        }
        return false;
    }

    public static boolean hasRaisedShield(LivingEntity mob) {
        return SHIELD_GUARD.contains(mob.getUUID()) && mob.isUsingItem() && !mob.getUseItem().isEmpty();
    }

    /** Knocks a mob's raised shield out for 5 seconds (riposte strike): shield down, break sound and particles. */
    public static void disableShield(LivingEntity mob) {
        SHIELD_DISABLED_UNTIL.put(mob.getUUID(), mob.level().getGameTime() + 100L);
        mob.stopUsingItem();
        mob.level().broadcastEntityEvent(mob, (byte) 30);
    }

    private static boolean isShieldDisabled(Mob mob) {
        Long until = SHIELD_DISABLED_UNTIL.get(mob.getUUID());
        if (until == null) return false;
        if (mob.level().getGameTime() >= until) {
            SHIELD_DISABLED_UNTIL.remove(mob.getUUID());
            return false;
        }
        return true;
    }

    /** A mob with a shield guards ONLY with it; without one it guards with a blockable weapon. */
    private static boolean canGuard(Mob mob) {
        if (isCrawlingZombie(mob)) return false;
        if (shieldHand(mob) != null && isShieldDisabled(mob)) return false;
        return shieldHand(mob) != null || holdsGuardWeapon(mob);
    }

    /** Same condition vanilla uses for skeletons / zombies catching fire in daylight. */
    private static boolean burnsInSun(Mob mob) {
        if (mob.level().isClientSide() || !mob.level().isBrightOutside()) return false;
        if (!mob.is(EntityTypeTags.SKELETONS) && !mob.is(EntityTypeTags.ZOMBIES)) return false;
        if (!mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return false;
        BlockPos pos = BlockPos.containing(mob.getX(), mob.getBoundingBox().maxY, mob.getZ());
        return mob.getLightLevelDependentMagicValue() > 0.5F && mob.level().canSeeSky(pos);
    }

    // ── Guard goal ──────────────────────────────────────────────────────────────

    private static final class GuardGoal extends Goal {
        private final Mob mob;
        private int guardTicks;
        private boolean usingShield;
        private long nextAllowed;

        GuardGoal(Mob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.noneOf(Flag.class)); // runs alongside every other goal, it only freezes the navigation
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            if (!canGuard(mob) || mob.isPassenger() || mob.isSwinging() || mob.isUsingItem()) return false;
            if (mob.level().getGameTime() < nextAllowed) return false;
            boolean hasShield = shieldHand(mob) != null;
            if (hasShield && incomingProjectile(mob)) return true; // shield goes up in time against a flying arrow
            LivingEntity target = mob.getTarget();
            if (target == null || !target.isAlive()) return false;
            int react = AmmConfig.MOB_BLOCK_REACT_CHANCE_PERCENT.get();
            // ... or against a target that is drawing a bow.
            if (hasShield && target.isUsingItem() && isBow(target.getUseItem()) && mob.distanceTo(target) <= 24.0F) {
                return mob.getRandom().nextInt(100) < react;
            }
            if (mob.distanceTo(target) > 4.0F) return false;
            if (target.isSwinging()) return mob.getRandom().nextInt(100) < react;
            return mob.getRandom().nextInt(25) == 0; // fairly often, even without provocation
        }

        @Override
        public void start() {
            int d = AmmConfig.MOB_BLOCK_DURATION_TICKS.get();
            guardTicks = d - d / 4 + mob.getRandom().nextInt(d / 2 + 1); // average d, +-25%
            InteractionHand shield = shieldHand(mob);
            usingShield = shield != null;
            if (usingShield) {
                guardTicks = guardTicks * Math.max(100, AmmConfig.MOB_SHIELD_DURATION_PERCENT.get()) / 100; // shields are held longer
                // Real vanilla shield: after a few ticks of use the game blocks like for a player.
                mob.startUsingItem(shield);
                SHIELD_GUARD.add(mob.getUUID());
            } else {
                BlockingHandler.setMobBlocking(mob, true);
            }
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = mob.getTarget();
            if (guardTicks <= 0 || !mob.isAlive() || isCrawlingZombie(mob)) return false;
            if (usingShield) return mob.isUsingItem() && shieldHand(mob) != null;
            if (target == null || !target.isAlive()) return false;
            return shieldHand(mob) == null && holdsGuardWeapon(mob);
        }

        @Override
        public void tick() {
            guardTicks--;
            mob.getNavigation().stop();
            LivingEntity target = mob.getTarget();
            float turn = usingShield ? AmmConfig.MOB_SHIELD_TURN_SPEED_DEGREES.get() : 30.0F;
            if (target != null) mob.getLookControl().setLookAt(target, turn, turn);
        }

        @Override
        public void stop() {
            if (usingShield) {
                if (mob.isUsingItem()) mob.stopUsingItem();
                SHIELD_GUARD.remove(mob.getUUID());
                usingShield = false;
            } else {
                BlockingHandler.setMobBlocking(mob, false);
            }
            int cd = AmmConfig.MOB_BLOCK_COOLDOWN_TICKS.get();
            nextAllowed = mob.level().getGameTime() + cd + mob.getRandom().nextInt(cd / 2 + 1);
        }
    }

    // ── Sit goal ────────────────────────────────────────────────────────────────

    /** An idle mob (no target, nobody near) sits down on the same invisible seat the player uses; the seat stands it up again. */
    private static final class SitGoal extends Goal {
        private final Mob mob;
        private long nextAllowed;

        SitGoal(Mob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.noneOf(Flag.class));
        }

        @Override
        public boolean canUse() {
            if (!AmmConfig.MOB_SIT_ENABLED.get() || !canSitNow(mob)) return false;
            if (mob.getTarget() != null || mob.getLastHurtByMob() != null || mob.hurtTime > 0) return false;
            if (mob.isPassenger() || !mob.onGround() || mob.isOnFire() || mob.isInWater()) return false;
            if (mob.level().getGameTime() < nextAllowed) return false;
            if (!mob.getNavigation().isDone()) return false;
            Vec3 v = mob.getDeltaMovement();
            if (v.x * v.x + v.z * v.z > 0.0005D) return false;
            if (BlockingHandler.isBlocking(mob)) return false;
            // Not while a player is within the mob's detection range (it would find a target and stand right up), nor in the sun.
            if (mob instanceof Villager villager) {
                // A villager is peaceful: it may sit with a player in view. Not while busy, trading, or with a player right next to it.
                if (SittingSeat.isVillagerBusy(villager)) return false;
                if (mob.level().getNearestPlayer(mob, 3.5D) != null) return false;
            } else {
                // Others: not while a player is within the mob's detection range (it would find a target and stand right up).
                double sight = Math.max(8.0D, mob.getAttributeValue(Attributes.FOLLOW_RANGE));
                if (mob.level().getNearestPlayer(mob, sight) != null) return false;
            }
            if (burnsInSun(mob)) return false;
            return mob.getRandom().nextInt(240) == 0;
        }

        @Override
        public void start() {
            nextAllowed = mob.level().getGameTime() + 600 + mob.getRandom().nextInt(600); // 30 - 60 s before the next sit
            BlockPos below = BlockPos.containing(mob.getX(), mob.getY() - 0.01D, mob.getZ());
            if (mob.level().getBlockState(below).isAir()) return;
            mob.getNavigation().stop();
            SittingSeat.seat(mob);
        }

        @Override
        public boolean canContinueToUse() {
            return false; // one-shot: the seat takes over
        }
    }

    // ── Twirl goal ──────────────────────────────────────────────────────────────

    private static final class SpinGoal extends Goal {
        private final Mob mob;
        private int spinTicks;

        SpinGoal(Mob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE)); // stands still while twirling, free to look around
        }

        private boolean canTwirl() {
            return canTwirlKind(mob) && mob.getTarget() == null && mob.hurtTime <= 0 && mob.onGround() && !mob.isPassenger()
                    && holdsTwirlItem(mob) && !BlockingHandler.isBlocking(mob);
        }

        @Override
        public boolean canUse() {
            return AmmConfig.MOB_SPIN_ENABLED.get() && canTwirl() && mob.getRandom().nextInt(200) == 0;
        }

        @Override
        public void start() {
            spinTicks = 40 + mob.getRandom().nextInt(41); // 2 - 4 seconds
            mob.setData(AmmAttachments.SPINNING.get(), true);
        }

        @Override
        public boolean canContinueToUse() {
            return spinTicks > 0 && mob.isAlive() && canTwirl();
        }

        @Override
        public void tick() {
            spinTicks--;
            mob.getNavigation().stop();
        }

        @Override
        public void stop() {
            mob.setData(AmmAttachments.SPINNING.get(), false);
        }
    }
}
