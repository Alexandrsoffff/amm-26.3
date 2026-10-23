package com.alexandrso.amm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.alexandrso.amm.client.render.AmmPoseData;
import com.alexandrso.amm.client.render.AmmPoses;
import com.alexandrso.amm.client.render.AmmStateAccess;
import com.alexandrso.amm.client.render.SpiderTilt;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    // Wounded zombie: hip pivot / ground drop / centre shift of the model (old WoundedZombieHandler constants).
    private static final float HIP_PIVOT = 0.75F;
    private static final float GROUND_DROP = 0.625F; // hip ends up 0.125 above the ground: legs lie flat
    private static final float CENTER_SHIFT = 0.1F;

    /** Copies our data from the entity into the render state (once per frame). */
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"), remap = false)
    private void amm$extract(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        AmmPoseData d = ((AmmStateAccess) state).amm$data();
        d.fill(entity, partialTick);
        // Spider crawling along a wall: add the vertical movement vanilla ignores to the leg animation.
        if (d.spiderWalkSpeed > 0.0F) {
            state.walkAnimationPos += d.spiderWalkPos;
            state.walkAnimationSpeed = Math.max(state.walkAnimationSpeed, d.spiderWalkSpeed);
        }
    }

    /** Spider on a wall: the tilt is applied in world axes at the entity origin, before the body yaw. */
    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("HEAD"), remap = false)
    private void amm$spiderTilt(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
        AmmPoseData d = ((AmmStateAccess) state).amm$data();
        if (d.spiderProgress > 0.001F) {
            SpiderTilt.apply(poseStack, d, bodyRot, state.boundingBoxWidth, state.boundingBoxHeight);
        }
    }

    /** Crawling zombie: lay the body down once per render (fold forward around the hips, in the entity's own yaw frame). */
    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("TAIL"), remap = false)
    private void amm$fold(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
        AmmPoseData d = ((AmmStateAccess) state).amm$data();
        if (!d.woundedCrawl || state.isBaby) return;
        poseStack.translate(0.0F, 0.0F, CENTER_SHIFT);
        poseStack.translate(0.0F, -GROUND_DROP, 0.0F);
        poseStack.translate(0.0F, HIP_PIVOT, 0.0F);
        poseStack.rotate(Axis.XP.rotationDegrees(-AmmPoses.FOLD_DEGREES));
        poseStack.translate(0.0F, -HIP_PIVOT, 0.0F);
    }
}
