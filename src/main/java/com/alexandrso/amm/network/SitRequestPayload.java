package com.alexandrso.amm.network;

import com.alexandrso.amm.AlexMovementsMod;
import com.alexandrso.amm.entity.SittingSeat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: sit down on the ground (G). Replaces PacketSit. */
public record SitRequestPayload() implements CustomPacketPayload {
    public static final Type<SitRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AlexMovementsMod.MODID, "sit_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SitRequestPayload> CODEC =
            StreamCodec.unit(new SitRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SitRequestPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> SittingSeat.trySit(ctx.player()));
    }
}
