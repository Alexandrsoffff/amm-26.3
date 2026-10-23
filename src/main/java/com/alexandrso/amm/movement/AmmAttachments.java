package com.alexandrso.amm.movement;

import java.util.function.Supplier;

import com.alexandrso.amm.AlexMovementsMod;

import com.mojang.serialization.Codec;

import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Replaces the hand-written *State sets + *Network sync packets of the 1.12.2 mod
 * (ManualCrawlState, SwimState, FallState ...). A synced data attachment is stored on the
 * player, saved/loaded correctly and automatically sent to every client tracking that player.
 */
public final class AmmAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AlexMovementsMod.MODID);

    /** True while the player is crawling on purpose (Ctrl + Shift). Server-authoritative, synced to trackers. */
    public static final Supplier<AttachmentType<Boolean>> MANUAL_CRAWL = ATTACHMENTS.register("manual_crawl",
            () -> AttachmentType.builder(() -> false).sync(ByteBufCodecs.BOOL).build());

    /** True while the entity holds a guard (RMB with a weapon). Server-authoritative, synced, not saved. */
    public static final Supplier<AttachmentType<Boolean>> BLOCKING = ATTACHMENTS.register("blocking",
            () -> AttachmentType.builder(() -> false).sync(ByteBufCodecs.BOOL).build());

    /** True while the player grips a ledge and pulls themselves up. Cosmetic state, synced to trackers. */
    public static final Supplier<AttachmentType<Boolean>> WALL_CLIMBING = ATTACHMENTS.register("wall_climbing",
            () -> AttachmentType.builder(() -> false).sync(ByteBufCodecs.BOOL).build());

    /** True while the entity (player or mob) twirls its weapon. Synced to trackers. */
    public static final Supplier<AttachmentType<Boolean>> SPINNING = ATTACHMENTS.register("spinning",
            () -> AttachmentType.builder(() -> false).sync(ByteBufCodecs.BOOL).build());

    /** True while a wounded zombie lies on the ground crawling (zombie phase). Synced to trackers. */
    public static final Supplier<AttachmentType<Boolean>> WOUNDED_CRAWL = ATTACHMENTS.register("wounded_crawl",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL.fieldOf("value")).sync(ByteBufCodecs.BOOL).build());

    /** The zombie already used its one mercy save. Saved with the entity (the old amm_used_mercy NBT flag). */
    public static final Supplier<AttachmentType<Boolean>> USED_MERCY = ATTACHMENTS.register("used_mercy",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL.fieldOf("value")).build());

    /** True while the player points forward with the raised main hand (Alt). Synced to trackers. */
    public static final Supplier<AttachmentType<Boolean>> POINTING = ATTACHMENTS.register("pointing",
            () -> AttachmentType.builder(() -> false).sync(ByteBufCodecs.BOOL).build());

    private AmmAttachments() {
    }
}
