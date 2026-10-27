package org.worldloom;

import com.artemis.Component;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import org.worldloom.map.MapRole;
import org.worldloom.camera.PixelPerfectCameraConfig;
import org.worldloom.component.Pos;
import org.worldloom.component.Render;
import org.worldloom.component.ZIndex;

/**
 * Worldloom 应用级配置。
 *
 * <p>配置在构建后保持不变。运行时相机缩放、地图切换等状态应通过对应系统或事件修改。</p>
 */
public final class WorldloomConfig {
    private final ObjectMap<String, String> entityLayers;
    private final ObjectMap<String, String[]> physicsLayers;
    private final ObjectMap<String, String[]> hiddenLayers;
    private final ObjectMap<String, String> navigationRoadLayers;
    private final Array<Class<? extends Component>> autoComponents;
    private final Array<String> residentMaps;
    private final ObjectSet<String> residentMapIds;
    private final ObjectMap<String, MapRole> mapRoles;
    private final int uiWidth;
    private final int uiHeight;
    private final float uiZoom;
    private final float gameWidth;
    private final float gameHeight;
    private final float cameraZoom;
    private final float worldScale;
    private final float gravityX;
    private final float gravityY;
    private final boolean allowPhysicsSleep;
    private final boolean combineTileShapes;
    private final boolean legacyBox2dLightsEnabled;
    private final float navigationRoadCost;
    private final float navigationOffRoadCost;
    private final PixelPerfectCameraConfig pixelPerfectCameraConfig;
    private final String initialMap;
    private final String skinPath;

    private WorldloomConfig(Builder builder) {
        entityLayers = copyStringMap(builder.entityLayers);
        physicsLayers = copyArrayMap(builder.physicsLayers);
        hiddenLayers = copyArrayMap(builder.hiddenLayers);
        navigationRoadLayers = copyStringMap(builder.navigationRoadLayers);
        autoComponents = new Array<>(builder.autoComponents);
        residentMaps = new Array<>(builder.residentMaps);
        residentMapIds = new ObjectSet<>();
        residentMapIds.addAll(residentMaps);
        mapRoles = new ObjectMap<>();
        mapRoles.putAll(builder.mapRoles);
        uiWidth = builder.uiWidth;
        uiHeight = builder.uiHeight;
        uiZoom = builder.uiZoom;
        gameWidth = builder.gameWidth;
        gameHeight = builder.gameHeight;
        cameraZoom = builder.cameraZoom;
        worldScale = builder.worldScale;
        gravityX = builder.gravityX;
        gravityY = builder.gravityY;
        allowPhysicsSleep = builder.allowPhysicsSleep;
        combineTileShapes = builder.combineTileShapes;
        legacyBox2dLightsEnabled = builder.legacyBox2dLightsEnabled;
        navigationRoadCost = builder.navigationRoadCost;
        navigationOffRoadCost = builder.navigationOffRoadCost;
        pixelPerfectCameraConfig = builder.pixelPerfectCameraConfig;
        initialMap = builder.initialMap;
        skinPath = builder.skinPath;
    }

    public static Builder builder() { return new Builder(); }

    public ObjectMap<String, String> getEntityLayers() { return copyStringMap(entityLayers); }
    public ObjectMap<String, String[]> getPhysicsLayers() { return copyArrayMap(physicsLayers); }
    /** 返回隐藏层配置副本，例如交给地图系统后隐藏 island 的 SEA。 */
    public ObjectMap<String, String[]> getHiddenLayers() { return copyArrayMap(hiddenLayers); }
    public ObjectMap<String, String> getNavigationRoadLayers() {
        return copyStringMap(navigationRoadLayers);
    }
    public Array<Class<? extends Component>> getAutoComponents() { return new Array<>(autoComponents); }
    /** 返回正式常驻地图的副本；空集合保留原单地图加载方式，不包括物品和预制体模板。 */
    public Array<String> getResidentMaps() { return new Array<>(residentMaps); }

    /** 查询常驻模式开关，不在逐实体物理查询中复制地图目录。 */
    public boolean isResidentMode() { return residentMaps.notEmpty(); }

    /** 零分配查询正式常驻图，不能用素材来源图代替实体的运行归属。 */
    public boolean isResidentMap(String mapId) { return mapId != null && residentMapIds.contains(mapId); }

    /** 未指定用途的地图默认为常规世界，模板用途由应用配置显式声明。 */
    public MapRole getMapRole(String mapId) { return mapRoles.get(mapId, MapRole.WORLD); }

