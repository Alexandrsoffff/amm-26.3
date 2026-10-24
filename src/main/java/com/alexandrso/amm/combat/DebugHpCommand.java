package com.alexandrso.amm.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.alexandrso.amm.AlexMovementsMod;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * /ammdebug toggles a debug view: every mob's name tag shows its current/max HP.
 * The original custom name and visibility are remembered and restored when it is turned off.
 */
@EventBusSubscriber(modid = AlexMovementsMod.MODID)
public final class DebugHpCommand {
    private record Original(Component name, boolean visible) {
    }

    private static final Map<UUID, Original> ORIGINALS = new HashMap<>();
    private static boolean enabled;

    private DebugHpCommand() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("ammdebug").executes(ctx -> {
            enabled = !enabled;
            MinecraftServer server = ctx.getSource().getServer();
            if (!enabled) restoreAll(server);
            boolean now = enabled;
            ctx.getSource().sendSuccess(() -> Component.literal("amm debug HP: " + (now ? "ON" : "OFF")), false);
            return 1;
        }));
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled || event.getServer().getTickCount() % 5 != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof Mob mob && mob.isAlive()) update(mob);
            }
        }
    }

    private static void update(Mob mob) {
        Original orig = ORIGINALS.computeIfAbsent(mob.getUUID(),
                id -> new Original(mob.getCustomName(), mob.isCustomNameVisible()));
        Component base = orig.name() != null ? orig.name() : mob.getType().getDescription();

        float hp = mob.getHealth();
        float max = mob.getMaxHealth();
        ChatFormatting color = hp / max > 0.66F ? ChatFormatting.GREEN
                : hp / max > 0.33F ? ChatFormatting.YELLOW : ChatFormatting.RED;
        MutableComponent label = Component.empty().append(base).append(Component.literal(
                String.format(" %.1f/%.1f", hp, max)).withStyle(color));

        mob.setCustomName(label);
        mob.setCustomNameVisible(true);
    }

    private static void restoreAll(MinecraftServer server) {
        if (ORIGINALS.isEmpty()) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                Original orig = ORIGINALS.get(e.getUUID());
                if (orig == null) continue;
                e.setCustomName(orig.name());
                e.setCustomNameVisible(orig.visible());
            }
        }
        ORIGINALS.clear();
    }

    /** Names are saved with the world, so put them back before the server shuts down. */
    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        if (enabled) restoreAll(event.getServer());
        enabled = false;
    }
}