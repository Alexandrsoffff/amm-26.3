package com.alexandrso.amm.client;


import com.alexandrso.amm.AlexMovementsMod;
import com.alexandrso.amm.network.SitRequestPayload;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/** G: sit down on the ground. */
@EventBusSubscriber(modid = AlexMovementsMod.MODID, value = Dist.CLIENT)
public final class SitKeyHandler {
    public static final KeyMapping SIT_KEY = new KeyMapping("key.amm.sit", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD, InputConstants.KEY_G, CrawlKeyHandler.CATEGORY);

    private SitKeyHandler() {
    }

    @SubscribeEvent
    static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(SIT_KEY);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        while (SIT_KEY.consumeClick()) {
            if (!player.isPassenger() && player.onGround()) {
                ClientPacketDistributor.sendToServer(new SitRequestPayload());
            }
        }
    }
}
