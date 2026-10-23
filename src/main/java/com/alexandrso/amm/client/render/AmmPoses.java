package com.alexandrso.amm.client.render;

import com.alexandrso.amm.config.AmmConfig;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;

/**
 * Arm/body poses, ported from CombinedModelPlayer / BlockingAnimations / MobCombatAnimations /
 * WoundedCrawlAnimations. One implementation for players AND mobs and for armor too (armor is
 * the same HumanoidModel run through the same setupAnim, so the old CombinedModelArmor
 * "copy the pose" step is no longer needed). Only xRot/yRot/zRot are changed, never the pivots.
 */
public final class AmmPoses {
    private static final float DUAL_GUARD_PITCH = -0.85F;
    private static final float DUAL_GUARD_YAW = 0.65F;
    private static final float TOOL_TILT = 0.3F;

    // wounded zombie
    public static final float FOLD_DEGREES = 45.0F;
    private static final float ARM_REACH_FORWARD = 0.65F;
    private static final float HEAD_PITCH = -1.1F;
    private static final float HEAD_YAW_LIMIT = 60.0F;
    private static final float HEAD_PITCH_UP_LIMIT = -35.0F;
    private static final float HEAD_PITCH_DOWN_LIMIT = 25.0F;
    private static final float CYCLE_SPEED = 0.9F;
    private static final float ARM_PULL = 0.35F;
    private static final float LEG_DRAG = 0.2F;

    private AmmPoses() {
    }

    public static void apply(HumanoidModel<?> m, AmmPoseData d, HumanoidRenderState s) {
        // Endermen reuse HumanoidModel but their "hat" is the jaw: never touch them.
        if (m.getClass().getSimpleName().contains("Enderman")) return;
        if (d.woundedCrawl && !s.isBaby) {
            woundedCrawl(m, d, s);
            return;
        }

        boolean whole = false;
        boolean headTouched = false;
        if (d.wallClimb) {
            headTouched = true;
            wallClimb(m, d, s);
            whole = true;
        } else if (d.spinning) {
            spin(m, d, s);
            headTouched = d.dual;
            whole = true;
        } else if (d.pointing) {
            point(m, d, s);
            whole = true;
        }

        if (d.blocking) {
            guard(m, d);
        } else if (!whole) {
            toolTilt(m, d, s);
            dualSwing(m, d);
        }
        if (headTouched) syncHat(m);
    }

    /**
     * The hat layer follows the head's ROTATION only. Copying the whole pose (loadPose) also copied the
     * head's position, which put the enderman's jaw (its "hat" part, mounted at its own offset) above the head.
     */
    private static void syncHat(HumanoidModel<?> m) {
        m.hat.xRot = m.head.xRot;
        m.hat.yRot = m.head.yRot;
        m.hat.zRot = m.head.zRot;
    }

    /** Arms-up "gripping the ledge" pose; while carrying only the primary hand climbs, the other stays in the cradle. */
    private static void wallClimb(HumanoidModel<?> m, AmmPoseData d, HumanoidRenderState s) {
        float shimmer = Mth.cos(s.ageInTicks * 1.2F) * 0.05F;
        m.leftArm.xRot = -2.6F + shimmer;
        m.rightArm.xRot = -2.6F - shimmer;
        m.leftArm.yRot = 0.15F;
        m.rightArm.yRot = -0.15F;
        m.leftArm.zRot = 0.1F;
        m.rightArm.zRot = -0.1F;
        m.head.xRot = -0.3F;
    }

    // ── gestures ────────────────────────────────────────────────────────────────

