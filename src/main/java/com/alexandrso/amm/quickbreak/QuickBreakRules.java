package com.alexandrso.amm.quickbreak;

import com.alexandrso.amm.config.AmmConfig;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;

/**
 * Which blocks quick-break and which carried stacks count as "loaded". Stored content is read from
 * the item's container component (chests, barrels, furnaces, hoppers... all keep it there since
 * 1.20.5), so the 100 lines of hand-written NBT list scanning are gone.
 */
public final class QuickBreakRules {
    private QuickBreakRules() {
    }

    public static boolean isQuickBreakBlock(Block block) {
        String name = BuiltInRegistries.BLOCK.getKey(block).toString();
        for (String s : AmmConfig.QB_BLOCKS.get()) {
            if (s.equals(name)) return true;
            if (s.contains("*") && name.contains(s.replace("*", ""))) return true;
        }
        return false;
    }

    public static boolean isLoaded(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return false;
        if (!isQuickBreakBlock(blockItem.getBlock())) return false;
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) return false;
        for (var ignored : contents.nonEmptyItems()) return true;
        return false;
    }

    public static int countLoaded(Inventory inv) {
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isLoaded(inv.getItem(i))) count++;
        }
        return count;
    }

    /** {stackable items, blocks, non-stackables} stored inside one loaded stack. */
    public static int[] storedCounts(ItemStack stack) {
        int[] counts = new int[3];
        if (!isLoaded(stack)) return counts;
        for (var template : stack.get(DataComponents.CONTAINER).nonEmptyItems()) {
            ItemStack inner = template.create();
            if (inner.getMaxStackSize() == 1) counts[2] += inner.getCount();
            else if (inner.getItem() instanceof BlockItem) counts[1] += inner.getCount();
            else counts[0] += inner.getCount();
        }
        return counts;
    }
}
