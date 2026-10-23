package com.alexandrso.amm.client.render;

import com.alexandrso.amm.AlexMovementsMod;
import com.alexandrso.amm.client.DualClientState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * First-person versions of the poses that {@link AmmPoses} draws in third person (guard, twirl,
 * pointing, ledge climb, carry cradle, off-hand strike of the dual style).
 * <p>
 * The first-person hands are not a HumanoidModel, so the model poses never reach them. Instead,
 * before vanilla draws a hand, the pose stack is moved/rotated around that hand's resting
 * position; vanilla then draws the hand and item on top of it (equip animation, swing and
 * item transforms keep working).
 * <p>
 * The transform is pushed here and popped when the next hand event of the same stack arrives
 * (the stack is a per-frame object of the game renderer, so a leftover push on the last hand is harmless).
 */
@EventBusSubscriber(modid = AlexMovementsMod.MODID, value = Dist.CLIENT)
public final class FirstPersonPoses {
    /** Resting position of a first-person hand (vanilla ItemInHandRenderer#applyItemArmTransform). */
    private static final float HAND_X = 0.56F;
    private static final float HAND_Y = -0.52F;
    private static final float HAND_Z = -0.72F;

    /** Pose fade speed, progress per tick. */
    private static final float FADE = 0.25F;

    private static final AmmPoseData DATA = new AmmPoseData();

    // progress 0..1 of every pose, previous and current tick (interpolated with the partial tick)
    private static float block, blockPrev;
    private static float spin, spinPrev;
    private static float point, pointPrev;
    private static float climb, climbPrev;
    // what the poses of this tick want (kept for the render phase)
    private static boolean dualGuard;
    private static boolean guardMain;
    private static boolean polearmGuard;
    private static boolean polearmMain;
    private static boolean spinOn;
    private static float spinTicks, spinTicksPrev;

    private static PoseStack pushed;

    private FirstPersonPoses() {
    }

    // ── per tick: which poses are wanted ────────────────────────────────────────

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        blockPrev = block;
        spinPrev = spin;
        pointPrev = point;
        climbPrev = climb;
        spinTicksPrev = spinTicks;
        if (player == null) {
            spinTicks = spinTicksPrev = 0.0F;
            return;
        }

        AmmPoseData d = DATA;
        d.fill(player, 1.0F);

        boolean wantGuard = d.blocking && !d.hasShield && (d.mainCanBlock || d.offCanBlock);
        boolean wantClimb = d.wallClimb;
        boolean whole = wantClimb;
        boolean wantSpin = !whole && !wantGuard && d.spinning;
        boolean wantPoint = !whole && !wantGuard && !wantSpin && d.pointing;

        dualGuard = d.mainCanBlock && d.offCanBlock && d.dual;
        guardMain = d.mainCanBlock;
        if (wantGuard) {
            polearmGuard = d.guardPolearm;
            polearmMain = d.polearmInMain;
        }

        spinOn = wantSpin;
        if (wantSpin) spinTicks += 1.0F;
        else if (spin <= 0.0F) spinTicks = spinTicksPrev = 0.0F; // next twirl starts from angle 0: no jump

