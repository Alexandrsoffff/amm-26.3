package com.alexandrso.amm.config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;

import com.alexandrso.amm.AlexMovementsMod;
import com.mojang.logging.LogUtils;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * Runtime view of the blocking config (the old BlockingState lookup helpers).
 * Replaces the {@code instanceof ItemSword / ItemTool} checks of 1.12.2, which no longer exist:
 * an item can block if it is listed in {@code itemChances} or carries one of the vanilla tool
 * item tags (swords, axes, pickaxes, shovels, hoes, spears).
 */
@EventBusSubscriber(modid = AlexMovementsMod.MODID)
public final class BlockingRules {
    private static final Logger LOG = LogUtils.getLogger();

    private static final Map<String, Integer> ITEM_CHANCES = new HashMap<>();
    private static final Set<String> CUSTOM_SHIELDS = new HashSet<>();
    private static final Set<String> BLACKLIST = new HashSet<>();
    private static final Set<String> FULL_DAMAGE_DUAL = new HashSet<>();

    private static final TagKey<Item>[] BLOCKABLE_TAGS = tags("swords", "axes", "pickaxes", "shovels", "hoes", "spears");

    private static final TagKey<Item>[] TOOL_TAGS = tags("pickaxes", "axes", "shovels", "hoes");
    public static final int POLEARM_CHANCE = 95;

    /** Pickaxe/axe/shovel/hoe (not a sword): held with a slight downward tilt in third person. */
    public static boolean isHeldTool(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (TagKey<Item> tag : TOOL_TAGS) {
            if (stack.is(tag)) return true;
        }
        return false;
    }

    public static boolean isPolearm(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(Items.TRIDENT)
                || stack.is(TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace("spears"))));
    }

    private BlockingRules() {
    }

    @SafeVarargs
    private static TagKey<Item>[] tags(String... names) {
        @SuppressWarnings("unchecked")
        TagKey<Item>[] out = new TagKey[names.length];
        for (int i = 0; i < names.length; i++) {
            out[i] = TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace(names[i]));
        }
        return out;
    }

    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent.Loading event) {
        onConfig(event);
    }

    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        onConfig(event);
    }

    private static void onConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() != AmmConfig.SPEC) return;
        ITEM_CHANCES.clear();
        for (String s : AmmConfig.ITEM_CHANCES.get()) {
            int eq = s.indexOf('=');
            if (eq <= 0) continue;
            try {
                ITEM_CHANCES.put(s.substring(0, eq).trim(), Integer.parseInt(s.substring(eq + 1).trim()));
            } catch (NumberFormatException ignored) {
                // rejected by the config validator already
            }
        }
        refill(CUSTOM_SHIELDS, AmmConfig.CUSTOM_SHIELDS.get());
        refill(BLACKLIST, AmmConfig.BLACKLIST.get());
        refill(FULL_DAMAGE_DUAL, AmmConfig.FULL_DAMAGE_DUAL_ITEMS.get());
        LOG.info("[amm] config loaded: itemChances={} riposteItems={} riposteDuration={} ticks",
                ITEM_CHANCES.size(), AmmConfig.RIPOSTE_ITEMS.get().size(), AmmConfig.RIPOSTE_DURATION_TICKS.get());
    }

    private static void refill(Set<String> target, Iterable<? extends String> source) {
        target.clear();
        for (String s : source) target.add(s.trim());
    }

    private static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static boolean isShield(ItemStack stack) {
        return stack.is(Items.SHIELD) || stack.has(DataComponents.BLOCKS_ATTACKS) || CUSTOM_SHIELDS.contains(id(stack));
    }

    public static boolean canBlock(ItemStack stack) {
        if (stack.isEmpty() || isShield(stack)) return false;
        if (isPolearm(stack) && !AmmConfig.POLEARM_BLOCK_ENABLED.get()) return false;
        String id = id(stack);
        if (BLACKLIST.contains(id)) return false;
        if (ITEM_CHANCES.containsKey(id)) return true;
        if (stack.is(Items.TRIDENT)) return true;
        for (TagKey<Item> tag : BLOCKABLE_TAGS) {
            if (stack.is(tag)) return true;
        }
        return false;
    }

    public static int chance(ItemStack stack) {
        Integer own = ITEM_CHANCES.get(id(stack));
        if (own != null) return own;
        return isPolearm(stack) ? AmmConfig.POLEARM_BLOCK_CHANCE.get() : AmmConfig.DEFAULT_CHANCE.get();
    }

    public static boolean keepsFullDamageInDual(ItemStack stack) {
        return FULL_DAMAGE_DUAL.contains(id(stack));
    }

    public static int dualDamagePercent(ItemStack stack) {
        return keepsFullDamageInDual(stack) ? 100 : AmmConfig.DUAL_DAMAGE_PERCENT.get();
    }

    /** Reads the config directly: no dependency on the config-event listener having run. */
    public static boolean isRiposteItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String id = id(stack);
        for (String s : AmmConfig.RIPOSTE_ITEMS.get()) {
            if (s.trim().equals(id)) return true;
        }
        return false;
    }

    /** True when BOTH hands hold a blockable weapon/tool (no shield involved). */
    public static boolean isDualWielding(net.minecraft.world.entity.LivingEntity entity) {
        if (!AmmConfig.DUAL_ENABLED.get() || entity == null) return false;
        ItemStack main = entity.getMainHandItem();
        ItemStack off = entity.getOffhandItem();
        if (isPolearm(main) || isPolearm(off)) return false; // polearms are two-handed
        return canBlock(main) && canBlock(off);
    }

    /** Dual-wield block chance: best weapon + a percent of the weaker one, capped (never lowers a stronger weapon). */
    public static int dualChance(ItemStack main, ItemStack off) {
        int a = chance(main);
        int b = chance(off);
        int best = Math.max(a, b);
        int other = Math.min(a, b);
        int cap = Math.max(AmmConfig.DUAL_MAX_CHANCE.get(), best);
        return Math.min(cap, best + Math.round(other * AmmConfig.DUAL_BLOCK_OTHER_PERCENT.get() / 100.0F));
    }

    public static int projectileChance(int meleeChance) {
        return Math.max(0, Math.min(100, Math.round(meleeChance * AmmConfig.PROJECTILE_BLOCK_PERCENT.get() / 100.0F)));
    }
}