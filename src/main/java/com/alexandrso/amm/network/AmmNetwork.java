package com.alexandrso.amm.network;

import com.alexandrso.amm.AlexMovementsMod;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = AlexMovementsMod.MODID)
public final class AmmNetwork {
    private static final String PROTOCOL = "1";

    private AmmNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);
        registrar.playToServer(CrawlRequestPayload.TYPE, CrawlRequestPayload.CODEC, CrawlRequestPayload::handle);
        registrar.playToServer(BlockRequestPayload.TYPE, BlockRequestPayload.CODEC, BlockRequestPayload::handle);
        registrar.playToServer(SwingMissPayload.TYPE, SwingMissPayload.CODEC, SwingMissPayload::handle);
        registrar.playToClient(DualSwingPayload.TYPE, DualSwingPayload.CODEC, DualSwingPayload::handle);
        registrar.playToServer(WallClimbPayload.TYPE, WallClimbPayload.CODEC, WallClimbPayload::handle);
        registrar.playToServer(SitRequestPayload.TYPE, SitRequestPayload.CODEC, SitRequestPayload::handle);
        registrar.playToServer(GesturePayload.TYPE, GesturePayload.CODEC, GesturePayload::handle);
        // Next phases (see PORTING.md): blocking, dual swing, item spin, pointing, carry, sit, wall climb.
    }
}
