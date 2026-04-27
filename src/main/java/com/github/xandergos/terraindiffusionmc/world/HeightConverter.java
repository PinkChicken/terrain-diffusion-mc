package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.pipeline.WorldPipelineModelConfig;

public class HeightConverter {
    private static final int SEA_LEVEL = 63;

    /**
     * resolution = nativeResolution / scale, matching the original formula.
     * nativeResolution=30 m/px, scale=2 (default) → 15 m/block.
     * Land: baseY = meters / resolution (linear).
     * Ocean: baseY = -sqrt(|meters|+10) + sqrt(10) - 1 (compressed, avoids deep floor).
     */
    public static int convertToMinecraftHeight(short meters) {
        int scale = WorldScaleManager.getCurrentScale();
        float resolution = WorldPipelineModelConfig.nativeResolution() / (float) scale;

        int baseY;
        if (meters >= 0) {
            baseY = (int) (meters / resolution);
        } else {
            baseY = (int) (-Math.sqrt(Math.abs(meters) + 10) + Math.sqrt(10.0)) - 1;
        }
        return baseY + SEA_LEVEL;
    }
}
