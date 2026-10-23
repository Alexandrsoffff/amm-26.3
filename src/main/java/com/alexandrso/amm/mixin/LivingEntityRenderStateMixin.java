package com.alexandrso.amm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.alexandrso.amm.client.render.AmmPoseData;
import com.alexandrso.amm.client.render.AmmStateAccess;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Every living render state carries our pose data (filled in LivingEntityRendererMixin). */
@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements AmmStateAccess {
    @Unique
    private final AmmPoseData amm$data = new AmmPoseData();

    @Override
    public AmmPoseData amm$data() {
        return amm$data;
    }
}
