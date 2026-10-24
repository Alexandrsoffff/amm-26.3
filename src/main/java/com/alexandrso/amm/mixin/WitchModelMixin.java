package com.alexandrso.amm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.alexandrso.amm.client.render.AmmPoses;
import com.alexandrso.amm.client.render.AmmStateAccess;

import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.WitchRenderState;

/**
 * Witch model: like the villager it has no riding pose in vanilla, so the legs are bent forward here when the witch sits.
 * The target is matched by name (and skipped when absent) because the model's package differs between game versions.
 */
@Pseudo
@Mixin(targets = {
        "net.minecraft.client.model.monster.witch.WitchModel",
        "net.minecraft.client.model.npc.WitchModel",
        "net.minecraft.client.model.WitchModel"
}, remap = false)
public abstract class WitchModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/WitchRenderState;)V",
            at = @At("TAIL"), remap = false, require = 0)
    private void amm$sit(WitchRenderState state, CallbackInfo ci) {
        if (state instanceof AmmStateAccess access) {
            AmmPoses.applySitting(((Model) (Object) this).root(), access.amm$data(), "witch model");
        }
    }
}
