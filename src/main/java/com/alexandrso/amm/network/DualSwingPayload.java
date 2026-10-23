package com.alexandrso.amm.network;

import com.alexandrso.amm.AlexMovementsMod;
import com.alexandrso.amm.client.DualClientState;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> clients: the off-hand swing of {@code entityId} starts in {@code delay} ticks (delay < 0 cancels it). */
public record DualSwingPayload(int entityId, float delay) implements CustomPacketPayload {
    public static final Type<DualSwingPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AlexMovementsMod.MODID, "dual_swing"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DualSwingPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DualSwingPayload::entityId,
            ByteBufCodecs.FLOAT, DualSwingPayload::delay,
            DualSwingPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DualSwingPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (payload.delay() < 0.0F) {
                DualClientState.cancel(payload.entityId());
                return;
            }
            Entity e = ctx.player().level().getEntity(payload.entityId());
            if (e != null) DualClientState.start(payload.entityId(), e.tickCount + payload.delay());
        });
    }
}