        block = fade(block, wantGuard);
        spin = fade(spin, wantSpin);
        point = fade(point, wantPoint);
        climb = fade(climb, wantClimb);
    }

    private static float fade(float value, boolean on) {
        return on ? Math.min(1.0F, value + FADE) : Math.max(0.0F, value - FADE);
    }

    private static float smooth(float prev, float cur, float partial) {
        float p = Mth.lerp(partial, prev, cur);
        return p * p * (3.0F - 2.0F * p);
    }

    // ── per hand: move the pose stack ───────────────────────────────────────────

    @SubscribeEvent
    static void onRenderHand(RenderHandEvent event) {
        PoseStack ps = event.getPoseStack();
        if (pushed != null) {
            if (pushed == ps) ps.popPose(); // the previous hand's transform must not leak into this one
            pushed = null;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        boolean isMain = event.getHand() == InteractionHand.MAIN_HAND;
        boolean rightArm = isMain == (player.getMainArm() == HumanoidArm.RIGHT);
        float side = rightArm ? 1.0F : -1.0F;

        float pBlock = smooth(blockPrev, block, partial);
        float pSpin = smooth(spinPrev, spin, partial);
        float pPoint = smooth(pointPrev, point, partial);
        float pClimb = smooth(climbPrev, climb, partial);
        float offSwing = isMain ? 0.0F : DualClientState.offhandSwingProgress(player, partial);
        boolean dualNow = DATA.dual;
        if (offSwing > 0.0F && !dualNow) offSwing = 0.0F;

        boolean any = pBlock > 0.001F || pSpin > 0.001F || pPoint > 0.001F || pClimb > 0.001F
                || offSwing > 0.0F;
        if (!any) return;

        float age = player.tickCount + partial;

        ps.pushPose();
        pushed = ps;

        // ledge climb: both hands reach up and grip
        if (pClimb > 0.001F) {
            float shimmer = Mth.cos(age * 1.2F + (isMain ? 0.0F : Mth.PI)) * 0.02F;
            ps.translate(-side * 0.16F * pClimb, (0.58F + shimmer) * pClimb, -0.04F * pClimb);
            rotateAroundHand(ps, side, -48.0F * pClimb, 0.0F, side * 6.0F * pClimb);
        }

        // weapon guard
        if (pBlock > 0.001F) {
            if (polearmGuard) {
                if (isMain == polearmMain) {
                    ps.translate(-side * 0.10F * pBlock, 0.28F * pBlock, 0.10F * pBlock);
                    ps.translate(side * HAND_X, HAND_Y, HAND_Z);
                    ps.rotate(Axis.YP.rotationDegrees(side * 70.0F * pBlock));
                    ps.rotate(Axis.XP.rotationDegrees(90.0F * pBlock));
                    ps.rotate(Axis.YP.rotationDegrees(side * 10.0F * pBlock));
                    ps.translate(-side * HAND_X, -HAND_Y, -HAND_Z);
                }
            } else if (dualGuard || isMain == guardMain) {
                // sword: old (pre-1.9) block
                ps.translate(-side * 0.14F * pBlock, 0.08F * pBlock, 0.14F * pBlock);
                ps.translate(side * HAND_X, HAND_Y, HAND_Z);
                ps.rotate(Axis.YP.rotationDegrees(side * 75.0F * pBlock));
                ps.rotate(Axis.XP.rotationDegrees(-80.0F * pBlock));
                ps.rotate(Axis.YP.rotationDegrees(side * 15.0F * pBlock));
                ps.translate(-side * HAND_X, -HAND_Y, -HAND_Z);
            }
        }

        // twirl: the item spins around the grip (both hands, opposite phase, when dual-wielding)
        if (pSpin > 0.001F && (DATA.dual || isMain)) {
            float t = Mth.lerp(partial, spinTicksPrev, spinTicks);
            float angle = (t * 17.2F) % 360.0F; // starts at 0 with the twirl, so nothing jumps
            if (DATA.dual && !isMain) angle = -angle + 180.0F;
            ps.translate(-side * 0.26F * pSpin, 0.24F * pSpin, 0.02F * pSpin);
            rotateAroundHand(ps, side, -20.0F * pSpin, 0.0F, spinOn ? angle : angle * pSpin);
        }

        // pointing: the main hand is raised and tilted forward
        if (pPoint > 0.001F && isMain) {
            ps.translate(-side * 0.10F * pPoint, 0.30F * pPoint, 0.0F);
            rotateAroundHand(ps, side, -32.0F * pPoint, 0.0F, 0.0F);
        }

        // delayed off-hand strike of the dual style: vanilla's swing, applied to the other hand
        if (offSwing > 0.0F) offhandSwing(ps, side, offSwing);
    }

    /** Rotates around the resting position of the hand: x pitch, y yaw, z roll (degrees). */
    private static void rotateAroundHand(PoseStack ps, float side, float xDeg, float yDeg, float zDeg) {
        ps.translate(side * HAND_X, HAND_Y, HAND_Z);
        if (yDeg != 0.0F) ps.rotate(Axis.YP.rotationDegrees(yDeg));
        if (xDeg != 0.0F) ps.rotate(Axis.XP.rotationDegrees(xDeg));
        if (zDeg != 0.0F) ps.rotate(Axis.ZP.rotationDegrees(zDeg));
        ps.translate(-side * HAND_X, -HAND_Y, -HAND_Z);
    }

    /** Same motion as vanilla's ItemInHandRenderer swing (applyItemArmAttackTransform), for the hand vanilla does not swing. */
    private static void offhandSwing(PoseStack ps, float side, float sp) {
        float sqrt = Mth.sqrt(sp);
        float f = -0.4F * Mth.sin(sqrt * Mth.PI);
        float f1 = 0.2F * Mth.sin(sqrt * Mth.PI * 2.0F);
        float f2 = -0.2F * Mth.sin(sp * Mth.PI);
        ps.translate(side * f, f1, f2);

        ps.translate(side * HAND_X, HAND_Y, HAND_Z);
        float g = Mth.sin(sp * sp * Mth.PI);
        ps.rotate(Axis.YP.rotationDegrees(side * (45.0F + g * -20.0F)));
        float g1 = Mth.sin(sqrt * Mth.PI);
        ps.rotate(Axis.ZP.rotationDegrees(side * g1 * -20.0F));
        ps.rotate(Axis.XP.rotationDegrees(g1 * -80.0F));
        ps.rotate(Axis.YP.rotationDegrees(side * -45.0F));
        ps.translate(-side * HAND_X, -HAND_Y, -HAND_Z);
    }
}