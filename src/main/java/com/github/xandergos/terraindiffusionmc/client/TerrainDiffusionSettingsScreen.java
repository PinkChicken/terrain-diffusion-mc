package com.github.xandergos.terraindiffusionmc.client;

import com.github.xandergos.terraindiffusionmc.TerrainDiffusionMc;
import com.github.xandergos.terraindiffusionmc.world.TerrainDiffusionBiomeSource;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;
import java.util.Map;

/**
 * World-creation customise screen for the Terrain Diffusion world preset.
 *
 * <p>Lets the player choose one of four resolution scales before the world
 * is created.  The selection is persisted via the {@code scale} field in the
 * {@link TerrainDiffusionBiomeSource} codec, so it survives world reloads
 * without needing any external config file.
 */
@OnlyIn(Dist.CLIENT)
public class TerrainDiffusionSettingsScreen extends Screen {

    /**
     * Pixel multiplier values in display order.
     * Each entry corresponds to a {@code terrain_diffusion_scale_N} dimension type
     * where N is the 1-based index.
     */
    private static final int[] SCALE_VALUES = {1, 2, 4, 6};

    /** Human-readable labels shown in the cycle button, keyed by SCALE_VALUES index. */
    private static final String[] SCALE_LABELS = {
        "1× — 30 m/block",
        "2× — 15 m/block",
        "4× — 7.5 m/block",
        "6× — 5 m/block"
    };

    private final CreateWorldScreen parent;
    private final WorldCreationContext context;

    /** Index into {@link #SCALE_VALUES} currently selected. */
    private int selectedScaleIndex;

    public TerrainDiffusionSettingsScreen(CreateWorldScreen parent, WorldCreationContext context) {
        super(Component.translatable("createWorld.customize.terrain_diffusion.title"));
        this.parent  = parent;
        this.context = context;

        // Pre-select whatever scale is already encoded in the generator (if any).
        ChunkGenerator gen = context.selectedDimensions().overworld();
        int existingScale = 1;
        if (gen instanceof NoiseBasedChunkGenerator nbcg
                && nbcg.getBiomeSource() instanceof TerrainDiffusionBiomeSource tdbs) {
            existingScale = tdbs.getScale();
        }
        selectedScaleIndex = indexOf(existingScale);
    }

    @Override
    protected void init() {
        int cx = this.width  / 2;
        int cy = this.height / 2;

        // Scale selector
        CycleButton<Integer> scaleButton = CycleButton.<Integer>builder(
                    val -> Component.literal(SCALE_LABELS[indexOf(val)]))
                .withValues(List.of(SCALE_VALUES[0], SCALE_VALUES[1], SCALE_VALUES[2], SCALE_VALUES[3]))
                .withInitialValue(SCALE_VALUES[selectedScaleIndex])
                .create(cx - 100, cy - 20, 200, 20,
                        Component.translatable("createWorld.customize.terrain_diffusion.scale"),
                        (btn, val) -> selectedScaleIndex = indexOf(val));
        addRenderableWidget(scaleButton);

        // Done / Cancel
        addRenderableWidget(
            Button.builder(Component.translatable("gui.done"), btn -> applyAndClose())
                  .bounds(cx - 102, cy + 10, 100, 20).build());
        addRenderableWidget(
            Button.builder(Component.translatable("gui.cancel"), btn -> onClose())
                  .bounds(cx + 2,   cy + 10, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        // Brief description below the title
        Component desc = Component.translatable("createWorld.customize.terrain_diffusion.desc");
        graphics.drawCenteredString(this.font, desc, this.width / 2, 35, 0xAAAAAA);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    // -------------------------------------------------------------------------

    private void applyAndClose() {
        int scaleValue = SCALE_VALUES[selectedScaleIndex];
        int dimIndex   = selectedScaleIndex + 1;   // 1-based: scale_1 … scale_4

        parent.getUiState().updateDimensions((registryAccess, worldDimensions) -> {
            // 1. Select dimension type that matches the chosen scale's world height.
            Registry<DimensionType> dimTypes = registryAccess.registryOrThrow(Registries.DIMENSION_TYPE);
            ResourceKey<DimensionType> dimKey = ResourceKey.create(
                    Registries.DIMENSION_TYPE,
                    ResourceLocation.fromNamespaceAndPath(TerrainDiffusionMc.MOD_ID,
                            "terrain_diffusion_scale_" + dimIndex));
            Holder<DimensionType> dimHolder = dimTypes.getHolderOrThrow(dimKey);

            // 2. Rebuild biome source with the chosen scale encoded inside.
            HolderGetter<Biome> biomeGetter = registryAccess.lookupOrThrow(Registries.BIOME);
            TerrainDiffusionBiomeSource newSource =
                    new TerrainDiffusionBiomeSource(biomeGetter, scaleValue);

            // 3. Get the shared noise settings (same for all scales).
            Registry<NoiseGeneratorSettings> noiseSets =
                    registryAccess.registryOrThrow(Registries.NOISE_SETTINGS);
            Holder<NoiseGeneratorSettings> noiseHolder = noiseSets.getHolderOrThrow(
                    ResourceKey.create(Registries.NOISE_SETTINGS,
                            ResourceLocation.fromNamespaceAndPath(
                                    TerrainDiffusionMc.MOD_ID, "terrain_diffusion")));

            // 4. Build the new chunk generator and replace the overworld stem.
            ChunkGenerator newGen = new NoiseBasedChunkGenerator(newSource, noiseHolder);
            Map<ResourceKey<LevelStem>, LevelStem> newMap =
                    WorldDimensions.withOverworld(worldDimensions.dimensions(), dimHolder, newGen);
            return new WorldDimensions(newMap);
        });

        this.minecraft.setScreen(parent);
    }

    /** Returns the index of {@code value} in {@link #SCALE_VALUES}, or 0 if not found. */
    private static int indexOf(int value) {
        for (int i = 0; i < SCALE_VALUES.length; i++) {
            if (SCALE_VALUES[i] == value) return i;
        }
        return 0;
    }
}
