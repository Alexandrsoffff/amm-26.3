package com.alexandrso.amm.client;

import com.alexandrso.amm.AlexMovementsMod;
import com.alexandrso.amm.combat.BlockingHandler;
import com.alexandrso.amm.config.AmmConfig;
import com.alexandrso.amm.config.BlockingRules;
import com.alexandrso.amm.network.BlockRequestPayload;
import com.alexandrso.amm.network.SwingMissPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import com.alexandrso.amm.config.AmmConfig;
/**
 * Client side of the guard: hold RMB with a weapon for blockStartDelayTicks, tell the server,
 * and swallow attack/use clicks while the guard is up. (First-person/third-person guard POSES
 * are part of the render phase and not ported yet.)
 */
@EventBusSubscriber(modid = AlexMovementsMod.MODID, value = Dist.CLIENT)
public final class BlockingClientHandler {
    private static boolean clientIsBlocking;
    private static int useTicks;
    /** Right click was used for something else (eating, bow...) - no guard until the button is released. */
    private static boolean suppressed;

    private BlockingClientHandler() {
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            clientIsBlocking = false;
            useTicks = 0;
            return;
        }

        boolean holdsValid = BlockingHandler.canBlockNow(player);
        boolean rawKeyDown = mc.options.keyUse.isDown() && mc.gui.screen == null;
        if (!rawKeyDown) {
            suppressed = false;
        } else if (player.isUsingItem()) {
            // Eating, drinking, drawing a bow... never start (or keep) a guard until re-pressed.
            suppressed = true;
        }

        boolean keyDown = rawKeyDown && holdsValid && !suppressed;
        useTicks = keyDown ? useTicks + 1 : 0;
        boolean wantsToBlock = useTicks >= AmmConfig.BLOCK_START_DELAY_TICKS.get();

        if (wantsToBlock != clientIsBlocking) {
            clientIsBlocking = wantsToBlock;
            BlockingHandler.setBlocking(player, wantsToBlock); // instant local feedback
            ClientPacketDistributor.sendToServer(new BlockRequestPayload(wantsToBlock));
        }

        if (clientIsBlocking) {
            if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
            mc.options.keyAttack.setDown(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer p = Minecraft.getInstance().player;
        boolean polearmGuard = p != null && event.isUseItem() && !p.isUsingItem()
                && AmmConfig.POLEARM_BLOCK_ENABLED.get()
                && BlockingHandler.polearmStance(p)
                && (BlockingRules.isPolearm(p.getMainHandItem()) || BlockingRules.isPolearm(p.getOffhandItem()))
                && !BlockingRules.isShield(p.getMainHandItem()) && !BlockingRules.isShield(p.getOffhandItem());
        if (polearmGuard || (clientIsBlocking && (event.isAttack() || event.isUseItem()))) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** A swing at thin air is a miss for the riposte (the server cannot see it). */
    @SubscribeEvent
    static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getEntity() != mc.player) return;
        ItemStack held = mc.player.getMainHandItem();
        if (!BlockingRules.isDualWielding(mc.player) && !BlockingRules.isRiposteItem(held)) return;
        ClientPacketDistributor.sendToServer(new SwingMissPayload());
    }
}
