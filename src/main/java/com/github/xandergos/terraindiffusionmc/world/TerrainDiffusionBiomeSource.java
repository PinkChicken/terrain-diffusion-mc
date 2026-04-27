package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.HeightmapData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

import net.minecraft.util.RandomSource;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class TerrainDiffusionBiomeSource extends BiomeSource {

    // Custom sparse biome variants (same IDs as original Fabric mod, namespace corrected)
    private static final ResourceKey<Biome> FOREST_SPARSE = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("terrain_diffusion_mc", "forest_sparse"));
    private static final ResourceKey<Biome> TAIGA_SPARSE = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("terrain_diffusion_mc", "taiga_sparse"));
    private static final ResourceKey<Biome> SNOWY_TAIGA_SPARSE = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("terrain_diffusion_mc", "snowy_taiga_sparse"));

    public static final MapCodec<TerrainDiffusionBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    RegistryOps.retrieveGetter(Registries.BIOME),
                    Codec.INT.optionalFieldOf("scale", 1).forGetter(TerrainDiffusionBiomeSource::getScale)
            ).apply(instance, TerrainDiffusionBiomeSource::new));

    /** Pixel multiplier (1, 2, 4, or 6). Stored in level.dat so it survives world reloads. */
    private final int scale;
    private final Map<Short, Holder<Biome>> biomeIdMap;

    public TerrainDiffusionBiomeSource(HolderGetter<Biome> biomes, int scale) {
        this.scale = scale;
        biomeIdMap = new HashMap<>();
        // Vanilla biomes — IDs match BiomeClassifier output
        biomeIdMap.put((short)  1, biomes.getOrThrow(Biomes.PLAINS));
        biomeIdMap.put((short)  3, biomes.getOrThrow(Biomes.SNOWY_PLAINS));
        biomeIdMap.put((short)  5, biomes.getOrThrow(Biomes.DESERT));
        biomeIdMap.put((short)  6, biomes.getOrThrow(Biomes.SWAMP));
        biomeIdMap.put((short)  8, biomes.getOrThrow(Biomes.FOREST));
        biomeIdMap.put((short) 15, biomes.getOrThrow(Biomes.TAIGA));
        biomeIdMap.put((short) 16, biomes.getOrThrow(Biomes.SNOWY_TAIGA));
        biomeIdMap.put((short) 17, biomes.getOrThrow(Biomes.SAVANNA));
        biomeIdMap.put((short) 19, biomes.getOrThrow(Biomes.WINDSWEPT_HILLS));
        biomeIdMap.put((short) 23, biomes.getOrThrow(Biomes.JUNGLE));
        biomeIdMap.put((short) 26, biomes.getOrThrow(Biomes.BADLANDS));
        biomeIdMap.put((short) 29, biomes.getOrThrow(Biomes.MEADOW));
        biomeIdMap.put((short) 31, biomes.getOrThrow(Biomes.GROVE));
        biomeIdMap.put((short) 32, biomes.getOrThrow(Biomes.SNOWY_SLOPES));
        biomeIdMap.put((short) 33, biomes.getOrThrow(Biomes.FROZEN_PEAKS));
        biomeIdMap.put((short) 35, biomes.getOrThrow(Biomes.STONY_PEAKS));
        biomeIdMap.put((short) 41, biomes.getOrThrow(Biomes.WARM_OCEAN));
        biomeIdMap.put((short) 44, biomes.getOrThrow(Biomes.OCEAN));
        biomeIdMap.put((short) 46, biomes.getOrThrow(Biomes.COLD_OCEAN));
        biomeIdMap.put((short) 48, biomes.getOrThrow(Biomes.FROZEN_OCEAN));
        // Custom sparse biomes
        biomeIdMap.put((short) 108, biomes.getOrThrow(FOREST_SPARSE));
        biomeIdMap.put((short) 115, biomes.getOrThrow(TAIGA_SPARSE));
        biomeIdMap.put((short) 116, biomes.getOrThrow(SNOWY_TAIGA_SPARSE));
    }

    /** Returns the pixel multiplier (1, 2, 4, or 6) chosen at world creation. */
    public int getScale() {
        return scale;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomeIdMap.values().stream().distinct();
    }

    /**
     * Short-circuit MC's expanding spiral biome search.
     * The default implementation calls getNoiseBiome for every candidate position,
     * which blocks on fetchHeightmap — causing thousands of tile requests before the
     * world even loads. Return null so the caller defaults to (0, y, 0) for spawn.
     */
    @Override
    @Nullable
    public Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(
            int x, int y, int z, int radius, int blockCheckInterval,
            Predicate<Holder<Biome>> biome, RandomSource random, boolean findClosest,
            Climate.Sampler sampler) {
        return null;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        int blockX = QuartPos.toBlock(x);
        int blockZ = QuartPos.toBlock(z);

        int tileSize  = TerrainDiffusionConfig.tileSize();
        int tileShift = Integer.numberOfTrailingZeros(tileSize);

        int tileX = blockX >> tileShift;
        int tileZ = blockZ >> tileShift;

        int blockStartX = tileX << tileShift;
        int blockStartZ = tileZ << tileShift;
        int blockEndX   = blockStartX + tileSize;
        int blockEndZ   = blockStartZ + tileSize;

        HeightmapData data = LocalTerrainProvider.getInstance()
                .fetchHeightmap(blockStartZ, blockStartX, blockEndZ, blockEndX);

        if (data != null && data.biomeIds != null) {
            int localX = Math.max(0, Math.min(data.width  - 1, blockX - blockStartX));
            int localZ = Math.max(0, Math.min(data.height - 1, blockZ - blockStartZ));
            Holder<Biome> entry = biomeIdMap.get(data.biomeIds[localZ][localX]);
            if (entry != null) return entry;
        }

        return biomeIdMap.get((short) 1); // fallback: Plains
    }
}
