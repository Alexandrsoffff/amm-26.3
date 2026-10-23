package com.alexandrso.amm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.alexandrso.amm.client.render.AmmPoses;
import com.alexandrso.amm.client.render.AmmStateAccess;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Runs after vanilla posed the humanoid (players, zombies, skeletons, villagers, armor...): our poses go on top. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
            at = @At("TAIL"), remap = false)
    private void amm$poses(HumanoidRenderState state, CallbackInfo ci) {
        if (state instanceof AmmStateAccess access) {
            AmmPoses.apply((HumanoidModel<?>) (Object) this, access.amm$data(), state);
        }
    }
}
