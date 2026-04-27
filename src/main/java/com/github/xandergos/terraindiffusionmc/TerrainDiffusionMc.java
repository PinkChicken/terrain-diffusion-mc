package com.github.xandergos.terraindiffusionmc;

import com.github.xandergos.terraindiffusionmc.explorer.ExplorerServer;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.ModelAssetManager;
import com.github.xandergos.terraindiffusionmc.pipeline.PipelineModels;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.commands.Commands.literal;

@Mod(TerrainDiffusionMc.MOD_ID)
public class TerrainDiffusionMc {
    public static final String MOD_ID = "terrain_diffusion_mc";
    private static final Logger LOG = LoggerFactory.getLogger(TerrainDiffusionMc.class);

    public TerrainDiffusionMc(IEventBus modEventBus) {
        LOG.info("Initializing terrain_diffusion_mc");

        ModRegistries.register(modEventBus);

        ModelAssetManager.ensureAssetsReady();
        PipelineModels.load();

        NeoForge.EVENT_BUS.addListener(TerrainDiffusionMc::onServerStarting);
        NeoForge.EVENT_BUS.addListener(TerrainDiffusionMc::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(TerrainDiffusionMc::onServerStopping);
        NeoForge.EVENT_BUS.addListener(TerrainDiffusionMc::onRegisterCommands);
    }

    private static void onServerStarting(ServerStartingEvent event) {
        LocalTerrainProvider.clearCache();
    }

    private static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            LocalTerrainProvider.init(level.getSeed());
            // Detect world scale from the dimension type key (terrain_diffusion_mc:terrain_diffusion_scale_N)
            var dimTypeKey = level.dimensionTypeRegistration().unwrapKey().orElse(null);
            WorldScaleManager.initFromDimensionType(dimTypeKey);
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        ExplorerServer.stop();
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            literal("td-explore").executes(TerrainDiffusionMc::executeExplore)
        );
    }

    private static int executeExplore(CommandContext<CommandSourceStack> ctx) {
        try {
            int port = ExplorerServer.startIfNotRunning();
            String url = "http://localhost:" + port;
            MutableComponent link = Component.literal(url)
                .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                                 .withUnderlined(true));
            ctx.getSource().sendSuccess(
                () -> Component.literal("Terrain Explorer: ").append(link),
                false);
        } catch (Exception e) {
            LOG.error("Failed to start terrain explorer", e);
            ctx.getSource().sendFailure(Component.literal("Failed to start terrain explorer: " + e.getMessage()));
        }
        return 1;
    }
}