    private static void spin(HumanoidModel<?> m, AmmPoseData d, HumanoidRenderState s) {
        if (d.dual) {
            float t = s.ageInTicks * 0.32F;
            m.rightArm.xRot = -1.1F + Mth.cos(t);
            m.rightArm.yRot = -0.45F;
            m.rightArm.zRot = -0.25F + Mth.sin(t) * 0.9F;
            m.leftArm.xRot = -1.1F + Mth.cos(t + Mth.PI);
            m.leftArm.yRot = 0.45F;
            m.leftArm.zRot = 0.25F - Mth.sin(t + Mth.PI) * 0.9F;
            float twist = Mth.sin(t) * 0.18F; // small counter-twist: reads as a flourish
            m.body.yRot = twist;
            m.head.yRot += twist * 0.5F;
            return;
        }
        float t = s.ageInTicks * 0.3F;
        var arm = d.rightMain ? m.rightArm : m.leftArm;
        arm.xRot = -0.7F + Mth.cos(t) * 1.1F;
        arm.yRot = 0.0F;
        arm.zRot = Mth.sin(t) * 1.1F;
    }

    /** Main hand raised and tracking the head: pitch raises/lowers it, head yaw swings it sideways. */
    private static void point(HumanoidModel<?> m, AmmPoseData d, HumanoidRenderState s) {
        float pitch = s.xRot * Mth.DEG_TO_RAD;
        float yaw = Mth.clamp(s.yRot, -75.0F, 75.0F) * Mth.DEG_TO_RAD;
        var arm = d.rightMain ? m.rightArm : m.leftArm;
        arm.xRot = -1.4F + pitch;
        arm.yRot = yaw * 0.6F;
        arm.zRot = 0.0F;
    }

    // ── combat ──────────────────────────────────────────────────────────────────
    /** Raised weapon guard. Players: old-style sword block; polearms: two-handed, shaft horizontal; mobs: slightly lower. Shields use vanilla's own pose. */
    private static void guard(HumanoidModel<?> m, AmmPoseData d) {
        if (d.hasShield) return;

        if (d.mainCanBlock && d.offCanBlock && d.dual) {
            m.rightArm.xRot = DUAL_GUARD_PITCH + 0.08F;
            m.rightArm.yRot = -DUAL_GUARD_YAW;
            m.rightArm.zRot = 0.0F;
            m.leftArm.xRot = DUAL_GUARD_PITCH - 0.08F;
            m.leftArm.yRot = DUAL_GUARD_YAW;
            m.leftArm.zRot = 0.0F;
        } else if (d.guardPolearm) {
            // Spear / trident held across the chest: both arms raised forward and slightly inward,
            // the holding forearm twisted 90 degrees (zRot) so the shaft lies horizontally.
            boolean holdRight = (d.polearmInMain == d.rightMain);
            var hold = holdRight ? m.rightArm : m.leftArm;
            var help = holdRight ? m.leftArm : m.rightArm;
            float in = holdRight ? -1.0F : 1.0F;      // sign that turns the HOLDING arm toward the body centre

            hold.xRot = -1.40F;                        // raised forward
            hold.yRot = in * 0.30F;                    // slightly inward
            hold.zRot = in * 1.55F;                    // twist: shaft becomes horizontal

            help.xRot = -1.40F;                        // same height, so both hands sit on the same shaft
            help.yRot = -in * 0.30F;                   // the other arm also turns inward
            help.zRot = -in * 1.55F;
        } else if (d.mainCanBlock || d.offCanBlock) {
            boolean withRight = (d.mainCanBlock == d.rightMain);
            var arm = withRight ? m.rightArm : m.leftArm;
            if (d.isPlayer) {
                arm.xRot = arm.xRot * 0.5F - 0.9424779F;
                arm.yRot = (withRight ? -1.0F : 1.0F) * Mth.PI / 6.0F;
            } else {
                arm.xRot = -1.0F;
                arm.yRot = withRight ? -0.52F : 0.52F;
            }
            arm.zRot = 0.0F;
        }
    }

