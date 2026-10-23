package com.alexandrso.amm;

import org.slf4j.Logger;

import com.alexandrso.amm.config.AmmConfig;
import com.alexandrso.amm.movement.AmmAttachments;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * Alex Movements Mod - 26.3 / NeoForge port.
 * Event handlers live in their own classes and register themselves through
 * {@code @EventBusSubscriber}; this class only wires registries and config.
 */
@Mod(AlexMovementsMod.MODID)
public class AlexMovementsMod {
    public static final String MODID = "amm";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AlexMovementsMod(IEventBus modBus, ModContainer container) {
        AmmAttachments.ATTACHMENTS.register(modBus);
        com.alexandrso.amm.entity.AmmEntities.ENTITY_TYPES.register(modBus);
        container.registerConfig(ModConfig.Type.LOCAL, AmmConfig.SPEC);
    }
}
