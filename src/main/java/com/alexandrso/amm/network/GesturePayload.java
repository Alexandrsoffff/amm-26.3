package com.alexandrso.amm.network;

import com.alexandrso.amm.AlexMovementsMod;
import com.alexandrso.amm.movement.AmmAttachments;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: item twirl (Alt + attack) and pointing (Alt alone) on/off. Replaces ItemSpinNetwork + PointingNetwork. */
public record GesturePayload(boolean spin, boolean active) implements CustomPacketPayload {
    public static final Type<GesturePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AlexMovementsMod.MODID, "gesture"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GesturePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, GesturePayload::spin,
            ByteBufCodecs.BOOL, GesturePayload::active,
            GesturePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GesturePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            // Both hands are busy holding a mob: no gestures.
            boolean value = payload.active() && !player.isSpectator();
            var type = payload.spin() ? AmmAttachments.SPINNING.get() : AmmAttachments.POINTING.get();
            if (player.getData(type) != value) player.setData(type, value);
        });
    }
}
