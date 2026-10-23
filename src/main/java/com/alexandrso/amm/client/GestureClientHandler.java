package com.alexandrso.amm.client;


import com.alexandrso.amm.AlexMovementsMod;
import com.alexandrso.amm.movement.AmmAttachments;
import com.alexandrso.amm.network.GesturePayload;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * Easter-egg gestures. Hold the gesture key + attack: twirl the held weapon. Hold the key alone:
 * raise the main hand and point where you look. No gameplay effect; the poses are drawn by the
 * render phase from the synced SPINNING / POINTING attachments.
 * <p>
 * The real attack is swallowed in InteractionKeyMappingTriggered instead of by forcing the key
 * state up (the 1.12 version did that and had to poll the hardware to stay sane).
 */
@EventBusSubscriber(modid = AlexMovementsMod.MODID, value = Dist.CLIENT)
public final class GestureClientHandler {
    /** One physical key (Alt) for both gestures, like the old SPIN_KEY / POINT_KEY. */
    public static final KeyMapping GESTURE_KEY = new KeyMapping("key.amm.gesture", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD, InputConstants.KEY_LALT, CrawlKeyHandler.CATEGORY);

    private static boolean spinning;
    private static boolean pointing;

    private GestureClientHandler() {
    }

    public static boolean isSpinning() {
        return spinning;
    }

    @SubscribeEvent
    static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(GESTURE_KEY);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gui.screen != null) {
            spinning = false;
            pointing = false;
            if (player != null) push(player, true, false);
            if (player != null) push(player, false, false);
            return;
        }

        boolean keyDown = GESTURE_KEY.isDown();
        boolean nowSpinning = keyDown && mc.options.keyAttack.isDown();
        boolean nowPointing = keyDown && !nowSpinning; // mutually exclusive: key + attack is the twirl

        if (nowSpinning != spinning) {
            spinning = nowSpinning;
            push(player, true, nowSpinning);
        }
        if (nowPointing != pointing) {
            pointing = nowPointing;
            push(player, false, nowPointing);
        }
    }

    private static void push(LocalPlayer player, boolean spin, boolean value) {
        var type = spin ? AmmAttachments.SPINNING.get() : AmmAttachments.POINTING.get();
        if (player.getData(type) == value) return;
        player.setData(type, value); // instant local feedback
        ClientPacketDistributor.sendToServer(new GesturePayload(spin, value));
    }

    /** While twirling, the real attack must not fire (its swing would fight the twirl). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (spinning && event.isAttack()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }
}
