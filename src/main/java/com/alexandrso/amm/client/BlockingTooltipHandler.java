package com.alexandrso.amm.client;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import com.alexandrso.amm.AlexMovementsMod;
import com.alexandrso.amm.config.AmmConfig;
import com.alexandrso.amm.config.BlockingRules;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Block chance (and riposte / dual chances) in the tooltip of every item that can block. */
@EventBusSubscriber(modid = AlexMovementsMod.MODID, value = Dist.CLIENT)
public final class BlockingTooltipHandler {
    private static final DecimalFormat SECONDS = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private BlockingTooltipHandler() {
    }

    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        if (!AmmConfig.SHOW_BLOCK_CHANCE_TOOLTIP.get()) return;
        ItemStack stack = event.getItemStack();
        if (!BlockingRules.canBlock(stack)) return;

        var tip = event.getToolTip();
        tip.add(Component.translatable("amm.tooltip.block_chance", BlockingRules.chance(stack)).withStyle(ChatFormatting.BLUE));

        if (AmmConfig.RIPOSTE_ENABLED.get() && BlockingRules.isRiposteItem(stack)) {
            tip.add(Component.translatable("amm.tooltip.riposte", AmmConfig.RIPOSTE_DAMAGE_BONUS_PERCENT.get(),
                    SECONDS.format(AmmConfig.RIPOSTE_DURATION_TICKS.get() / 20.0D)).withStyle(ChatFormatting.GOLD));
        }

        // Dual chances: only for a stack that is actually held while the other hand holds a blockable weapon too.
        Player player = event.getEntity();
        if (player == null || !BlockingRules.isDualWielding(player)) return;
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (stack != main && stack != off) return;

        int dual = BlockingRules.dualChance(main, off);
        tip.add(Component.translatable("amm.tooltip.block_chance_dual", dual).withStyle(ChatFormatting.BLUE));
        tip.add(Component.translatable("amm.tooltip.block_chance_projectile", BlockingRules.projectileChance(dual)).withStyle(ChatFormatting.BLUE));
    }
}
