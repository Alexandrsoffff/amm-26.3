package com.alexandrso.amm.entity;

import java.util.function.Supplier;

import com.alexandrso.amm.AlexMovementsMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AmmEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AlexMovementsMod.MODID);

    public static final Supplier<EntityType<SittingSeat>> SITTING_SEAT = ENTITY_TYPES.register("sitting_seat",
            () -> EntityType.Builder.<SittingSeat>of(SittingSeat::new, MobCategory.MISC)
                    .sized(0.1F, 0.03F)
                    .noSummon()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(AlexMovementsMod.MODID, "sitting_seat"))));
    private AmmEntities() {
    }
}
