package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.DimensionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WorldScaleManager {
    private static final Logger LOG = LoggerFactory.getLogger(WorldScaleManager.class);

    // Config values 1-4 map to these pixel multipliers (== original scale 1/2/4/6).
    static final int[] MULTIPLIERS = {1, 2, 4, 6};

    // Active multiplier. Starts at the config-file default; overridden at world load
    // when a terrain_diffusion_scale_N dimension type is detected.
    private static volatile int activeMultiplier = MULTIPLIERS[TerrainDiffusionConfig.worldScale() - 1];

    private WorldScaleManager() {}

    /**
     * Called when the overworld loads. Reads the scale from the dimension type key
     * (terrain_diffusion_mc:terrain_diffusion_scale_1 … scale_4) and caches it so
     * HeightConverter always uses the right value for this world.
     *
     * Falls back to the config file when the dimension type isn't one of ours
     * (e.g. legacy worlds created before the scale presets existed).
     */
    public static void initFromDimensionType(ResourceKey<DimensionType> dimTypeKey) {
        if (dimTypeKey != null
                && "terrain_diffusion_mc".equals(dimTypeKey.location().getNamespace())) {
            String path = dimTypeKey.location().getPath();
            for (int i = 0; i < MULTIPLIERS.length; i++) {
                if (path.equals("terrain_diffusion_scale_" + (i + 1))) {
                    activeMultiplier = MULTIPLIERS[i];
                    LOG.info("[TerrainDiffusion] Scale {} → {}m/block (dimension type: {})",
                            activeMultiplier, 30 / activeMultiplier, path);
                    return;
                }
            }
        }
        // Not a scale-specific dimension type — honour the config file.
        activeMultiplier = MULTIPLIERS[TerrainDiffusionConfig.worldScale() - 1];
        LOG.info("[TerrainDiffusion] Scale {} → {}m/block (from config file)",
                activeMultiplier, 30 / activeMultiplier);
    }

    /**
     * Directly set the active multiplier.
     * Called by the chunk-generator mixin as soon as a TD generator is constructed,
     * so the correct scale is ready before any chunk is generated.
     */
    public static void setMultiplier(int multiplier) {
        activeMultiplier = multiplier;
        LOG.info("[TerrainDiffusion] Scale {} → {}m/block (from biome source)",
                multiplier, 30 / multiplier);
    }

    /** Pixel multiplier currently in use (1, 2, 4, or 6). */
    public static int getCurrentScale() {
        return activeMultiplier;
    }

    public static int clampScale(int scale) {
        return Math.max(1, Math.min(4, scale));
    }
}
