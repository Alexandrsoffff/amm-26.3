package com.alexandrso.amm.network;

import com.alexandrso.amm.AlexMovementsMod;
import com.alexandrso.amm.combat.DualHandler;
import com.alexandrso.amm.combat.RiposteHandler;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: "I swung at thin air" (the server cannot see it). Ends the riposte window (and later the dual combo). */
public record SwingMissPayload() implements CustomPacketPayload {
    public static final Type<SwingMissPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AlexMovementsMod.MODID, "swing_miss"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SwingMissPayload> CODEC =
            StreamCodec.unit(new SwingMissPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SwingMissPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            RiposteHandler.swingMissed(ctx.player());
            DualHandler.comboBreak(ctx.player());
        });
    }
}
