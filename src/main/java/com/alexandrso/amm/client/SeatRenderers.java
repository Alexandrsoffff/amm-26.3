package com.alexandrso.amm.client;

import com.alexandrso.amm.AlexMovementsMod;
import com.alexandrso.amm.entity.AmmEntities;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = AlexMovementsMod.MODID, value = Dist.CLIENT)
public final class SeatRenderers {
    private SeatRenderers() {
    }

    @SubscribeEvent
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AmmEntities.SITTING_SEAT.get(), NoopRenderer::new);
    }
}
