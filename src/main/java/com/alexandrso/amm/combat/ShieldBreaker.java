package com.alexandrso.amm.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Knocks a raised shield out of the target's hands (what an axe hit does to a blocking target). */
public final class ShieldBreaker {
    /** Ticks a knocked-out shield stays unusable (5 seconds). */
    private static final int DISABLE_TICKS = 100;

    private ShieldBreaker() {
    }

    /** Mob: its guard goal drops the shield and refuses it for a while. Player: the shield goes on cooldown, stops being used, and the break sound/animation plays. */
    public static void disable(LivingEntity target) {
        if (target instanceof Mob mob) {
            if (MobCombatHandler.hasRaisedShield(mob)) MobCombatHandler.disableShield(mob);
            return;
        }
        if (!(target instanceof Player victim) || !victim.isUsingItem()) return;
        ItemStack active = victim.getUseItem();
        victim.getCooldowns().addCooldown(active, DISABLE_TICKS);
        victim.stopUsingItem();
        victim.level().broadcastEntityEvent(victim, (byte) 30);
    }
}
