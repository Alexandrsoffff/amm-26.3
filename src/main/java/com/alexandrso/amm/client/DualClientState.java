package com.alexandrso.amm.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.entity.Entity;

/** Client-visible off-hand swing timers (entity id -> entity.tickCount at which the swing starts). Used by the render phase. */
public final class DualClientState {
    public static final float SWING_DURATION = 6.0F;
    private static final Map<Integer, Float> SWING_START = new HashMap<>();

    private DualClientState() {
    }

    public static void start(int entityId, float startTick) {
        SWING_START.put(entityId, startTick);
    }

    public static void cancel(int entityId) {
        SWING_START.remove(entityId);
    }

    public static void clear() {
        SWING_START.clear();
    }

    /** 0..1 progress of the entity's off-hand swing, 0 when it is not swinging. */
    public static float offhandSwingProgress(Entity entity, float partialTicks) {
        Float start = SWING_START.get(entity.getId());
        if (start == null) return 0.0F;
        float t = entity.tickCount + partialTicks - start;
        if (t <= 0.0F || t >= SWING_DURATION) return 0.0F;
        return t / SWING_DURATION;
    }
}
