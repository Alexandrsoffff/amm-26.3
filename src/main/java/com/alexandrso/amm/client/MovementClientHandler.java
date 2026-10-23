package com.alexandrso.amm.client;

import com.alexandrso.amm.AlexMovementsMod;
import com.alexandrso.amm.config.AmmConfig;
import com.alexandrso.amm.movement.AmmAttachments;
import com.alexandrso.amm.movement.CrawlHandler;
import com.alexandrso.amm.network.WallClimbPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Ledge climb (hold sprint while airborne next to a ledge) and the dodge roll (hold A/S/D, tap Space). */
@EventBusSubscriber(modid = AlexMovementsMod.MODID, value = Dist.CLIENT)
public final class MovementClientHandler {
    private static final float DODGE_FORCE_VERTICAL = 0.20F;
    private static final float DODGE_FORCE_HORIZONTAL = 0.40F;
    private static final int DODGE_COOLDOWN_TICKS = 50;

    private static boolean wasClimbing;
    private static boolean jumpWasDown;
    private static int dodgeCooldown;

    private MovementClientHandler() {
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            wasClimbing = false;
            return;
        }
        ledgeClimb(mc, player);
        dodge(mc, player);
    }

    // ── ledge climb ─────────────────────────────────────────────────────────────

    private static boolean isFree(Level level, LocalPlayer player, AABB box) {
        return !level.getBlockCollisions(player, box).iterator().hasNext();
    }

    private static void ledgeClimb(Minecraft mc, LocalPlayer player) {
        if (!AmmConfig.LEDGE_CLIMB_ENABLED.get() || mc.gui.screen != null || !mc.options.keySprint.isDown()
                || player.isSpectator() || player.getAbilities().flying) {
            setClimbing(player, false);
            return;
        }

        Level level = player.level();
        Vec3 look = player.getLookAngle();
        Vec3 lookFront = look.scale(0.75D);
        Vec3 lookBehind = look.scale(-0.25D);

        double yScanRangeAir = player.getBbHeight() + 0.2D;
        double yScanRangeSolid = 0.4D;
        double yScanRes = 0.2D;
        double yAirSize = 0.25D;
        double xzSize = 0.3D;
        double xzSizeBehind = 0.1D;

        AABB body = player.getBoundingBox();
        AABB spotForHands = new AABB(player.getX() + lookFront.x, body.minY, player.getZ() + lookFront.z,
                player.getX() + lookFront.x, body.minY, player.getZ() + lookFront.z).inflate(xzSize, yAirSize, xzSize);
        AABB behindUnderFeet = new AABB(player.getX() + lookBehind.x, body.minY, player.getZ() + lookBehind.z,
                player.getX() + lookBehind.x, body.minY, player.getZ() + lookBehind.z).inflate(xzSizeBehind, yAirSize, xzSizeBehind);

        boolean found = false;
        // Only while airborne and not already standing on the ledge itself.
        if (!player.onGround() && isFree(level, player, behindUnderFeet)) {
            for (double y = yScanRangeAir; y > 0.25D && !found; y -= yScanRes) {
                // Open air for the hands - now look for solid ground just below it.
                if (!isFree(level, player, spotForHands.move(0, y, 0))) continue;
                for (double y2 = 0; y2 < yScanRangeSolid; y2 += yScanRes) {
                    AABB probe = spotForHands.move(0, y - yAirSize - y2, 0);
                    if (!isFree(level, player, probe) && probe.minY + 0.15D > body.minY) {
                        found = true;
                        break;
                    }
                }
            }
        }

        setClimbing(player, found);

        if (found) {
            Vec3 v = player.getDeltaMovement();
            if (v.y < 0.08D) player.setDeltaMovement(v.x, 0.08D, v.z);
            player.resetFallDistance(); // like a ladder; the server resets its own copy (see below)
        }
    }

    private static void setClimbing(LocalPlayer player, boolean now) {
        if (now == wasClimbing) return;
        wasClimbing = now;
        player.setData(AmmAttachments.WALL_CLIMBING.get(), now);
        ClientPacketDistributor.sendToServer(new WallClimbPayload(now));
    }

    // ── dodge roll ──────────────────────────────────────────────────────────────

    private static void dodge(Minecraft mc, LocalPlayer player) {
        if (!AmmConfig.DODGE_ENABLED.get() || mc.gui.screen != null) return;
        if (dodgeCooldown > 0) dodgeCooldown--;

        Options o = mc.options;
        boolean jumpDown = o.keyJump.isDown();
        boolean pressed = jumpDown && !jumpWasDown;
        jumpWasDown = jumpDown;
        if (!pressed || dodgeCooldown > 0) return;

        // Not while flying, gliding, swimming, crawling, or when too hungry to sprint.
        if (player.getAbilities().flying || player.isFallFlying() || player.isInWater()) return;
        if (player.isVisuallyCrawling() || CrawlHandler.isManualCrawling(player) || player.isPassenger()) return;
        if (player.getFoodData().getFoodLevel() <= 6) return;

        boolean justJumped = !player.onGround() && player.getDeltaMovement().y > 0.0D && player.fallDistance == 0.0D;
        if (!player.onGround() && !justJumped) return;

        boolean forward = o.keyUp.isDown();
        boolean back = o.keyDown.isDown();
        boolean left = o.keyLeft.isDown();
        boolean right = o.keyRight.isDown();
        if (forward) return;                       // no speed abuse
        if (!back && !left && !right) return;      // needs a direction

        float yaw = player.getYRot();
        if (back) {
            yaw += 180.0F;
            if (left) yaw += 45.0F;
            else if (right) yaw -= 45.0F;
        } else if (left) {
            yaw -= 90.0F;
        } else {
            yaw += 90.0F;
        }

        float dashYaw = yaw * Mth.DEG_TO_RAD;
        player.setDeltaMovement(-Mth.sin(dashYaw) * DODGE_FORCE_HORIZONTAL, DODGE_FORCE_VERTICAL, Mth.cos(dashYaw) * DODGE_FORCE_HORIZONTAL);
        dodgeCooldown = DODGE_COOLDOWN_TICKS;
    }
}
