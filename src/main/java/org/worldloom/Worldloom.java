package org.worldloom;

import org.worldloom.manager.ReflectionManager;
import org.worldloom.manager.map.GameSnapshotManager;
import org.worldloom.manager.map.MapManager;

/** Worldloom 的应用级入口。 */
public final class Worldloom {
    private static WorldloomConfig config;

    private Worldloom() {
    }

    /** 在创建 Worldloom 页面或加载引擎资源前设置应用配置。 */
    public static void configure(WorldloomConfig applicationConfig) {
        if (applicationConfig == null) {
            throw new IllegalArgumentException("applicationConfig cannot be null");
        }
        config = applicationConfig;
    }

    public static WorldloomConfig config() {
        if (config == null) {
            throw new IllegalStateException("Worldloom is not configured");
        }
        return config;
    }

    /** 注册游戏项目的反射根包。 */
    public static void setGameRootClass(Class<?> rootClass) {
        ReflectionManager.setRootClass(rootClass);
    }

    /** 资源加载完成后，根据配置建立地图和实体原型。 */
    public static void initializeMaps() {
        WorldloomConfig current = config();
        MapManager.init(current.getEntityLayers(), current.getPhysicsLayers(),current.getHiddenLayers());
    }

    /** 从 Tiled 地图原型建立新会话。 */
    public static void startNewGame() {
        GameSnapshotManager.getInstance().startNewGame(config().getInitialMap());
    }

    /** 从 JSON 存档建立会话。 */
    public static void loadGame(String saveJson) {
        GameSnapshotManager.getInstance().loadSaveJson(saveJson);
    }

    /** 创建当前会话的引擎运行实例构建器。 */
    public static WorldloomBuilder engineBuilder() {
        return new WorldloomBuilder(config());
    }
}
