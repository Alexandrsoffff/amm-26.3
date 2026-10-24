package com.alexandrso.amm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.alexandrso.amm.client.render.AmmPoses;
import com.alexandrso.amm.client.render.AmmStateAccess;

import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;

/**
 * Illager model (vindicator, pillager, evoker, illusioner) is not a HumanoidModel, so the humanoid hook never reaches it.
 * Runs after vanilla posed the model: while the illager guards or twirls its weapon, the crossed-arms part is
 * swapped for the two separate arms and they get the guard / twirl pose.
 * The target is matched by name (and skipped when absent) because the model's package differs between game versions.
 */
@Pseudo
@Mixin(targets = {
        "net.minecraft.client.model.monster.illager.IllagerModel",
        "net.minecraft.client.model.IllagerModel"
}, remap = false)
public abstract class IllagerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/IllagerRenderState;)V",
            at = @At("TAIL"), remap = false, require = 0)
    private void amm$poses(IllagerRenderState state, CallbackInfo ci) {
        if (state instanceof AmmStateAccess access) {
            AmmPoses.applyIllager(((Model) (Object) this).root(), access.amm$data(), state);
        }
    }
}
