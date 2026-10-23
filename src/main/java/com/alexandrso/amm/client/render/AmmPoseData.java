package com.alexandrso.amm.client.render;

import com.alexandrso.amm.client.DualClientState;
import com.alexandrso.amm.combat.BlockingHandler;
import com.alexandrso.amm.config.AmmConfig;
import com.alexandrso.amm.config.BlockingRules;
import com.alexandrso.amm.movement.AmmAttachments;
import com.alexandrso.amm.movement.SpiderWallHandler;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Everything the poses need, copied from the entity ONCE per frame in extractRenderState
 * (the render thread must not read live entity data while it draws). Read by AmmPoses and the
 * setupRotations hooks.
 */
public final class AmmPoseData {
    public boolean isPlayer;
    public boolean guardPolearm;
    public boolean blocking;
    public boolean spinning;
    public boolean pointing;
    public boolean wallClimb;
    public boolean woundedCrawl;
    public boolean dual;
    public boolean polearmInMain;
    public boolean mainCanBlock;
    public boolean offCanBlock;
    public boolean hasShield;
    public boolean mainTool;
    public boolean offTool;
    public boolean rightMain;
    public boolean usingItem;
    public boolean busy; // gliding or riding: no tool tilt
    public float offhandSwing;
    public int entityId;

    // spider on a wall
    public float spiderProgress;
    public float spiderTheta;
    public Direction spiderWall;
    public float spiderWalkPos;
    public float spiderWalkSpeed;

    public void fill(LivingEntity e, float partial) {
        entityId = e.getId();
        isPlayer = e instanceof Player;
        blocking = BlockingHandler.isBlocking(e);
        spinning = e.getData(AmmAttachments.SPINNING.get());
        pointing = isPlayer && e.getData(AmmAttachments.POINTING.get());
        wallClimb = isPlayer && e.getData(AmmAttachments.WALL_CLIMBING.get());
        woundedCrawl = e.getData(AmmAttachments.WOUNDED_CRAWL.get());

        ItemStack main = e.getMainHandItem();
        ItemStack off = e.getOffhandItem();
        hasShield = BlockingRules.isShield(main) || BlockingRules.isShield(off);
        mainCanBlock = BlockingRules.canBlock(main);
        offCanBlock = BlockingRules.canBlock(off);
        dual = BlockingRules.isDualWielding(e);
        guardPolearm = BlockingRules.isPolearm(main) || BlockingRules.isPolearm(off);
        polearmInMain = BlockingRules.isPolearm(main);
        mainTool = BlockingRules.isHeldTool(main);
        offTool = BlockingRules.isHeldTool(off);
        rightMain = e.getMainArm() == HumanoidArm.RIGHT;
        usingItem = e.isUsingItem();
        busy = e.isFallFlying() || e.isPassenger();
        offhandSwing = DualClientState.offhandSwingProgress(e, partial);

        SpiderWallHandler.Data d = SpiderWallHandler.get(e);
        if (d != null) {
            spiderProgress = d.prevProgress + (d.progress - d.prevProgress) * partial;
            spiderTheta = d.prevTheta + SpiderWallHandler.wrapRadians(d.theta - d.prevTheta) * partial;
            spiderWall = d.renderWall;
            spiderWalkSpeed = d.prevWalkSpeed + (d.walkSpeed - d.prevWalkSpeed) * partial;
            spiderWalkPos = d.walkPos - d.walkSpeed * (1.0F - partial);
        } else {
            spiderWalkPos = 0.0F;
            spiderWalkSpeed = 0.0F;
            spiderProgress = 0.0F;
            spiderWall = null;
        }
    }
}
