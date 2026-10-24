package com.alexandrso.amm.config;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * One typed config replacing both 1.12.2 files (amm.cfg + amm_blocking.cfg).
 * All modded (Pyrotech) item ids are gone; the lists now contain the vanilla gear of the new
 * versions: copper and netherite tools/swords, spears and the mace.
 */
public final class AmmConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    // ── movement ────────────────────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue CRAWL_ENABLED;
    public static final ModConfigSpec.DoubleValue CRAWL_BREAK_SPEED_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue DODGE_ENABLED;
    public static final ModConfigSpec.BooleanValue LEDGE_CLIMB_ENABLED;
    public static final ModConfigSpec.BooleanValue SPIDER_WALL_TILT_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SPIDER_WALL_TILT_EXCLUDED_MODS;

    // ── blocking ────────────────────────────────────────────────────────────────
    public static final ModConfigSpec.IntValue DEFAULT_CHANCE;
    public static final ModConfigSpec.IntValue BLOCK_START_DELAY_TICKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_CHANCES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CUSTOM_SHIELDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;
    public static final ModConfigSpec.BooleanValue SHOW_BLOCK_CHANCE_TOOLTIP;
    public static final ModConfigSpec.IntValue PROJECTILE_BLOCK_PERCENT;
    public static final ModConfigSpec.BooleanValue HELD_TOOL_TILT;
    public static final ModConfigSpec.BooleanValue POLEARM_BLOCK_ENABLED;
    public static final ModConfigSpec.IntValue POLEARM_BLOCK_CHANCE;
    public static final ModConfigSpec.IntValue SPEAR_BLOCK_PIERCE_POINTS;

    // ── dual style ──────────────────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue DUAL_ENABLED;
    public static final ModConfigSpec.IntValue DUAL_DAMAGE_PERCENT;
    public static final ModConfigSpec.IntValue DUAL_COOLDOWN_PENALTY_PERCENT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FULL_DAMAGE_DUAL_ITEMS;
    public static final ModConfigSpec.BooleanValue DUAL_COMBO_ENABLED;
    public static final ModConfigSpec.IntValue DUAL_COMBO_PAIRS;
    public static final ModConfigSpec.IntValue DUAL_COMBO_SPEED_BONUS_PERCENT;
    public static final ModConfigSpec.IntValue DUAL_COMBO_MIN_CHARGE_PERCENT;
    public static final ModConfigSpec.IntValue DUAL_COMBO_TIMEOUT_TICKS;
    public static final ModConfigSpec.BooleanValue DUAL_COMBO_NOTIFY;
    public static final ModConfigSpec.DoubleValue DUAL_OFFHAND_DELAY_TICKS;
    public static final ModConfigSpec.DoubleValue DUAL_OFFHAND_REACH_LEEWAY;
    public static final ModConfigSpec.DoubleValue DUAL_OFFHAND_AIM_LEEWAY;
    public static final ModConfigSpec.IntValue DUAL_BLOCK_OTHER_PERCENT;
    public static final ModConfigSpec.IntValue DUAL_MAX_CHANCE;

    // ── riposte ─────────────────────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue RIPOSTE_ENABLED;
    public static final ModConfigSpec.IntValue RIPOSTE_DAMAGE_BONUS_PERCENT;
    public static final ModConfigSpec.IntValue RIPOSTE_DURATION_TICKS;
    public static final ModConfigSpec.IntValue RIPOSTE_SHIELD_PIERCE_PERCENT;
    public static final ModConfigSpec.BooleanValue RIPOSTE_NOTIFY;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RIPOSTE_ITEMS;

    // ── mobs ────────────────────────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue MOB_BLOCK_ENABLED;
    public static final ModConfigSpec.IntValue MOB_BLOCK_REACT_CHANCE_PERCENT;
    public static final ModConfigSpec.IntValue MOB_BLOCK_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue MOB_BLOCK_DURATION_TICKS;
    public static final ModConfigSpec.IntValue MOB_SHIELD_DURATION_PERCENT;
    public static final ModConfigSpec.IntValue MOB_SHIELD_TURN_SPEED_DEGREES;
    public static final ModConfigSpec.BooleanValue MOB_SPIN_ENABLED;
    public static final ModConfigSpec.BooleanValue MOB_SIT_ENABLED;
    public static final ModConfigSpec.BooleanValue MOB_DUAL_STRIKE_ENABLED;
    public static final ModConfigSpec.BooleanValue WOUNDED_ZOMBIE_ENABLED;
    public static final ModConfigSpec.IntValue WOUNDED_MERCY_CHANCE_PERCENT;
    public static final ModConfigSpec.DoubleValue WOUNDED_HEALTH;
    public static final ModConfigSpec.DoubleValue WOUNDED_SPEED_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue WOUNDED_ATTACK_MULTIPLIER;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MOB_POSE_ENTITIES;

    // ── quick break ─────────────────────────────────────────────────────────────
    public enum SlownessMode { ITEM_QUANTITY, FLAT_PER_LOADED_BLOCK }

    public static final ModConfigSpec.BooleanValue QB_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> QB_BLOCKS;
    public static final ModConfigSpec.DoubleValue QB_SPEED;
    public static final ModConfigSpec.EnumValue<SlownessMode> QB_SLOWNESS_MODE;
    public static final ModConfigSpec.IntValue QB_FLAT_LEVEL;
    public static final ModConfigSpec.IntValue QB_ITEMS_PER_STEP;
    public static final ModConfigSpec.IntValue QB_LEVEL_PER_ITEM_STEP;
    public static final ModConfigSpec.IntValue QB_BLOCKS_PER_STEP;
    public static final ModConfigSpec.IntValue QB_LEVEL_PER_BLOCK_STEP;
    public static final ModConfigSpec.IntValue QB_NONSTACK_PER_STEP;
    public static final ModConfigSpec.IntValue QB_LEVEL_PER_NONSTACK_STEP;
    public static final ModConfigSpec.IntValue QB_MAX_SLOWNESS;
    public static final ModConfigSpec.IntValue QB_MAX_CARRIED;
    public static final ModConfigSpec.IntValue QB_OVERLOAD_AMPLIFIER;

    static {
        B.push("quickBreak");
        QB_ENABLED = B.comment("Sneak + empty main hand: listed blocks break instantly into an item that KEEPS its contents (like creative pick-block).")
                .define("enableQuickBreak", true);
        QB_BLOCKS = B.comment("Blocks that quick-break. '*' wildcards match any part of the id.")
                .defineListAllowEmpty("quickBreakBlocks", List.of(
                        "minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", "minecraft:hopper",
                        "minecraft:dropper", "minecraft:dispenser", "minecraft:crafter", "minecraft:jukebox",
                        "minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker",
                        "minecraft:crafting_table", "minecraft:brewing_stand", "minecraft:chiseled_bookshelf",
                        "minecraft:decorated_pot", "minecraft:copper_chest", "minecraft:exposed_copper_chest",
                        "minecraft:weathered_copper_chest", "minecraft:oxidized_copper_chest"),
                        () -> "", AmmConfig::isString);
        QB_SPEED = B.comment("Break speed applied to listed blocks while sneaking with an empty hand.")
                .defineInRange("quickBreakSpeed", 200.0D, 1.0D, 100000.0D);
        QB_SLOWNESS_MODE = B.comment("ITEM_QUANTITY: slowness grows with how much is stored in every carried block (batches below).",
                "FLAT_PER_LOADED_BLOCK: every carried block with ANY content adds a fixed slowness, full or not.")
                .defineEnum("slownessMode", SlownessMode.FLAT_PER_LOADED_BLOCK);
        QB_FLAT_LEVEL = B.comment("FLAT mode: slowness levels each loaded block adds.").defineInRange("flatSlownessLevelPerLoadedBlock", 3, 0, 10);
        QB_ITEMS_PER_STEP = B.comment("ITEM mode: stored stackable items per batch.").defineInRange("itemsPerSlownessStep", 256, 1, 1000);
        QB_LEVEL_PER_ITEM_STEP = B.comment("ITEM mode: slowness levels per item batch.").defineInRange("slownessLevelPerItemStep", 1, 0, 10);
        QB_BLOCKS_PER_STEP = B.comment("ITEM mode: stored block items per batch.").defineInRange("blocksPerSlownessStep", 256, 1, 1000);
        QB_LEVEL_PER_BLOCK_STEP = B.comment("ITEM mode: slowness levels per block batch.").defineInRange("slownessLevelPerBlockStep", 1, 0, 10);
        QB_NONSTACK_PER_STEP = B.comment("ITEM mode: stored non-stackable items (tools, armor, weapons) per batch.").defineInRange("nonStackablesPerSlownessStep", 2, 1, 1000);
        QB_LEVEL_PER_NONSTACK_STEP = B.comment("ITEM mode: slowness levels per non-stackable batch.").defineInRange("slownessLevelPerNonStackableStep", 1, 0, 10);
        QB_MAX_SLOWNESS = B.comment("Caps how high normal slowness can stack.").defineInRange("maxSlownessLevel", 255, 1, 255);
        QB_MAX_CARRIED = B.comment("Loaded blocks a player can carry before being overloaded and immobilized.").defineInRange("maxCarriedBlocks", 36, 1, 36);
        QB_OVERLOAD_AMPLIFIER = B.comment("Slowness amplifier when overloaded (255 freezes movement).").defineInRange("overloadSlownessAmplifier", 255, 1, 255);
        B.pop();

        B.push("movement");
        CRAWL_ENABLED = B.comment("Manual crawling (Ctrl + Shift). Vanilla 1-block-gap crawling and swimming are always on.")
                .define("crawlEnabled", true);
        CRAWL_BREAK_SPEED_MULTIPLIER = B.comment("Block breaking speed multiplier while crawling on land (0.35 = 65% slower).")
                .defineInRange("crawlBreakSpeedMultiplier", 0.35D, 0.05D, 1.0D);
        DODGE_ENABLED = B.comment("Dodge roll (hold A/S/D, tap Space).").define("dodgeEnabled", true);
        LEDGE_CLIMB_ENABLED = B.comment("Climb up over ledges while sprinting into them.").define("ledgeClimbEnabled", true);
        SPIDER_WALL_TILT_ENABLED = B.comment("Spiders on a wall lie belly-to-wall (the hitbox is reshaped to match).")
                .define("spiderWallTiltEnabled", true);
        SPIDER_WALL_TILT_EXCLUDED_MODS = B.comment("Spiders from these mod ids are left alone.")
                .defineListAllowEmpty("spiderWallTiltExcludedMods", List.of("spiderstpo"), () -> "", AmmConfig::isString);
        B.pop();

        B.push("blocking");
        DEFAULT_CHANCE = B.comment("Default block chance (0-100) of a blockable item that is not in itemChances.")
                .defineInRange("defaultChance", 60, 0, 100);
        BLOCK_START_DELAY_TICKS = B.comment("Ticks the use key must be held before the guard starts (20 ticks = 1 second).")
                .defineInRange("blockStartDelayTicks", 5, 1, 40);
        ITEM_CHANCES = B.comment(
                "Per-item block chance, format modid:item=chance. Any item listed here can block.",
                "Swords/axes/pickaxes/shovels/hoes/spears (item tags) can block anyway with defaultChance.",
                "Unknown ids (item does not exist in this version) are simply ignored.")
                .defineListAllowEmpty("itemChances", List.of(
                        // swords - best guard
                        "minecraft:wooden_sword=80", "minecraft:stone_sword=80", "minecraft:copper_sword=80",
                        "minecraft:iron_sword=80", "minecraft:golden_sword=80", "minecraft:diamond_sword=80",
                        "minecraft:netherite_sword=80",
                        // axes - heavy, take the role of the old hammers
                        "minecraft:wooden_axe=50", "minecraft:stone_axe=50", "minecraft:copper_axe=50",
                        "minecraft:iron_axe=50", "minecraft:golden_axe=50", "minecraft:diamond_axe=50",
                        "minecraft:netherite_axe=50",
                        // mace - heavy
                        "minecraft:mace=50"),
                        () -> "", AmmConfig::isChanceEntry);
        POLEARM_BLOCK_ENABLED = B.comment("Spears and the trident can block while sneaking (Shift + use key). Off = they never block.")
                .define("polearmBlockEnabled", true);
        POLEARM_BLOCK_CHANCE = B.comment("Block chance (0-100) of spears and the trident (overridden by itemChances).")
                .defineInRange("polearmBlockChance", 95, 0, 100);
        SPEAR_BLOCK_PIERCE_POINTS = B.comment("A spear/trident hit lowers the target's weapon block chance by this many points (80 -> 50 with 30).")
                .defineInRange("spearBlockPiercePoints", 30, 0, 100);
        CUSTOM_SHIELDS = B.comment("Extra shield items besides minecraft:shield, format modid:item.")
                .defineListAllowEmpty("customShields", List.of(), () -> "", AmmConfig::isString);
        BLACKLIST = B.comment("Items removed from blocking AND dual style, format modid:item (e.g. minecraft:iron_axe).")
                .defineListAllowEmpty("blacklist", List.of(), () -> "", AmmConfig::isString);
        SHOW_BLOCK_CHANCE_TOOLTIP = B.comment("Show the block chance in the tooltip of items that can block.")
                .define("showBlockChanceTooltip", true);
        PROJECTILE_BLOCK_PERCENT = B.comment("DUAL STYLE ONLY: projectile block chance = dual block chance * this percent / 100.")
                .defineInRange("projectileBlockPercent", 38, 0, 100);
        HELD_TOOL_TILT = B.comment("Held tools tilt slightly downward in third person.").define("heldToolTilt", true);
        B.pop();

        B.push("dual");
        DUAL_ENABLED = B.comment("A blockable item in BOTH hands gives a delayed off-hand strike and extra block chance.")
                .define("dualEnabled", true);
        DUAL_DAMAGE_PERCENT = B.comment("Percent of damage EVERY strike deals while dual-wielding.")
                .defineInRange("dualDamagePercent", 70, 1, 100);
        DUAL_COOLDOWN_PENALTY_PERCENT = B.comment("Extra charge time while dual-wielding, in percent on top of the slowest weapon's own.")
                .defineInRange("dualCooldownPenaltyPercent", 37, 0, 300);
        FULL_DAMAGE_DUAL_ITEMS = B.comment("Weapons that keep 100% damage in dual style (the light ones), format modid:item.")
                .defineListAllowEmpty("fullDamageDualItems", List.of(),
                        () -> "", AmmConfig::isString);
        DUAL_COMBO_ENABLED = B.comment("Hit with both hands several times in a row without missing and your attacks get faster.")
                .define("dualComboEnabled", true);
        DUAL_COMBO_PAIRS = B.comment("Consecutive double strikes needed to start the combo (3 = 6 strikes).")
                .defineInRange("dualComboPairs", 3, 1, 20);
        DUAL_COMBO_SPEED_BONUS_PERCENT = B.comment("Combo attack speed bonus in percent (the dual penalty is removed first).")
                .defineInRange("dualComboSpeedBonusPercent", 25, 0, 300);
        DUAL_COMBO_MIN_CHARGE_PERCENT = B.comment("Minimum attack charge (percent) for a hit to count for the combo.")
                .defineInRange("dualComboMinChargePercent", 80, 0, 100);
        DUAL_COMBO_TIMEOUT_TICKS = B.comment("The combo is lost if no new dual strike lands within this many ticks.")
                .defineInRange("dualComboTimeoutTicks", 140, 5, 1200);
        DUAL_COMBO_NOTIFY = B.comment("Action-bar message and sound when the combo starts/ends.")
                .define("dualComboNotify", true);
        DUAL_OFFHAND_DELAY_TICKS = B.comment("Delay in ticks between the main-hand and the off-hand strike.")
                .defineInRange("dualOffhandDelayTicks", 7.0D, 0.0D, 20.0D);
        DUAL_OFFHAND_REACH_LEEWAY = B.comment("Extra blocks of reach tolerance for the off-hand strike.")
                .defineInRange("dualOffhandReachLeeway", 0.6D, 0.0D, 5.0D);
        DUAL_OFFHAND_AIM_LEEWAY = B.comment("How much the target hitbox is widened for the off-hand aim check.")
                .defineInRange("dualOffhandAimLeeway", 0.35D, 0.0D, 2.0D);
        DUAL_BLOCK_OTHER_PERCENT = B.comment("Dual block chance = best weapon chance + this percent of the other weapon's chance.")
                .defineInRange("dualBlockOtherPercent", 35, 0, 100);
        DUAL_MAX_CHANCE = B.comment("Maximum block chance (percent) in dual style.")
                .defineInRange("dualMaxChance", 85, 0, 100);
        B.pop();

        B.push("riposte");
        RIPOSTE_ENABLED = B.comment("After a successful block a sword held ALONE deals extra damage with its NEXT hit.")
                .define("riposteEnabled", true);
        RIPOSTE_DAMAGE_BONUS_PERCENT = B.comment("Extra damage (percent) of the riposte hit.")
                .defineInRange("riposteDamageBonusPercent", 30, 0, 500);
        RIPOSTE_DURATION_TICKS = B.comment("How long after a block the riposte hit is possible, in ticks.")
                .defineInRange("riposteDurationTicks", 30, 1, 600);
        RIPOSTE_SHIELD_PIERCE_PERCENT = B.comment("Chance (percent) that a fully charged riposte disables the target's shield (axes always do).")
                .defineInRange("riposteShieldPiercePercent", 25, 0, 100);
        RIPOSTE_NOTIFY = B.comment("Action-bar message and sound when a block opens the riposte window.")
                .define("riposteNotify", true);
        RIPOSTE_ITEMS = B.comment("Weapons that get the riposte, format modid:item.")
                .defineListAllowEmpty("riposteItems", List.of(
                                "minecraft:wooden_sword", "minecraft:stone_sword", "minecraft:copper_sword",
                                "minecraft:iron_sword", "minecraft:golden_sword", "minecraft:diamond_sword",
                                "minecraft:netherite_sword",
                                "minecraft:wooden_spear", "minecraft:stone_spear", "minecraft:copper_spear",
                                "minecraft:iron_spear", "minecraft:golden_spear", "minecraft:diamond_spear",
                                "minecraft:netherite_spear"),
                        () -> "", AmmConfig::isString);
        B.pop();

        B.push("mobs");
        MOB_BLOCK_ENABLED = B.comment("Mobs holding a blockable weapon raise a guard against their target's attacks.")
                .define("mobBlockEnabled", true);
        MOB_BLOCK_REACT_CHANCE_PERCENT = B.comment("Chance per check that a mob raises its guard.")
                .defineInRange("mobBlockReactChancePercent", 70, 0, 100);
        MOB_BLOCK_COOLDOWN_TICKS = B.comment("Ticks a mob waits after lowering its guard.")
                .defineInRange("mobBlockCooldownTicks", 30, 10, 600);
        MOB_BLOCK_DURATION_TICKS = B.comment("Average time a mob keeps its guard raised, in ticks.")
                .defineInRange("mobBlockDurationTicks", 30, 5, 200);
        MOB_SHIELD_DURATION_PERCENT = B.comment("How long a mob holds a raised SHIELD compared to mobBlockDurationTicks (percent).")
                .defineInRange("mobShieldDurationPercent", 250, 100, 1000);
        MOB_SHIELD_TURN_SPEED_DEGREES = B.comment("Degrees per tick a mob turns toward its target while holding a shield.")
                .defineInRange("mobShieldTurnSpeedDegrees", 6, 1, 90);
        MOB_SPIN_ENABLED = B.comment("Idle 'smart' mobs twirl their weapon now and then (never zombies).")
                .define("mobSpinEnabled", true);
        MOB_SIT_ENABLED = B.comment("Idle skeletons, illagers, villagers, witches sometimes sit down (never zombies).")
                .define("mobSitEnabled", true);
        MOB_DUAL_STRIKE_ENABLED = B.comment("Mobs holding a blockable weapon in BOTH hands follow every main-hand attack with a delayed off-hand strike (same rules as the player's dual style).")
                .define("mobDualStrikeEnabled", true);
        WOUNDED_ZOMBIE_ENABLED = B.comment("Mercy save: a lethal hit on an adult zombie sometimes leaves it crawling at low health instead (once per zombie, ever).")
                .define("woundedZombieEnabled", true);
        WOUNDED_MERCY_CHANCE_PERCENT = B.comment("Chance (percent) of the mercy save.").defineInRange("woundedMercyChancePercent", 65, 0, 100);
        WOUNDED_HEALTH = B.comment("Health a wounded zombie is left with.").defineInRange("woundedHealth", 6.5D, 1.0D, 100.0D);
        WOUNDED_SPEED_MULTIPLIER = B.comment("Movement speed of a crawling zombie compared to normal.").defineInRange("woundedSpeedMultiplier", 0.42D, 0.05D, 1.0D);
        WOUNDED_ATTACK_MULTIPLIER = B.comment("Damage of a crawling zombie compared to normal.").defineInRange("woundedAttackMultiplier", 0.3D, 0.0D, 1.0D);
        MOB_POSE_ENTITIES = B.comment("Modded humanoid mobs that should show the guard/twirl poses, format modid:entity.")
                .defineListAllowEmpty("mobPoseEntities", List.of(), () -> "", AmmConfig::isString);
        B.pop();
    }

    public static final ModConfigSpec SPEC = B.build();

    private static boolean isString(Object o) {
        return o instanceof String s && !s.isBlank();
    }

    /** modid:item=chance with chance 0..100 */
    private static boolean isChanceEntry(Object o) {
        if (!(o instanceof String s)) return false;
        int eq = s.indexOf('=');
        if (eq <= 0) return false;
        try {
            int c = Integer.parseInt(s.substring(eq + 1).trim());
            return c >= 0 && c <= 100;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private AmmConfig() {
    }
}
