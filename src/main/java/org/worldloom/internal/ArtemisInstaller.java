package org.worldloom.internal;

import com.artemis.BaseSystem;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.link.EntityLinkManager;
import com.artemis.managers.PlayerManager;
import com.artemis.managers.TagManager;
import com.artemis.managers.TeamManager;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.utils.Array;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.SubscribeAnnotationFinder;
import net.mostlyoriginal.api.event.dispatcher.FastEventDispatcher;
import net.mostlyoriginal.api.plugin.extendedcomponentmapper.ExtendedComponentMapperPlugin;
import net.mostlyoriginal.plugin.ProfilerPlugin;
import org.worldloom.EnginePhase;
import org.worldloom.WorldloomConfig;
import org.worldloom.WorldloomSystemRegistry;
import org.worldloom.audio.AudioConfig;
import org.worldloom.light.AmbientLightConfig;
import org.worldloom.light.AmbientLightTimeSource;
import org.worldloom.light.SunLightConfig;
import org.worldloom.light.TopDownShadowConfig;
import org.worldloom.manager.map.GameSnapshotManager;
import org.worldloom.shader.TileLayerShaderConfig;
import org.worldloom.system.AssetSystem;
import org.worldloom.system.AudioSystem;
import org.worldloom.system.B2dSystem;
import org.worldloom.system.BTreeSystem;
import org.worldloom.system.CameraSystem;
import org.worldloom.system.DynamicAmbientLight;
import org.worldloom.system.DynamicSunLight;
import org.worldloom.system.DebuffSystem;
import org.worldloom.system.EntityFactory;
import org.worldloom.system.InputProcessSystem;
import org.worldloom.system.KeyframeShapeSystem;
import org.worldloom.system.LayerSamplingSystem;
import org.worldloom.system.LightSystem;
import org.worldloom.system.MapTransitionSystem;
import org.worldloom.system.NavigationSystem;
import org.worldloom.system.OnInteractSystem;
import org.worldloom.system.PointLightSystem;
import org.worldloom.system.PixelPerfectCompositeSystem;
import org.worldloom.system.PixelPerfectRenderSystem;
import org.worldloom.system.PosFollowBodySystem;
import org.worldloom.system.RenderBatchingSystem;
import org.worldloom.system.RenderFrameSystem;
import org.worldloom.system.RenderPhysicsSystem;
import org.worldloom.system.RenderTiledSystem;
import org.worldloom.system.RenderUISystem;
import org.worldloom.system.ShaderTileLayerRenderSystem;
import org.worldloom.system.SliceSystem;
import org.worldloom.system.StateSystem;
import org.worldloom.system.SysRestoreSystem;
import org.worldloom.system.TileAnimSystem;
import org.worldloom.system.TiledMapSystem;
import org.worldloom.system.TopDownPointLightRenderSystem;
import org.worldloom.system.TopDownShadowSystem;
import org.worldloom.system.ZIndexSystem;

/** 将 Worldloom 与游戏模块安装到 Artemis，外部项目不应直接使用。 */
public final class ArtemisInstaller {
    private ArtemisInstaller() {
    }

