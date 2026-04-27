package com.github.xandergos.terraindiffusionmc.mixin;

import com.github.xandergos.terraindiffusionmc.world.TerrainDiffusionBiomeSource;
import com.github.xandergos.terraindiffusionmc.world.TerrainDiffusionFluidLevelSampler;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/**
 * Replaces the default {@link Aquifer.FluidPicker} supplier in {@link NoiseBasedChunkGenerator}
 * with {@link TerrainDiffusionFluidLevelSampler} when the biome source is a
 * {@link TerrainDiffusionBiomeSource}.
 *
 * <p>The field is a {@code Supplier<Aquifer.FluidPicker>} (lazily initialised via
 * {@code Suppliers.memoize}). We replace it with a plain supplier that always
 * returns our sampler instance, which checks AI biome data for ocean/lake and
 * uses low-frequency noise for land aquifer placement.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public class NoiseBasedChunkGeneratorMixin {

    @Mutable
    @Shadow
    private Supplier<Aquifer.FluidPicker> globalFluidPicker;

    @Inject(
        method = "<init>(Lnet/minecraft/world/level/biome/BiomeSource;Lnet/minecraft/core/Holder;)V",
        at = @At("TAIL")
    )
    private void onInit(
            BiomeSource biomeSource,
            Holder<NoiseGeneratorSettings> settings,
            CallbackInfo ci) {

        if (biomeSource instanceof TerrainDiffusionBiomeSource tdbs) {
            // Propagate the per-world scale immediately so LocalTerrainProvider
            // uses the right block→pixel mapping before any chunk is generated.
            WorldScaleManager.setMultiplier(tdbs.getScale());
            TerrainDiffusionFluidLevelSampler sampler = new TerrainDiffusionFluidLevelSampler();
            this.globalFluidPicker = () -> sampler;
        }
    }
}
