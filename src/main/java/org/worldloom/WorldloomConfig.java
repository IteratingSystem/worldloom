package org.worldloom;

import com.artemis.Component;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
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
    private final ObjectMap<String, String> navigationRoadLayers;
    private final Array<Class<? extends Component>> autoComponents;
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
        navigationRoadLayers = copyStringMap(builder.navigationRoadLayers);
        autoComponents = new Array<>(builder.autoComponents);
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
    public ObjectMap<String, String> getNavigationRoadLayers() {
        return copyStringMap(navigationRoadLayers);
    }
    public Array<Class<? extends Component>> getAutoComponents() { return new Array<>(autoComponents); }
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
        private final ObjectMap<String, String> navigationRoadLayers = new ObjectMap<>();
        private final Array<Class<? extends Component>> autoComponents = new Array<>();
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
        public Builder skin(String path) { skinPath = path; return this; }

        public Builder entityLayer(String mapName, String layerName) {
            entityLayers.put(mapName, layerName);
            return this;
        }

        public Builder physicsLayers(String mapName, String... layerNames) {
            physicsLayers.put(mapName, layerNames.clone());
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
            if (skinPath == null || skinPath.isBlank()) {
                throw new IllegalStateException("skin path cannot be blank");
            }
            return new WorldloomConfig(this);
        }
    }
}
