package com.alexandrso.amm.movement;

import com.alexandrso.amm.AlexMovementsMod;
import com.alexandrso.amm.config.AmmConfig;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Manual crawling (Ctrl + Shift, hold Shift to keep crawling).
 * <p>
 * Since 1.14 vanilla already has swimming (sprint in water) and automatic crawling in 1-block
 * gaps, with the right 0.6 hitbox, so all of Ido's hitbox/pose/reflection code from the 1.12.2
 * mod is gone. What vanilla does NOT have is crawling on open ground on purpose - that is the
 * only thing left here: we force the vanilla SWIMMING pose, which on dry land is rendered as
 * the vanilla crawl animation and uses the vanilla crawl hitbox.
 */
@EventBusSubscriber(modid = AlexMovementsMod.MODID)
public final class CrawlHandler {
    private CrawlHandler() {
    }

    public static boolean isManualCrawling(Player player) {
        return player.getData(AmmAttachments.MANUAL_CRAWL.get());
    }

    public static void setManualCrawl(Player player, boolean value) {
        if (isManualCrawling(player) != value) {
            player.setData(AmmAttachments.MANUAL_CRAWL.get(), value);
        }
    }

    /** Conditions that must hold for a manual crawl to start or continue. */
    public static boolean canCrawl(Player player) {
        return AmmConfig.CRAWL_ENABLED.get()
                && !player.isSpectator()
                && !player.isInWater()
                && !player.isPassenger()
                && !player.isFallFlying()
                && !player.getAbilities().flying;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();

        // Ledge climb: the server resets ITS fall distance (the client's copy is not what fall damage uses).
        if (!player.level().isClientSide() && player.getData(AmmAttachments.WALL_CLIMBING.get())) {
            if (AmmConfig.LEDGE_CLIMB_ENABLED.get() && !player.onGround()) player.resetFallDistance();
            else player.setData(AmmAttachments.WALL_CLIMBING.get(), false);
        }

        if (!isManualCrawling(player)) return;

        if (!canCrawl(player)) {
            // Only the server (and the local player's own client) end the crawl; remote clients
            // just wait for the synced attachment to change.
            if (!player.level().isClientSide() || player.isLocalPlayer()) {
                setManualCrawl(player, false);
            }
            return;
        }

        // updatePlayerPose() ran earlier this tick and picked STANDING/CROUCHING; override it.
        // NOTE: if this ever shows hitbox jitter in tight spaces, replace it with a mixin into
        // Player#updatePlayerPose (see PORTING.md).
        player.setPose(Pose.SWIMMING);

        // Old preventLadderClimbWhileCrawling: crawling players cannot climb.
        if (player.onClimbable()) {
            Vec3 v = player.getDeltaMovement();
            if (v.y > 0.0D) player.setDeltaMovement(v.x, 0.0D, v.z);
        }
    }

    /** A crawling player (manual, or squeezed into a gap on dry land) breaks blocks 65% slower. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (player.isInWater() || player.isSpectator()) return;
        if (isManualCrawling(player) || player.isVisuallyCrawling()) {
            event.setNewSpeed(event.getNewSpeed() * AmmConfig.CRAWL_BREAK_SPEED_MULTIPLIER.get().floatValue());
        }
    }
}