    /** 在创建实体或物理资源前验证目标图，模板只能作为来源，不能作为实例的运行归属。 */
    public void requireRuntimeMap(String mapId) {
        if (mapId == null || mapId.isBlank() || getMapRole(mapId) == MapRole.TEMPLATE
            || (isResidentMode() && !isResidentMap(mapId)))
            throw new IllegalArgumentException("不是可运行的世界地图：" + mapId);
    }
    public int getUiWidth() { return uiWidth; }
    public int getUiHeight() { return uiHeight; }
    public float getUiZoom() { return uiZoom; }
    public float getGameWidth() { return gameWidth; }
    public float getGameHeight() { return gameHeight; }
    public float getCameraZoom() { return cameraZoom; }
    public float getWorldScale() { return worldScale; }
    public float getGravityX() { return gravityX; }
    public float getGravityY() { return gravityY; }
    public boolean isPhysicsSleepAllowed() { return allowPhysicsSleep; }
    public boolean isCombineTileShapes() { return combineTileShapes; }
    public boolean isLegacyBox2dLightsEnabled() { return legacyBox2dLightsEnabled; }
    public float getNavigationRoadCost() { return navigationRoadCost; }
    public float getNavigationOffRoadCost() { return navigationOffRoadCost; }
    public PixelPerfectCameraConfig getPixelPerfectCameraConfig() {
        return pixelPerfectCameraConfig;
    }
    public String getInitialMap() { return initialMap; }
    public String getSkinPath() { return skinPath; }

    private static ObjectMap<String, String> copyStringMap(ObjectMap<String, String> source) {
        ObjectMap<String, String> result = new ObjectMap<>();
        result.putAll(source);
        return result;
    }

    private static ObjectMap<String, String[]> copyArrayMap(ObjectMap<String, String[]> source) {
        ObjectMap<String, String[]> result = new ObjectMap<>();
        for (ObjectMap.Entry<String, String[]> entry : source) {
            result.put(entry.key, entry.value.clone());
        }
        return result;
    }

    public static final class Builder {
        private final ObjectMap<String, String> entityLayers = new ObjectMap<>();
        private final ObjectMap<String, String[]> physicsLayers = new ObjectMap<>();
        private final ObjectMap<String, String[]> hiddenLayers = new ObjectMap<>();
        private final ObjectMap<String, String> navigationRoadLayers = new ObjectMap<>();
        private final Array<Class<? extends Component>> autoComponents = new Array<>();
        private final Array<String> residentMaps = new Array<>();
        private final ObjectMap<String, MapRole> mapRoles = new ObjectMap<>();
        private boolean residentMapsByRole;
        private int uiWidth = 640;
        private int uiHeight = 480;
        private float uiZoom = 1f;
        private float gameWidth = 640f;
        private float gameHeight = 480f;
        private float cameraZoom = 1f;
        private float worldScale = 1f;
        private float gravityX;
        private float gravityY = -9.8f;
        private boolean allowPhysicsSleep;
        private boolean combineTileShapes = true;
        private boolean legacyBox2dLightsEnabled;
        private float navigationRoadCost = 1f;
        private float navigationOffRoadCost = 4f;
        private PixelPerfectCameraConfig pixelPerfectCameraConfig =
            PixelPerfectCameraConfig.disabled();
        private String initialMap = "defaultMap";
        private String skinPath = "skin/main.json";

        private Builder() {
            autoComponents.add(Pos.class);
            autoComponents.add(Render.class);
            autoComponents.add(ZIndex.class);
        }

        public Builder ui(int width, int height, float zoom) {
            uiWidth = width;
            uiHeight = height;
            uiZoom = zoom;
            return this;
        }

        public Builder game(float width, float height) {
            gameWidth = width;
            gameHeight = height;
            return this;
        }

        public Builder cameraZoom(float zoom) { cameraZoom = zoom; return this; }
        public Builder worldScale(float scale) { worldScale = scale; return this; }

        public Builder gravity(float x, float y) {
            gravityX = x;
            gravityY = y;
            return this;
        }