    private static void toolTilt(HumanoidModel<?> m, AmmPoseData d, HumanoidRenderState s) {
        if (!AmmConfig.HELD_TOOL_TILT.get() || d.usingItem || d.busy) return;
        float fade = 1.0F - Mth.clamp(s.swingAnimation * 3.0F, 0.0F, 1.0F);
        if (fade <= 0.0F) return;
        if (d.mainTool) (d.rightMain ? m.rightArm : m.leftArm).xRot += TOOL_TILT * fade;
        if (d.offTool) (d.rightMain ? m.leftArm : m.rightArm).xRot += TOOL_TILT * fade;
    }

    /** The delayed off-hand strike of the dual style: a swing of the off arm, same shape as vanilla's. */
    private static void dualSwing(HumanoidModel<?> m, AmmPoseData d) {
        float sp = d.offhandSwing;
        if (sp <= 0.0F || !d.dual) return;
        var offArm = d.rightMain ? m.leftArm : m.rightArm;
        boolean offIsLeft = d.rightMain;

        float twist = Mth.sin(Mth.sqrt(sp) * Mth.PI * 2.0F) * 0.2F * (offIsLeft ? -1.0F : 1.0F);
        float f1 = 1.0F - sp;
        f1 = f1 * f1;
        f1 = f1 * f1;
        f1 = 1.0F - f1;
        float f2 = Mth.sin(f1 * Mth.PI);
        float f3 = Mth.sin(sp * Mth.PI) * -(m.head.xRot - 0.7F) * 0.75F;

        offArm.xRot -= f2 * 1.2F + f3;
        offArm.yRot += twist * 2.0F;
        offArm.zRot += Mth.sin(sp * Mth.PI) * (offIsLeft ? 0.4F : -0.4F);
    }

    // ── wounded zombie ──────────────────────────────────────────────────────────

    /** Prone, dragging-itself-along pose. The whole body is folded forward in LivingEntityRendererMixin; here only the limbs. */
    private static void woundedCrawl(HumanoidModel<?> m, AmmPoseData d, HumanoidRenderState s) {
        float strength = Mth.clamp(s.walkAnimationSpeed * 3.0F, 0.0F, 1.0F);
        float phase = s.walkAnimationPos * CYCLE_SPEED;
        float cycle = Mth.sin(phase * 1.6F);
        float cycleOpp = Mth.cos(phase * 1.6F);

        float fold = FOLD_DEGREES * Mth.DEG_TO_RAD;
        float levelOffset = Mth.HALF_PI - fold; // makes a hanging limb point straight back
        float breath = Mth.sin(s.ageInTicks * 0.12F) * 0.05F * (1.0F - strength);

        float armBase = -fold - ARM_REACH_FORWARD;
        m.rightArm.xRot = armBase + cycle * ARM_PULL * strength + breath;
        m.leftArm.xRot = armBase - cycle * ARM_PULL * strength - breath;
        m.rightArm.yRot = 0.0F;
        m.leftArm.yRot = 0.0F;
        m.rightArm.zRot = 0.0F;
        m.leftArm.zRot = 0.0F;

        m.rightLeg.xRot = levelOffset - cycle * LEG_DRAG * strength;
        m.leftLeg.xRot = levelOffset + cycle * LEG_DRAG * strength;
        m.rightLeg.yRot = 0.0F;
        m.leftLeg.yRot = 0.0F;
        m.rightLeg.zRot = 0.05F * cycleOpp * strength;
        m.leftLeg.zRot = -0.05F * cycleOpp * strength;

        float netHeadYaw = Mth.clamp(s.yRot, -HEAD_YAW_LIMIT, HEAD_YAW_LIMIT);
        float lookPitch = Mth.clamp(s.xRot, HEAD_PITCH_UP_LIMIT, HEAD_PITCH_DOWN_LIMIT);
        m.head.xRot = HEAD_PITCH + levelOffset + lookPitch * Mth.DEG_TO_RAD + 0.08F * cycleOpp * strength + breath;
        m.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        m.head.zRot = 0.1F * cycle * strength;
        syncHat(m);

        m.body.xRot = 0.0F;
        m.body.yRot = 0.0F;
        m.body.zRot = 0.0F;
    }
}