package org.worldloom;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.Array;
import org.worldloom.audio.AudioConfig;
import org.worldloom.internal.ArtemisInstaller;
import org.worldloom.light.AmbientLightConfig;
import org.worldloom.light.AmbientLightTimeSource;
import org.worldloom.light.SunLightConfig;
import org.worldloom.light.TopDownShadowConfig;
import org.worldloom.manager.map.GameSnapshotManager;
import org.worldloom.shader.TileLayerShaderConfig;

/** 构建一个包含引擎系统和游戏业务系统的运行实例。 */
public final class WorldloomBuilder {
    private final WorldloomConfig config;
    private final WorldloomSystemRegistry systems = new WorldloomSystemRegistry();
    private final Array<TileLayerShaderConfig> tileLayerShaders = new Array<>();
    private AmbientLightTimeSource lightTimeSource;
    private AmbientLightConfig ambientLightConfig;
    private TopDownShadowConfig shadowConfig;
    private SunLightConfig sunLightConfig;
    private AudioConfig audioConfig = new AudioConfig();

    WorldloomBuilder(WorldloomConfig config) {
        this.config = config;
    }

    public WorldloomBuilder addModule(WorldloomGameModule module) {
        if (module == null) {
            throw new IllegalArgumentException("module cannot be null");
        }
        module.registerSystems(systems);
        return this;
    }

    public SystemRegistration addSystem(EnginePhase phase, BaseSystem system) {
        return systems.add(phase, system);
    }

    public WorldloomBuilder configureAudio(AudioConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("audio config cannot be null");
        }
        audioConfig = config;
        return this;
    }

    public WorldloomBuilder configureLighting(
        AmbientLightTimeSource timeSource,
        AmbientLightConfig ambientConfig,
        TopDownShadowConfig topDownShadowConfig) {
        return configureLighting(timeSource, ambientConfig, topDownShadowConfig,
            new SunLightConfig());
    }

    public WorldloomBuilder configureLighting(
        AmbientLightTimeSource timeSource,
        AmbientLightConfig ambientConfig,
        TopDownShadowConfig topDownShadowConfig,
        SunLightConfig sunConfig) {
        if (timeSource == null || ambientConfig == null
            || topDownShadowConfig == null || sunConfig == null) {
            throw new IllegalArgumentException("lighting configuration cannot contain null");
        }
        lightTimeSource = timeSource;
        ambientLightConfig = ambientConfig;
        shadowConfig = topDownShadowConfig;
        sunLightConfig = sunConfig;
        return this;
    }

    public WorldloomBuilder addShaderTileLayer(TileLayerShaderConfig shaderConfig) {
        if (shaderConfig == null) {
            throw new IllegalArgumentException("shader config cannot be null");
        }
        tileLayerShaders.add(shaderConfig);
        return this;
    }

    /** 创建运行世界；实体加载失败时回收已创建世界，并保留原始失败原因。 */
    public WorldloomEngine build() {
        // 安装地图系统前必须已经选择新游戏或读档会话。
        GameSnapshotManager.getInstance().getCurrentMap();
        WorldConfigurationBuilder worldBuilder = new WorldConfigurationBuilder();
        ArtemisInstaller.install(
            worldBuilder,
            config,
            systems,
            audioConfig,
            lightTimeSource,
            ambientLightConfig,
            shadowConfig,
            sunLightConfig,
            tileLayerShaders);
        World world = new World(worldBuilder.build());
        return new WorldloomEngine(world);
    }
}