        public Builder allowPhysicsSleep(boolean allow) { allowPhysicsSleep = allow; return this; }
        public Builder combineTileShapes(boolean combine) { combineTileShapes = combine; return this; }
        public Builder legacyBox2dLights(boolean enabled) { legacyBox2dLightsEnabled = enabled; return this; }
        public Builder pixelPerfectCamera(PixelPerfectCameraConfig config) {
            if (config == null) {
                throw new IllegalArgumentException(
                    "pixel perfect camera config cannot be null");
            }
            pixelPerfectCameraConfig = config;
            return this;
        }
        public Builder initialMap(String mapName) { initialMap = mapName; return this; }
        /** 指定共用一个ECS、独立物理世界的正式地图，必须同时配置各图实体层。 */
        public Builder residentMaps(String... mapNames) {
            residentMapsByRole = false;
            residentMaps.clear();
            for (String name : mapNames) {
                if (name == null || name.isBlank() || residentMaps.contains(name, false)) {
                    throw new IllegalArgumentException("常驻地图名称不能为空或重复");
                }
                residentMaps.add(name);
            }
            return this;
        }
        /** 按用途推导常驻目录：所有配置实体层的WORLD地图参与运行，TEMPLATE只保留数据。 */
        public Builder residentMapsByRole() {
            residentMapsByRole = true;
            return this;
        }

        /** 声明地图用途；未声明的地图为WORLD，在build时校验目录，不依赖调用顺序。 */
        public Builder mapRole(String mapName, MapRole role) {
            if (mapName == null || mapName.isBlank() || role == null)
                throw new IllegalArgumentException("地图名称与用途不能为空");
            mapRoles.put(mapName, role);
            return this;
        }

        public Builder skin(String path) { skinPath = path; return this; }

        public Builder entityLayer(String mapName, String layerName) {
            entityLayers.put(mapName, layerName);
            return this;
        }

        public Builder physicsLayers(String mapName, String... layerNames) {
            physicsLayers.put(mapName, layerNames.clone());
            return this;
        }

        /** 例如 hiddenLayers("island", "SEA") 会在地图加载后隐藏普通渲染层。 */
        public Builder hiddenLayers(String mapName, String... layerNames) {
            hiddenLayers.put(mapName, layerNames.clone());
            return this;
        }

        /** 指定某张地图中用于降低寻路代价的道路瓦片层。 */
        public Builder navigationRoadLayer(String mapName, String layerName) {
            navigationRoadLayers.put(mapName, layerName);
            return this;
        }

        /** 设置道路与非道路区域的相对寻路代价。 */
        public Builder navigationCosts(float roadCost, float offRoadCost) {
            navigationRoadCost = roadCost;
            navigationOffRoadCost = offRoadCost;
            return this;
        }

        @SafeVarargs
        public final Builder autoComponents(Class<? extends Component>... componentTypes) {
            autoComponents.clear();
            autoComponents.addAll(componentTypes);
            return this;
        }

        public WorldloomConfig build() {
            if (uiWidth <= 0 || uiHeight <= 0 || gameWidth <= 0f || gameHeight <= 0f) {
                throw new IllegalStateException("viewport dimensions must be positive");
            }
            if (uiZoom <= 0f || cameraZoom <= 0f || worldScale <= 0f) {
                throw new IllegalStateException("zoom and world scale must be positive");
            }
            if (navigationRoadCost <= 0f
                || navigationOffRoadCost < navigationRoadCost) {
                throw new IllegalStateException(
                    "navigation costs must satisfy 0 < road cost <= off-road cost");
            }
            if (initialMap == null || initialMap.isBlank()) {
                throw new IllegalStateException("initial map cannot be blank");
            }
            for (ObjectMap.Entry<String, MapRole> entry : mapRoles) {
                if (!entityLayers.containsKey(entry.key))
                    throw new IllegalStateException("声明用途的地图缺少实体层：" + entry.key);
                if (entry.value == MapRole.TEMPLATE && (entry.key.equals(initialMap)
                    || physicsLayers.containsKey(entry.key) || navigationRoadLayers.containsKey(entry.key)))
                    throw new IllegalStateException("模板地图不能作为初始场景或配置世界物理/导航：" + entry.key);
            }
            if (residentMapsByRole) {
                residentMaps.clear();
                for (String map : entityLayers.keys())
                    if (mapRoles.get(map, MapRole.WORLD) == MapRole.WORLD) residentMaps.add(map);
            }
            for (String map : residentMaps)
                if (mapRoles.get(map, MapRole.WORLD) == MapRole.TEMPLATE)
                    throw new IllegalStateException("模板地图不能加入常驻目录：" + map);
            if ((residentMapsByRole || !residentMaps.isEmpty()) && !residentMaps.contains(initialMap, false)) {
                throw new IllegalStateException("初始地图必须属于正式常驻地图");
            }
            for (String map : residentMaps) {
                if (!entityLayers.containsKey(map)) throw new IllegalStateException("常驻地图缺少实体层：" + map);
            }
            if (skinPath == null || skinPath.isBlank()) {
                throw new IllegalStateException("skin path cannot be blank");
            }
            return new WorldloomConfig(this);
        }
    }
}
