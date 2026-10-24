package com.alexandrso.amm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.alexandrso.amm.client.render.AmmPoses;
import com.alexandrso.amm.client.render.AmmStateAccess;

import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;

/**
 * Villager, baby villager and wandering trader model: vanilla has no riding pose for it, so a seated villager keeps its legs straight.
 * Runs after vanilla posed the model and bends the legs forward when the entity sits.
 * The target is matched by name (and skipped when absent) because the model's package differs between game versions.
 */
@Pseudo
@Mixin(targets = {
        "net.minecraft.client.model.npc.VillagerModel",
        "net.minecraft.client.model.npc.BabyVillagerModel",
        "net.minecraft.client.model.VillagerModel"
}, remap = false)
public abstract class VillagerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/VillagerRenderState;)V",
            at = @At("TAIL"), remap = false, require = 0)
    private void amm$sit(VillagerRenderState state, CallbackInfo ci) {
        if (state instanceof AmmStateAccess access) {
            AmmPoses.applySitting(((Model) (Object) this).root(), access.amm$data(), "villager model");
        }
    }
}
