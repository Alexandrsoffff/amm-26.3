package com.alexandrso.amm.client;

import com.alexandrso.amm.AlexMovementsMod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client entry point: gives the mod an in-game config screen (Mods > Alex Movements Mod > Config). */
@Mod(value = AlexMovementsMod.MODID, dist = Dist.CLIENT)
public class AlexMovementsModClient {
    public AlexMovementsModClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