    public static void install(
        WorldConfigurationBuilder builder,
        WorldloomConfig config,
        WorldloomSystemRegistry gameSystems,
        AudioConfig audioConfig,
        AmbientLightTimeSource lightTimeSource,
        AmbientLightConfig ambientLightConfig,
        TopDownShadowConfig shadowConfig,
        SunLightConfig sunLightConfig,
        Array<TileLayerShaderConfig> tileLayerShaders) {

        RenderBatchingSystem renderBatchingSystem = new RenderBatchingSystem();
        CameraSystem cameraSystem = new CameraSystem(
            config.getGameWidth(),
            config.getGameHeight(),
            config.getCameraZoom(),
            config.getWorldScale());

        builder.dependsOn(ExtendedComponentMapperPlugin.class);
        builder.dependsOn(ProfilerPlugin.class);
        builder.dependsOn(TagManager.class);
        builder.dependsOn(PlayerManager.class);
        builder.dependsOn(TeamManager.class);
        builder.dependsOn(EntityLinkManager.class);
        builder.dependsOn(WorldSerializationManager.class);
        builder.with(new EventSystem(
            new FastEventDispatcher(), new SubscribeAnnotationFinder()));

        builder.with(new AssetSystem(config.getSkinPath()));
        String currentMap = GameSnapshotManager.getInstance().getCurrentMap();
        builder.with(new TiledMapSystem(
            currentMap,
            config.getEntityLayers(),
            config.getPhysicsLayers()));
        builder.with(new B2dSystem(
            config.getGravityX(),
            config.getGravityY(),
            config.isPhysicsSleepAllowed(),
            config.getWorldScale(),
            config.isCombineTileShapes()));
        add(builder, gameSystems.systemsFor(EnginePhase.INITIALIZE));

        builder.with(new InputProcessSystem());
        add(builder, gameSystems.systemsFor(EnginePhase.INPUT));
        builder.with(new OnInteractSystem());
        builder.with(new MapTransitionSystem());
        add(builder, gameSystems.systemsFor(EnginePhase.PRE_UPDATE));
        builder.with(new PosFollowBodySystem(config.getWorldScale()));
        builder.with(new BTreeSystem());
        builder.with(new StateSystem());
        builder.with(new DebuffSystem());
        add(builder, gameSystems.systemsFor(EnginePhase.UPDATE));
        builder.with(new NavigationSystem(
            config.getWorldScale(),
            config.getNavigationRoadLayers(),
            config.getNavigationRoadCost(),
            config.getNavigationOffRoadCost()));
        builder.with(cameraSystem);
        builder.with(new AudioSystem(audioConfig));
        builder.with(new KeyframeShapeSystem());
        builder.with(new TileAnimSystem());
        builder.with(new SliceSystem());
        builder.with(new LayerSamplingSystem());
        builder.with(new ZIndexSystem());
        add(builder, gameSystems.systemsFor(EnginePhase.POST_UPDATE));
        add(builder, gameSystems.systemsFor(EnginePhase.PRE_RENDER));

        PixelPerfectRenderSystem pixelPerfectRenderSystem =
            new PixelPerfectRenderSystem(
                config.getPixelPerfectCameraConfig());
        builder.with(pixelPerfectRenderSystem);

        if (lightTimeSource != null) {
            builder.with(new DynamicAmbientLight(lightTimeSource, ambientLightConfig));
            builder.with(new DynamicSunLight(lightTimeSource, sunLightConfig));
        }

        builder.with(new RenderTiledSystem(config.getWorldScale()));
        for (TileLayerShaderConfig shaderConfig : tileLayerShaders) {
            builder.with(new ShaderTileLayerRenderSystem(shaderConfig));
        }
        builder.with(renderBatchingSystem);
        builder.with(new RenderFrameSystem(renderBatchingSystem, config.getWorldScale()));
        builder.with(new RenderPhysicsSystem());
        add(builder, gameSystems.systemsFor(EnginePhase.WORLD_EFFECT));

        if (lightTimeSource != null) {
            builder.with(
                WorldConfigurationBuilder.Priority.LOWEST,
                new TopDownShadowSystem(
                    config.getWorldScale(), shadowConfig, sunLightConfig));
        }
        builder.with(
            WorldConfigurationBuilder.Priority.LOWEST,
            new SysRestoreSystem(true));
        builder.with(
            WorldConfigurationBuilder.Priority.LOWEST,
            new EntityFactory());
        builder.with(
            WorldConfigurationBuilder.Priority.LOWEST,
            new PointLightSystem());
        builder.with(
            WorldConfigurationBuilder.Priority.LOWEST,
            new LightSystem(config.isLegacyBox2dLightsEnabled()));
        addLowest(builder, gameSystems.systemsFor(EnginePhase.POST_AMBIENT));
        if (lightTimeSource != null) {
            builder.with(
                WorldConfigurationBuilder.Priority.LOWEST,
                new TopDownPointLightRenderSystem());
        }
        addLowest(builder, gameSystems.systemsFor(EnginePhase.POST_RENDER));
        builder.with(
            WorldConfigurationBuilder.Priority.LOWEST,
            new PixelPerfectCompositeSystem(pixelPerfectRenderSystem));
        addLowest(builder, gameSystems.systemsFor(EnginePhase.UI));
        builder.with(
            WorldConfigurationBuilder.Priority.LOWEST,
            new RenderUISystem(config.getUiWidth(), config.getUiHeight()));
    }

    private static void add(WorldConfigurationBuilder builder, Array<BaseSystem> systems) {
        for (BaseSystem system : systems) {
            builder.with(system);
        }
    }

    private static void addLowest(WorldConfigurationBuilder builder, Array<BaseSystem> systems) {
        for (BaseSystem system : systems) {
            builder.with(WorldConfigurationBuilder.Priority.LOWEST, system);
        }
    }
}
