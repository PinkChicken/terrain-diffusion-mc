package com.github.xandergos.terraindiffusionmc.client;

import com.github.xandergos.terraindiffusionmc.TerrainDiffusionMc;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterPresetEditorsEvent;

/**
 * Client-side mod-bus event handlers.
 *
 * <p>Registers the "Customize" screen for the Terrain Diffusion world preset so
 * that the world-creation UI shows a settings button when the preset is selected.
 */
@EventBusSubscriber(modid = TerrainDiffusionMc.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEventHandlers {

    private ClientEventHandlers() {}

    @SubscribeEvent
    public static void registerPresetEditors(RegisterPresetEditorsEvent event) {
        ResourceKey<WorldPreset> tdPreset = ResourceKey.create(
                Registries.WORLD_PRESET,
                ResourceLocation.fromNamespaceAndPath(TerrainDiffusionMc.MOD_ID, "terrain_diffusion"));

        // TerrainDiffusionSettingsScreen(CreateWorldScreen, WorldCreationContext)
        // exactly matches the PresetEditor functional-interface signature.
        event.register(tdPreset, TerrainDiffusionSettingsScreen::new);
    }
}
