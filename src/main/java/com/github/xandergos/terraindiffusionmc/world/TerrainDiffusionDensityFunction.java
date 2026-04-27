package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.HeightmapData;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.util.KeyDispatchDataCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class TerrainDiffusionDensityFunction implements DensityFunction {
    private static final Logger LOG = LoggerFactory.getLogger(TerrainDiffusionDensityFunction.class);

    // Suppress repeated error spam — log once per tile key, reset every 256 tiles
    private static final AtomicBoolean HAS_LOGGED_FIRST_SUCCESS = new AtomicBoolean(false);
    private static final AtomicInteger ERROR_COUNT = new AtomicInteger(0);

    public static final MapCodec<TerrainDiffusionDensityFunction> CODEC =
            MapCodec.unit(TerrainDiffusionDensityFunction::new);

    @Override
    public double compute(DensityFunction.FunctionContext ctx) {
        int x = ctx.blockX();
        int z = ctx.blockZ();
        int y = ctx.blockY();

        int tileSize = TerrainDiffusionConfig.tileSize();
        int tileShift = Integer.numberOfTrailingZeros(tileSize);

        int tileX = x >> tileShift;
        int tileZ = z >> tileShift;

        int blockStartX = tileX << tileShift;
        int blockStartZ = tileZ << tileShift;
        int blockEndX = blockStartX + tileSize;
        int blockEndZ = blockStartZ + tileSize;

        HeightmapData data;
        try {
            data = LocalTerrainProvider.getInstance().fetchHeightmap(blockStartZ, blockStartX, blockEndZ, blockEndX);
        } catch (Exception e) {
            int count = ERROR_COUNT.incrementAndGet();
            if (count <= 5) {
                LOG.error("[TerrainDiffusion] Pipeline FAILED for tile ({},{})→({},{}). " +
                          "World will appear as flat water until this is fixed. Error: {}",
                          blockStartX, blockStartZ, blockEndX, blockEndZ, e.getMessage());
                LOG.error("[TerrainDiffusion] Full stack trace:", e);
            } else if (count == 6) {
                LOG.error("[TerrainDiffusion] Suppressing further pipeline errors (total failures so far: {}).", count);
            }
            // Return solid terrain at y=80 — clearly wrong but not invisible like -y
            return 80 - y;
        }

        if (data == null || data.heightmap == null) {
            int count = ERROR_COUNT.incrementAndGet();
            if (count <= 3) {
                LOG.error("[TerrainDiffusion] fetchHeightmap returned null for tile ({},{})→({},{}). " +
                          "Pipeline may not be initialised yet.", blockStartX, blockStartZ, blockEndX, blockEndZ);
            }
            return 80 - y;
        }

        int localX = Math.max(0, Math.min(data.width  - 1, x - blockStartX));
        int localZ = Math.max(0, Math.min(data.height - 1, z - blockStartZ));

        short meters = data.heightmap[localZ][localX];
        int targetHeight = HeightConverter.convertToMinecraftHeight(meters);

        // Log first successful tile to confirm the pipeline is working
        if (!HAS_LOGGED_FIRST_SUCCESS.getAndSet(true)) {
            LOG.info("[TerrainDiffusion] First tile OK — sample meters={} → MC y={} (tile {},{} size {})",
                     meters, targetHeight, blockStartX, blockStartZ, tileSize);
        }

        return targetHeight - y;
    }

    @Override
    public void fillArray(double[] densities, DensityFunction.ContextProvider provider) {
        provider.fillAllDirectly(densities, this);
    }

    @Override
    public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(this);
    }

    @Override
    public double minValue() { return -64; }

    @Override
    public double maxValue() { return 1024; }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return KeyDispatchDataCodec.of(CODEC);
    }
}
