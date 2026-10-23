package com.alexandrso.amm.movement;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.alexandrso.amm.AlexMovementsMod;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Sitting on a cushion: after 15 s Regeneration is kept up until the player stands up. */
@EventBusSubscriber(modid = AlexMovementsMod.MODID)
public final class CushionRegenHandler {
    private static final Identifier CUSHION = Identifier.withDefaultNamespace("cushion");
    private static final int START_TICKS = 300;  // 15 s
    private static final int EFFECT_TICKS = 140;  // short, refreshed while sitting

    private static final Map<UUID, Integer> SITTING = new HashMap<>();

    private CushionRegenHandler() {
    }

    private static boolean isCushion(Entity vehicle) {
        return vehicle != null && CUSHION.equals(BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()));
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        UUID id = player.getUUID();

        if (!isCushion(player.getVehicle())) {
            Integer ticks = SITTING.remove(id);
            if (ticks != null && ticks >= START_TICKS) {
                MobEffectInstance e = player.getEffect(MobEffects.REGENERATION);
                // remove only our short refreshed effect, not a potion
                if (e != null && e.isAmbient() && e.getDuration() <= EFFECT_TICKS) {
                    player.removeEffect(MobEffects.REGENERATION);
                }
            }
            return;
        }

        int ticks = SITTING.merge(id, 1, Integer::sum);
        if (ticks >= START_TICKS) {
            MobEffectInstance e = player.getEffect(MobEffects.REGENERATION);
            if (e == null || e.getDuration() <= 20) {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, EFFECT_TICKS, 0, true, true));
            }
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SITTING.remove(event.getEntity().getUUID());
    }
}