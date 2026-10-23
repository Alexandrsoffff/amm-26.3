package com.alexandrso.amm.client;


import com.alexandrso.amm.AlexMovementsMod;
import com.alexandrso.amm.movement.CrawlHandler;
import com.alexandrso.amm.network.CrawlRequestPayload;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Ctrl + Shift starts crawling; holding Shift afterwards keeps you crawling. */
@EventBusSubscriber(modid = AlexMovementsMod.MODID, value = Dist.CLIENT)
public final class CrawlKeyHandler {
    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(AlexMovementsMod.MODID, "main"));

    public static final KeyMapping CRAWL_KEY = new KeyMapping("key.amm.crawl", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD, InputConstants.KEY_LCONTROL, CATEGORY);

    private CrawlKeyHandler() {
    }

    @SubscribeEvent
    static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(CRAWL_KEY);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gui.screen != null) return;

        boolean sneakDown = mc.options.keyShift.isDown();
        boolean crawlDown = CRAWL_KEY.isDown();

        boolean was = CrawlHandler.isManualCrawling(player);
        boolean now = was;

        if (!was) {
            // Cannot start while gripping a ladder/vine.
            if (sneakDown && crawlDown && !player.onClimbable() && CrawlHandler.canCrawl(player)) now = true;
        } else if (!sneakDown || !CrawlHandler.canCrawl(player)) {
            // Ladders deliberately do NOT end the crawl; climbing is blocked in CrawlHandler instead.
            now = false;
        }

        if (now != was) {
            CrawlHandler.setManualCrawl(player, now); // instant local feedback
            ClientPacketDistributor.sendToServer(new CrawlRequestPayload(now));
        }
    }
}
