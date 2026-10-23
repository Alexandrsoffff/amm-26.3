package com.alexandrso.amm.network;

import com.alexandrso.amm.AlexMovementsMod;
import com.alexandrso.amm.combat.BlockingHandler;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: guard up / down. Replaces BlockingNetwork.PacketBlocking (+ PacketSyncBlocking, now an attachment). */
public record BlockRequestPayload(boolean active) implements CustomPacketPayload {
    public static final Type<BlockRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AlexMovementsMod.MODID, "block_request"));
    public static final StreamCodec<ByteBuf, BlockRequestPayload> CODEC =
            ByteBufCodecs.BOOL.map(BlockRequestPayload::new, BlockRequestPayload::active);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BlockRequestPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            // Using something (eating, drinking, drawing a bow...) - the guard must not start.
            BlockingHandler.setBlocking(player, payload.active() && BlockingHandler.canBlockNow(player));
        });
    }
}
