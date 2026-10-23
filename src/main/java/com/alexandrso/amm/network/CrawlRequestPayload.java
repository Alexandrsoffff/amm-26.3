package com.alexandrso.amm.network;

import com.alexandrso.amm.AlexMovementsMod;
import com.alexandrso.amm.movement.CrawlHandler;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: "I want to start/stop crawling". Replaces ManualCrawlNetwork.PacketManualCrawl. */
public record CrawlRequestPayload(boolean active) implements CustomPacketPayload {
    public static final Type<CrawlRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AlexMovementsMod.MODID, "crawl_request"));
    public static final StreamCodec<ByteBuf, CrawlRequestPayload> CODEC =
            ByteBufCodecs.BOOL.map(CrawlRequestPayload::new, CrawlRequestPayload::active);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CrawlRequestPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            // The server never trusts the client: it re-checks every condition itself.
            CrawlHandler.setManualCrawl(player, payload.active() && CrawlHandler.canCrawl(player));
        });
    }
}
