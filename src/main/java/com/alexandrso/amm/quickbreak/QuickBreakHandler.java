package com.alexandrso.amm.quickbreak;

import com.alexandrso.amm.AlexMovementsMod;
import com.alexandrso.amm.config.AmmConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.BlockItem;

/**
 * Vanilla-feeling "pick up the whole block": with the main hand empty, sneaking while mining a
 * listed block breaks it almost instantly into an item that keeps its contents.
 * <p>
 * The break is taken over completely (the vanilla break is cancelled, the block entity is removed
 * BEFORE the block so a chest cannot spill its items, and the item comes from vanilla's own
 * pick-block-with-data copy). Placing it back is plain vanilla BlockItem behaviour now, so the
 * old "re-apply NBT one tick later" workaround for badly written modded ItemBlocks is gone, and
 * so is the preserved-block-state machinery (it only existed for the removed Pyrotech torches).
 */
@EventBusSubscriber(modid = AlexMovementsMod.MODID)
public final class QuickBreakHandler {
    private static final int REFRESH_DURATION = 40;

    private QuickBreakHandler() {
    }

    private static boolean isEligible(Player player) {
        return AmmConfig.QB_ENABLED.get() && !player.isCreative() && !player.isSpectator()
                && player.isShiftKeyDown() && player.getMainHandItem().isEmpty();
    }

    @SubscribeEvent
    static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (!isEligible(player) || !QuickBreakRules.isQuickBreakBlock(event.getState().getBlock())) return;
        event.setNewSpeed(AmmConfig.QB_SPEED.get().floatValue());
    }

    @SubscribeEvent
    static void onBreak(BreakBlockEvent event) {
        if (event.getLevel().isClientSide()) return;
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide() || !isEligible(player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        BlockState state = event.getState();
        Block block = state.getBlock();
        if (!QuickBreakRules.isQuickBreakBlock(block)) return;
        BlockPos pos = event.getPos();

        // Vanilla's own creative "pick block with data" copy: block entity data + components (container contents...).
        ItemStack stack = state.getCloneItemStack(level, pos, true);
        if (stack.isEmpty()) return;
        // Two loaded blocks with identical contents must never merge into one stack (the slowness counts stacks).
        if (QuickBreakRules.isLoaded(stack)) stack.set(DataComponents.MAX_STACK_SIZE, 1);

        BlockEntity be = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        if (be != null) {
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            be.saveCustomOnly(out);
            be.removeComponentsFromTag(out);
            BlockItem.setBlockEntityData(stack, be.getType(), out);
            stack.applyComponents(be.collectComponents()); // тут лежить CONTAINER
        }

        if (QuickBreakRules.isLoaded(stack)) stack.set(DataComponents.MAX_STACK_SIZE, 1);
        // Take the whole break over: remove the block entity FIRST so the block cannot spill its contents.
        event.setCanceled(true);
        level.removeBlockEntity(pos);
        level.levelEvent(2001, pos, Block.getId(state)); // break particles + sound
        level.removeBlock(pos, false);

        if (!player.getInventory().add(stack)) {
            player.spawnAtLocation(level, stack);
        }
    }

    // ── slowness of carried loaded blocks ───────────────────────────────────────

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !AmmConfig.QB_ENABLED.get()) return;

        Inventory inv = player.getInventory();
        int loaded = QuickBreakRules.countLoaded(inv);
        if (loaded == 0) return;

        int amplifier;
        if (loaded > AmmConfig.QB_MAX_CARRIED.get()) {
            amplifier = AmmConfig.QB_OVERLOAD_AMPLIFIER.get();
            Vec3 v = player.getDeltaMovement(); // locked: no horizontal and no upward movement
            player.setDeltaMovement(0.0D, Math.min(v.y, 0.0D), 0.0D);
        } else {
            int totalLevel;
            if (AmmConfig.QB_SLOWNESS_MODE.get() == AmmConfig.SlownessMode.FLAT_PER_LOADED_BLOCK) {
                totalLevel = loaded * AmmConfig.QB_FLAT_LEVEL.get();
            } else {
                int items = 0, blocks = 0, nonStackables = 0;
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    int[] c = QuickBreakRules.storedCounts(inv.getItem(i));
                    items += c[0];
                    blocks += c[1];
                    nonStackables += c[2];
                }
                totalLevel = (items / AmmConfig.QB_ITEMS_PER_STEP.get()) * AmmConfig.QB_LEVEL_PER_ITEM_STEP.get()
                        + (blocks / AmmConfig.QB_BLOCKS_PER_STEP.get()) * AmmConfig.QB_LEVEL_PER_BLOCK_STEP.get()
                        + (nonStackables / AmmConfig.QB_NONSTACK_PER_STEP.get()) * AmmConfig.QB_LEVEL_PER_NONSTACK_STEP.get();
            }
            if (totalLevel <= 0) return;
            amplifier = Math.max(0, Math.min(totalLevel, AmmConfig.QB_MAX_SLOWNESS.get()) - 1);
        }
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, REFRESH_DURATION, amplifier, true, false));
    }

    /** A loaded player jumps shorter (and an overloaded one not at all). */
    @SubscribeEvent
    static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!AmmConfig.QB_ENABLED.get() || !(event.getEntity() instanceof Player player)) return;
        if (QuickBreakRules.countLoaded(player.getInventory()) == 0) return;

        if (QuickBreakRules.countLoaded(player.getInventory()) > AmmConfig.QB_MAX_CARRIED.get()) {
            player.setDeltaMovement(Vec3.ZERO);
            return;
        }
        MobEffectInstance effect = player.getEffect(MobEffects.SLOWNESS);
        if (effect != null) {
            double multiplier = Math.max(0.0D, 1.0D - (effect.getAmplifier() + 1) * 0.1D);
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x * multiplier, v.y, v.z * multiplier);
        }
    }

    // ── tooltip ─────────────────────────────────────────────────────────────────

    @EventBusSubscriber(modid = AlexMovementsMod.MODID, value = Dist.CLIENT)
    public static final class Tooltip {
        private Tooltip() {
        }

        @SubscribeEvent
        static void onTooltip(ItemTooltipEvent event) {
            if (!AmmConfig.QB_ENABLED.get() || !QuickBreakRules.isLoaded(event.getItemStack())) return;
            event.getToolTip().add(Component.translatable("amm.quickbreak.tooltip.loaded")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }
}
