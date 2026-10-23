package com.alexandrso.amm.client.render;

/** Duck interface injected into LivingEntityRenderState: every render state carries our pose data. */
public interface AmmStateAccess {
    AmmPoseData amm$data();
}
