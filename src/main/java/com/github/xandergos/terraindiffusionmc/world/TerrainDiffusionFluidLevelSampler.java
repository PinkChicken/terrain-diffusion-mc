package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import com.github.xandergos.terraindiffusionmc.pipeline.FastNoiseLite;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.HeightmapData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;

/**
 * Controls where cave water appears in Terrain Diffusion worlds.
 *
 * <p>Rules (evaluated per aquifer cell, ~64-block regions):
 * <ol>
 *   <li><b>Ocean / Lake cells</b> — fluid level = 63 (sea level). Any cave that
 *       intersects the ocean floor is fully water-filled up to sea level.</li>
 *   <li><b>Land cells, high noise</b> — fluid level = 40. The deepest parts of
 *       caves are partially flooded, similar to a vanilla aquifer pocket.</li>
 *   <li><b>Land cells, low noise</b> — fluid level = -128 (below world bottom).
 *       Caves are completely dry.</li>
 * </ol>
 *
 * <p>The noise used for land cells is very low-frequency (~512 blocks per cycle)
 * so wet and dry regions form large, geologically coherent patches.
 */
public class TerrainDiffusionFluidLevelSampler implements Aquifer.FluidPicker {

    private static final BlockState WATER = Blocks.WATER.defaultBlockState();

    /** Sea level — ocean/lake caves always flood to this height. */
    private static final int SEA_LEVEL = 63;

    /**
     * Partial flood level for land aquifer pockets.
     * Only the lowest section of a cave (below y=40) gets water here.
     */
    private static final int CAVE_AQUIFER_LEVEL = 40;

    /**
     * Below world bottom — matches MC's own "no fluid" sentinel
     * (DimensionType.MIN_Y * 2 = -128). FluidStatus.at(y) returns AIR for all
     * valid y values, keeping the cave dry.
     */
    private static final int DRY_LEVEL = -128;

    /**
     * Fraction of land cells that become aquifer pockets.
     * Noise range is [-1, 1]; values above this threshold → wet.
     * 0.65 ≈ ~8 % of land area flooded (far upper tail of the distribution),
     * giving rare, isolated wet pockets instead of large flooded zones.
     */
    private static final float WET_THRESHOLD = 0.65f;

    // Medium-frequency Perlin noise: ~96-block spatial scale so wet blobs are
    // small (roughly cave-passage sized) and well-separated from each other.
    // Fixed seed — pattern varies per location, not per world seed.
    private final FastNoiseLite landNoise;

    public TerrainDiffusionFluidLevelSampler() {
        landNoise = new FastNoiseLite(0x1A2B3C4D);
        landNoise.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        landNoise.SetFrequency(1.0f / 96f);
    }

    @Override
    public Aquifer.FluidStatus computeFluid(int x, int y, int z) {
        LocalTerrainProvider provider = LocalTerrainProvider.getInstance();
        if (provider == null) {
            return new Aquifer.FluidStatus(DRY_LEVEL, WATER);
        }

        int tileSize  = TerrainDiffusionConfig.tileSize();
        int tileShift = Integer.numberOfTrailingZeros(tileSize);
        int tileX = x >> tileShift;
        int tileZ = z >> tileShift;
        int blockStartX = tileX << tileShift;
        int blockStartZ = tileZ << tileShift;

        try {
            HeightmapData data = provider.fetchHeightmap(
                    blockStartZ, blockStartX,
                    blockStartZ + tileSize, blockStartX + tileSize);

            if (data != null && data.biomeIds != null) {
                int localX = Math.max(0, Math.min(data.width  - 1, x - blockStartX));
                int localZ = Math.max(0, Math.min(data.height - 1, z - blockStartZ));
                short biomeId = data.biomeIds[localZ][localX];

                if (isOceanOrLake(biomeId)) {
                    // Ocean/lake: flood caves up to sea level
                    return new Aquifer.FluidStatus(SEA_LEVEL, WATER);
                }
            }
        } catch (Exception ignored) {
            // Pipeline not ready yet — treat as dry land
        }

        // Land cell: apply low-frequency noise
        float noise = landNoise.GetNoise(x, z);   // [-1, 1]
        if (noise > WET_THRESHOLD) {
            return new Aquifer.FluidStatus(CAVE_AQUIFER_LEVEL, WATER);
        }
        return new Aquifer.FluidStatus(DRY_LEVEL, WATER);
    }

    /**
     * Ocean biome IDs from {@link TerrainDiffusionBiomeSource}:
     * 41=warm_ocean, 44=ocean, 46=cold_ocean, 48=frozen_ocean.
     */
    private static boolean isOceanOrLake(short biomeId) {
        return biomeId == 41 || biomeId == 44 || biomeId == 46 || biomeId == 48;
    }
}
