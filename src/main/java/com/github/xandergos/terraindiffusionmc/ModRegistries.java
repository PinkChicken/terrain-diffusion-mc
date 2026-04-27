package com.github.xandergos.terraindiffusionmc;

import com.github.xandergos.terraindiffusionmc.world.TerrainDiffusionBiomeSource;
import com.github.xandergos.terraindiffusionmc.world.TerrainDiffusionDensityFunction;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistries {

    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
        DeferredRegister.create(Registries.BIOME_SOURCE, TerrainDiffusionMc.MOD_ID);

    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTIONS =
        DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, TerrainDiffusionMc.MOD_ID);

    public static void register(IEventBus eventBus) {
        BIOME_SOURCES.register(eventBus);
        DENSITY_FUNCTIONS.register(eventBus);

        BIOME_SOURCES.register("terrain_diffusion", () -> TerrainDiffusionBiomeSource.CODEC);
        DENSITY_FUNCTIONS.register("terrain_diffusion", () -> TerrainDiffusionDensityFunction.CODEC);
    }

    private ModRegistries() {}
}
