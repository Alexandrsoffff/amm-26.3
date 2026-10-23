package com.alexandrso.amm.network;

import com.alexandrso.amm.AlexMovementsMod;
import com.alexandrso.amm.config.AmmConfig;
import com.alexandrso.amm.movement.AmmAttachments;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: "I grip / let go of a ledge". Replaces WallClimbNetwork (the relay to other clients is now the synced attachment). */
public record WallClimbPayload(boolean active) implements CustomPacketPayload {
    public static final Type<WallClimbPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AlexMovementsMod.MODID, "wall_climb"));
    public static final StreamCodec<ByteBuf, WallClimbPayload> CODEC =
            ByteBufCodecs.BOOL.map(WallClimbPayload::new, WallClimbPayload::active);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WallClimbPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            boolean value = payload.active() && AmmConfig.LEDGE_CLIMB_ENABLED.get() && !player.isSpectator();
            if (player.getData(AmmAttachments.WALL_CLIMBING.get()) != value) {
                player.setData(AmmAttachments.WALL_CLIMBING.get(), value);
            }
        });
    }
}
