package org.worldloom.system;

import box2dLight.RayHandler;
import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ObjectMap;
import net.mostlyoriginal.api.plugin.extendedcomponentmapper.M;
import org.worldloom.component.Inert;
import org.worldloom.component.Pos;
import org.worldloom.component.Render;
import org.worldloom.component.SoarHeight;
import org.worldloom.component.TopDownShadow;
import org.worldloom.component.ZIndex;
import org.worldloom.light.SunLightConfig;
import org.worldloom.light.TopDownShadowConfig;
import org.worldloom.light.TopDownShadowLight;
import org.worldloom.light.TopDownSunLight;
import org.worldloom.light.OpaqueRegionCache;
import org.worldloom.light.SunShadowGeometry;
import org.worldloom.light.ShadowVolumeGeometry;
import org.worldloom.manager.ShaderManager;
import org.worldloom.render.MapVisibility;

import java.nio.IntBuffer;

/**
 * 为带有TopDownShadow组件的精灵生成并合成俯视角阴影。
 */
public class TopDownShadowSystem extends BaseSystem {
    private static final String TAG = TopDownShadowSystem.class.getSimpleName();
    private static final int SHADOW_SEGMENTS = 32;
    private static final int MAX_VOLUME_LAYERS = 16;
    private static final int SUN_RIBBON_MAX_VERTICES = 8;
    private static final float SUN_RIBBON_CHECK_TEXELS = 6f;
    private static final float SUN_RIBBON_SWITCH_TEXELS = 2.5f;
    private static final int GL_MAX_BLEND_EQUATION = 0x8008;
    private static final String SHADER_PATH = "shader/topdown/";

    private final float worldScale;
    private final TopDownShadowConfig config;
    private final SunLightConfig sunConfig;
    private final IntArray sortedShadowEntities = new IntArray();
    private final Vector2 lightDirection = new Vector2();
    private final Vector2 lightPosition = new Vector2();
    private final Vector3 lightAxisU = new Vector3();
    private final Vector3 lightAxisV = new Vector3();
    private final Vector3 lightAxisDepth = new Vector3();
    private final IntBuffer glStateBuffer = BufferUtils.newIntBuffer(1);
    private final int[] previousTextureBindings = new int[4];
    private final OpaqueRegionCache opaqueRegions = new OpaqueRegionCache();
    private final ObjectMap<ShadowVolumeGeometry, Mesh> volumeMeshes = new ObjectMap<>();
    private final SunShadowGeometry sunGeometry;
    private final com.badlogic.gdx.utils.Array<com.badlogic.gdx.utils.Disposable> renderResources = new com.badlogic.gdx.utils.Array<>();
    private final com.badlogic.gdx.utils.Array<FrameBuffer> frameBuffers = new com.badlogic.gdx.utils.Array<>();

    private B2dSystem b2dSystem;
    private CameraSystem cameraSystem;
    private PixelPerfectRenderSystem pixelPerfectRenderSystem;
    private TiledMapSystem tiledMapSystem;
    private M<Pos> mPos;
    private M<Render> mRender;
    private M<ZIndex> mZIndex;
    private M<SoarHeight> mSoarHeight;
    private M<TopDownShadow> mTopDownShadow;
    private M<org.worldloom.component.TopDownPointLight> mTopDownPointLight;

    private EntitySubscription shadowSubscription;
    private EntitySubscription pointLightSubscription;
    private RayHandler pointRayHandler;
    private TopDownSunLight sunLight;
    private SpriteBatch spriteBatch;
    private Mesh screenQuad;
    private Mesh sunProjectedShadowMesh;
    private Mesh projectedShadowMesh;
    private final Mesh[] volumeSliceMeshes = new Mesh[MAX_VOLUME_LAYERS + 1];
    private Mesh sunShadowRibbonMesh;
    private FrameBuffer heightMapSource;
    private FrameBuffer heightMap;
    private FrameBuffer depthDownsampleBuffer;
    private FrameBuffer entityMask;
    private FrameBuffer groundShadowSource;
    private FrameBuffer groundShadowMask;
    private FrameBuffer receiverShadowMask;
    private FrameBuffer sunShadowMap;
    private ShaderProgram heightMapShader;
    private ShaderProgram entityMaskShader;
    private ShaderProgram projectedShadowShader;
    private ShaderProgram sunShadowRibbonShader;
    private ShaderProgram receiverShader;
    private ShaderProgram lightSpaceCasterShader;
    private ShaderProgram lightSpaceGroundShader;
    private ShaderProgram sunCompositeShader;
    private ShaderProgram pointCompositeShader;
    private ShaderProgram depthDownsampleShader;
    private ShaderProgram depthExpandShader;
    private int bufferWidth;
    private int bufferHeight;
    private int depthBufferHeight;
    private float shadowTime;
    private float lightUvMinX;
    private float lightUvMinY;
    private float lightUvSizeX;
    private float lightUvSizeY;
    private float lightDepthMin;
    private float lightDepthMax;
    private float lightDepthBias;
    private float shadowCameraOriginalX;
    private float shadowCameraOriginalY;
    private float shadowUvOffsetX;
    private float shadowUvOffsetY;
    private boolean shadowCameraActive;
    public TopDownShadowSystem(float worldScale, TopDownShadowConfig config) {
        this(worldScale, config, new SunLightConfig());
    }

    public TopDownShadowSystem(float worldScale, TopDownShadowConfig config,
                               SunLightConfig sunConfig) {
        if (worldScale <= 0f) {
            throw new IllegalArgumentException("worldScale must be greater than zero");
        }
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }
        if (sunConfig == null) {
            throw new IllegalArgumentException("sunConfig cannot be null");
        }
        this.worldScale = worldScale;
        sunGeometry = new SunShadowGeometry(worldScale);
        this.config = config;
        this.sunConfig = sunConfig;
    }

    @Override
    protected void initialize() {
        shadowSubscription = world.getAspectSubscriptionManager().get(
            Aspect.all(Render.class, Pos.class, ZIndex.class, TopDownShadow.class)
                .exclude(Inert.class));
        pointLightSubscription = world.getAspectSubscriptionManager().get(
            Aspect.all(Pos.class, org.worldloom.component.TopDownPointLight.class)
                .exclude(Inert.class));

        RayHandler.useDiffuseLight(true);
        try {
            pointRayHandler = own(new RayHandler(b2dSystem.box2DWorld));
            pointRayHandler.setAmbientLight(0f);
            pointRayHandler.setBlur(true);
            pointRayHandler.setBlurNum(1);
            pointRayHandler.setLightMapRendering(false);
            sunLight = new TopDownSunLight(pointRayHandler, 0f,
                config.getHeightRange());
            sunLight.setSunBearingDegree(sunConfig.getReferenceBearingDegree());
            sunLight.setElevationDegree(sunConfig.getMinimumElevationDegree());

            spriteBatch = own(new SpriteBatch());
            screenQuad = own(org.worldloom.render.RenderResources.createScreenQuad());
            sunProjectedShadowMesh = own(createProjectedShadowMesh(1));
            projectedShadowMesh = own(createProjectedShadowMesh(SHADOW_SEGMENTS));
            sunShadowRibbonMesh = own(createSunShadowRibbonMesh());
            ShaderProgram.pedantic = false;
            ShaderManager shaderManager = ShaderManager.getInstance();
            heightMapShader = compileShader(
                shaderManager.getVertexContext(SHADER_PATH + "sprite"),
                shaderManager.getFragmentContext(SHADER_PATH + "height_map"),
                "Height map");
            entityMaskShader = compileShader(
                shaderManager.getVertexContext(SHADER_PATH + "sprite"),
                shaderManager.getFragmentContext(SHADER_PATH + "entity_mask"),
                "Entity mask");
            projectedShadowShader = compileShader(
                shaderManager.getVertexContext(SHADER_PATH + "projected_shadow"),
                shaderManager.getFragmentContext(SHADER_PATH + "projected_shadow"),
                "Projected shadow");
            sunShadowRibbonShader = compileShader(
                shaderManager.getVertexContext(SHADER_PATH + "shadow_ribbon"),
                shaderManager.getFragmentContext(SHADER_PATH + "shadow_ribbon"),
                "Sun shadow ribbon");
            receiverShader = compileShader(
                shaderManager.getVertexContext(SHADER_PATH + "receiver"),
                shaderManager.getFragmentContext(SHADER_PATH + "receiver"),
                "Receiver shadow");
            lightSpaceCasterShader = compileShader(
                shaderManager.getVertexContext(SHADER_PATH + "light_space_caster"),
                shaderManager.getFragmentContext(SHADER_PATH + "light_space_caster"),
                "Light-space caster");
            lightSpaceGroundShader = compileShader(
                shaderManager.getVertexContext(SHADER_PATH + "screen"),
                shaderManager.getFragmentContext(SHADER_PATH + "light_space_ground"),
                "Light-space ground");
            sunCompositeShader = compileShader(
                shaderManager.getVertexContext(SHADER_PATH + "screen"),
                shaderManager.getFragmentContext(SHADER_PATH + "sun_composite"),
                "Sun composite");
            pointCompositeShader = compileShader(
                shaderManager.getVertexContext(SHADER_PATH + "screen"),
                shaderManager.getFragmentContext(SHADER_PATH + "point_composite"),
                "Point composite");
            depthDownsampleShader = compileShader(
                shaderManager.getVertexContext(SHADER_PATH + "screen"),
                shaderManager.getFragmentContext(SHADER_PATH + "depth_downsample"),
                "Shadow depth downsample");
            depthExpandShader = compileShader(
                shaderManager.getVertexContext(SHADER_PATH + "screen"),
                shaderManager.getFragmentContext(SHADER_PATH + "depth_expand"),
                "Shadow depth expansion");
            resizeBuffersIfNeeded();
            validateRenderOrder();
            Gdx.app.log(TAG, "Top-down shadow system initialized");
        } catch (RuntimeException | Error failure) {
            try { dispose(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    /** 检查阴影合成是否位于世界渲染之后。 */
    private void validateRenderOrder() {
        int shadowIndex = -1;
        int renderIndex = -1;
        for (int i = 0; i < world.getSystems().size(); i++) {
            BaseSystem system = world.getSystems().get(i);
            if (system == this) {
                shadowIndex = i;
            } else if (system instanceof RenderBatchingSystem) {
                renderIndex = i;
            }
        }
        if (renderIndex >= 0 && shadowIndex >= 0 && shadowIndex < renderIndex) {
            Gdx.app.error(TAG,
                "TopDownShadowSystem must be registered with LOWEST priority "
                    + "after the world render systems");
        }
    }

    /** 只合成观察图阴影与灯光，保留本图屏外投影者的影响。 */
    @Override
    protected void processSystem() {
        resizeBuffersIfNeeded();
        shadowTime = (shadowTime + Math.min(world.getDelta(), 1f / 15f)) % 1000f;
        synchronizePointLights();
        boolean hasPointLights = hasViewedPointLights();
        boolean sunEnabled = config.isSunEnabled(tiledMapSystem.getCurrent());
        if ((!sunEnabled && !hasPointLights)
            || (shadowSubscription.getEntities().isEmpty() && !hasPointLights)) {
            return;
        }

        int previousActiveTexture = captureTextureBindings();
        try {
            beginShadowCamera();
            sortShadowEntities();
            renderEntityMask();
            renderHeightMap();

            if (sunEnabled) {
                renderSunShadowMap();
                renderShadowMasks(sunLight);
                compositeSunShadow();
            }
        } finally {
            endShadowCamera();
            restoreTextureBindings(previousActiveTexture);
        }
    }

    /** 在环境光合成完成后绘制点光源，避免环境光再次压暗灯光。 */
    void renderPointLightsAfterAmbient() {
        if (!hasViewedPointLights()) {
            return;
        }

        int previousActiveTexture = captureTextureBindings();
        try {
            beginShadowCamera();
            renderPointLights();
        } finally {
            endShadowCamera();
            restoreTextureBindings(previousActiveTexture);
        }
    }

    /**
     * 将阴影视图对齐到阴影缓冲自身的纹素网格。
     * 主世界仍按屏幕像素移动，二者的差值在最终合成时补偿。
     */
    private void beginShadowCamera() {
        if (shadowCameraActive || bufferWidth <= 0 || bufferHeight <= 0) {
            return;
        }
        shadowCameraOriginalX = cameraSystem.camera.position.x;
        shadowCameraOriginalY = cameraSystem.camera.position.y;
        float viewWidth = cameraSystem.camera.viewportWidth
            * cameraSystem.camera.zoom;
        float viewHeight = cameraSystem.camera.viewportHeight
            * cameraSystem.camera.zoom;
        float texelX = viewWidth / bufferWidth;
        float texelY = viewHeight / bufferHeight;
        float shadowX = snapDown(shadowCameraOriginalX, texelX);
        float shadowY = snapDown(shadowCameraOriginalY, texelY);
        shadowUvOffsetX = (shadowCameraOriginalX - shadowX) / viewWidth;
        shadowUvOffsetY = (shadowCameraOriginalY - shadowY) / viewHeight;
        cameraSystem.camera.position.set(
            shadowX, shadowY, cameraSystem.camera.position.z);
        cameraSystem.camera.update();
        shadowCameraActive = true;
    }

    /** 恢复像素相机本帧用于世界渲染的位置。 */
    private void endShadowCamera() {
        if (!shadowCameraActive) {
            return;
        }
        cameraSystem.camera.position.set(
            shadowCameraOriginalX, shadowCameraOriginalY,
            cameraSystem.camera.position.z);
        cameraSystem.camera.update();
        shadowCameraActive = false;
    }

    private float snapDown(float value, float step) {
        if (step <= 0f) {
            return value;
        }
        return (float) Math.floor(value / step) * step;
    }

    /**
     * 保存本系统会占用的纹理单元，避免污染海洋等其它多纹理Shader。
     */
    private int captureTextureBindings() {
        int activeTexture = getGlInteger(GL20.GL_ACTIVE_TEXTURE);
        for (int unit = 0; unit < previousTextureBindings.length; unit++) {
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0 + unit);
            previousTextureBindings[unit] =
                getGlInteger(GL20.GL_TEXTURE_BINDING_2D);
        }
        Gdx.gl.glActiveTexture(activeTexture);
        return activeTexture;
    }

    private void restoreTextureBindings(int activeTexture) {
        for (int unit = 0; unit < previousTextureBindings.length; unit++) {
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0 + unit);
            Gdx.gl.glBindTexture(
                GL20.GL_TEXTURE_2D, previousTextureBindings[unit]);
        }
        Gdx.gl.glActiveTexture(activeTexture);
    }

    private int getGlInteger(int parameter) {
        glStateBuffer.clear();
        Gdx.gl.glGetIntegerv(parameter, glStateBuffer);
        return glStateBuffer.get(0);
    }

    /** 只统计观察图开启的光源，后台地图不会触发无意义的灯光合成。 */
    private boolean hasViewedPointLights() {
        IntBag entities = pointLightSubscription.getEntities();
        for (int i = 0; i < entities.size(); i++) {
            int entityId = entities.get(i);
            if (MapVisibility.inViewedMap(world, entityId) && mTopDownPointLight.get(entityId).onOff) {
                return true;
            }
        }
        return false;
    }

    /** 将遮挡世界绑定到观察图，仅为本图创建点光源，避免后台地图污染光照。 */
    private void synchronizePointLights() {
        pointRayHandler.setWorld(b2dSystem.box2DWorld);
        IntBag entities = pointLightSubscription.getEntities();
        int[] ids = entities.getData();
        for (int i = 0; i < entities.size(); i++) {
            int entityId = ids[i];
            Pos pos = mPos.get(entityId);
            org.worldloom.component.TopDownPointLight component =
                mTopDownPointLight.get(entityId);
            if (!MapVisibility.inViewedMap(world, entityId)) {
                if (component.light != null) {
                    component.light.setActive(false);
                }
                continue;
            }
            if (component.light == null) {
                component.light = new org.worldloom.light.TopDownPointLight(
                    pointRayHandler, Math.max(3, component.rays), component.color,
                    Math.max(0.01f, component.distance * worldScale),
                    worldScale * (pos.x + component.offsetX),
                    worldScale * (pos.y + component.offsetY),
                    Math.max(0.01f, component.height * worldScale));
            }
            component.light.setPosition(
                worldScale * (pos.x + component.offsetX),
                worldScale * (pos.y + component.offsetY));
            component.light.setActive(false);
        }
    }

    /** 合成观察图所有灯光的影响，屏外光源仍可照亮视域内区域。 */
    private void renderPointLights() {
        IntBag entities = pointLightSubscription.getEntities();
        int[] ids = entities.getData();
        pointRayHandler.setCombinedMatrix(cameraSystem.camera);
        for (int i = 0; i < entities.size(); i++) {
            org.worldloom.component.TopDownPointLight component =
                mTopDownPointLight.get(ids[i]);
            if (!MapVisibility.inViewedMap(world, ids[i]) || !component.onOff || component.light == null) {
                continue;
            }
            component.light.setActive(true);
            pointRayHandler.update();
            pointRayHandler.prepareRender();
            renderShadowMasks(component.light);
            compositePointLight();
            component.light.setActive(false);
        }
        for (int i = 0; i < entities.size(); i++) {
            org.worldloom.component.TopDownPointLight component =
                mTopDownPointLight.get(ids[i]);
            if (component.light != null) {
                component.light.setActive(component.onOff && MapVisibility.inViewedMap(world, ids[i]));
            }
        }
    }

    /** 只排序观察图投影者，保留屏外投影者以允许其阴影进入相机画面。 */
    private void sortShadowEntities() {
        sortedShadowEntities.clear();
        IntBag entities = shadowSubscription.getEntities();
        int[] ids = entities.getData();
        for (int i = 0; i < entities.size(); i++) {
            int entityId = ids[i];
            if (MapVisibility.inViewedMap(world, entityId)) {
                sortedShadowEntities.add(entityId);
            }
        }
        for (int i = 1; i < sortedShadowEntities.size; i++) {
            int entityId = sortedShadowEntities.get(i);
            float footY = getFootY(entityId);
            int insertAt = i;
            while (insertAt > 0
                && getFootY(sortedShadowEntities.get(insertAt - 1)) < footY) {
                sortedShadowEntities.set(
                    insertAt, sortedShadowEntities.get(insertAt - 1));
                insertAt--;
            }
            sortedShadowEntities.set(insertAt, entityId);
        }
    }

    private void renderEntityMask() {
        entityMask.begin();
        clearBuffer();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendEquation(GL_MAX_BLEND_EQUATION);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
        spriteBatch.setProjectionMatrix(cameraSystem.camera.combined);
        spriteBatch.setShader(entityMaskShader);
        spriteBatch.begin();
        entityMaskShader.setUniformi("u_texture", 0);
        for (int i = 0; i < sortedShadowEntities.size; i++) {
            drawEntity(sortedShadowEntities.get(i));
        }
        spriteBatch.end();
        spriteBatch.setShader(null);
        Gdx.gl.glBlendEquation(GL20.GL_FUNC_ADD);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        entityMask.end();
    }

    private void renderHeightMap() {
        heightMapSource.begin();
        clearBuffer();
        spriteBatch.setProjectionMatrix(cameraSystem.camera.combined);
        spriteBatch.setShader(heightMapShader);
        spriteBatch.disableBlending();
        spriteBatch.begin();
        heightMapShader.setUniformi("u_texture", 0);
        heightMapShader.setUniformf("u_heightRange", config.getHeightRange());
        for (int i = 0; i < sortedShadowEntities.size; i++) {
            int entityId = sortedShadowEntities.get(i);
            heightMapShader.setUniformf("u_footY", getFootY(entityId));
            heightMapShader.setUniformf(
                "u_shadowDepth", getMaximumShadowDepth(entityId));
            drawEntity(entityId);
            spriteBatch.flush();
        }
        spriteBatch.end();
        spriteBatch.enableBlending();
        spriteBatch.setShader(null);
        heightMapSource.end();
        expandDepth(heightMapSource, heightMap);
    }

    /** 从太阳视角渲染带深度和实体ID的立体剪影。 */
    private void renderSunShadowMap() {
        updateSunLightSpace();
        sunShadowMap.begin();
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClearDepthf(1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LESS);
        Gdx.gl.glDepthMask(true);
        lightSpaceCasterShader.bind();
        lightSpaceCasterShader.setUniformi("u_texture", 0);
        setLightSpaceUniforms(lightSpaceCasterShader);
        boolean culling = Gdx.gl.glIsEnabled(GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        try {
            for (int i = 0; i < sortedShadowEntities.size; i++) {
                renderVolumeEntity(sortedShadowEntities.get(i));
            }
        } finally {
            if (culling) Gdx.gl.glEnable(GL20.GL_CULL_FACE);
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
            sunShadowMap.end();
        }
    }

    /**
     * 建立太阳的正交坐标系。U/V用于阴影图坐标，Depth用于比较遮挡先后。
     */
    private void updateSunLightSpace() {
        sunLight.getShadowDirection(lightDirection).nor();
        float sine = MathUtils.sinDeg(sunLight.getElevationDegree());
        float cosine = MathUtils.cosDeg(sunLight.getElevationDegree());
        lightAxisU.set(-lightDirection.y, lightDirection.x, 0f).nor();
        lightAxisV.set(lightDirection.x * sine,
            lightDirection.y * sine, cosine).nor();
        lightAxisDepth.set(lightDirection.x * cosine,
            lightDirection.y * cosine, -sine).nor();

        float halfWidth = cameraSystem.camera.viewportWidth
            * cameraSystem.camera.zoom * 0.5f;
        float halfHeight = cameraSystem.camera.viewportHeight
            * cameraSystem.camera.zoom * 0.5f;
        float centerX = cameraSystem.camera.position.x;
        float centerY = cameraSystem.camera.position.y;
        float minimumU = Float.POSITIVE_INFINITY;
        float maximumU = Float.NEGATIVE_INFINITY;
        float minimumV = Float.POSITIVE_INFINITY;
        float maximumV = Float.NEGATIVE_INFINITY;
        float minimumDepth = Float.POSITIVE_INFINITY;
        float maximumDepth = Float.NEGATIVE_INFINITY;
        for (int xIndex = 0; xIndex < 2; xIndex++) {
            float x = centerX + (xIndex == 0 ? -halfWidth : halfWidth);
            for (int yIndex = 0; yIndex < 2; yIndex++) {
                float y = centerY + (yIndex == 0 ? -halfHeight : halfHeight);
                for (int zIndex = 0; zIndex < 2; zIndex++) {
                    float z = zIndex == 0 ? 0f : config.getHeightRange();
                    float u = x * lightAxisU.x + y * lightAxisU.y;
                    float v = x * lightAxisV.x + y * lightAxisV.y
                        + z * lightAxisV.z;
                    float depth = x * lightAxisDepth.x
                        + y * lightAxisDepth.y + z * lightAxisDepth.z;
                    minimumU = Math.min(minimumU, u);
                    maximumU = Math.max(maximumU, u);
                    minimumV = Math.min(minimumV, v);
                    maximumV = Math.max(maximumV, v);
                    minimumDepth = Math.min(minimumDepth, depth);
                    maximumDepth = Math.max(maximumDepth, depth);
                }
            }
        }
        float worldPerTexel = Math.max(
            cameraSystem.camera.viewportWidth * cameraSystem.camera.zoom
                / Math.max(1, bufferWidth),
            cameraSystem.camera.viewportHeight * cameraSystem.camera.zoom
                / Math.max(1, bufferHeight));
        float uvPadding = Math.max(2f, worldPerTexel * 2f);
        lightUvMinX = minimumU - uvPadding;
        lightUvMinY = minimumV - uvPadding;
        lightUvSizeX = Math.max(1f, maximumU - minimumU + uvPadding * 2f);
        lightUvSizeY = Math.max(1f, maximumV - minimumV + uvPadding * 2f);
        float rayReach = config.getHeightRange() / Math.max(sine, 0.05f);
        lightDepthMin = minimumDepth - rayReach - uvPadding;
        lightDepthMax = maximumDepth + rayReach + uvPadding;
        lightDepthBias = Math.max(0.35f, worldPerTexel * 0.75f)
            / (lightDepthMax - lightDepthMin);
    }

    private void renderVolumeEntity(int entityId) {
        Render render = mRender.get(entityId);
        Pos pos = mPos.get(entityId);
        if (!isRenderable(render)) {
            return;
        }
        float soarHeight = getSoarHeight(entityId);
        if (render.textureSheets != null && !render.textureSheets.isEmpty()) {
            for (int i = 0; i < render.textureSheets.size; i++) {
                TextureRegion region = render.textureSheets.get(i);
                if (region != null) {
                    renderVolumeRegion(entityId, render, pos, region,
                        soarHeight + i * render.sheetOffset, getFootY(entityId));
                }
            }
            return;
        }
        renderVolumeRegion(entityId, render, pos, render.keyframe,
            soarHeight, getFootY(entityId));
    }

    /** 将闭合轮廓投影体写入原深度图，地面与对象受影共用同一遮挡结果。 */
    private void renderVolumeRegion(int entityId, Render render, Pos pos,
                                    TextureRegion region, float extraY,
                                    float footY) {
        ShadowVolumeGeometry geometry = opaqueRegions.getVolume(region);
        if (geometry != null && geometry.vertexCount() == 0) return;
        float drawX = worldScale * (pos.x + render.offsetX);
        float drawY = worldScale * (pos.y + render.offsetY + extraY);
        float depth = getShadowDepth(entityId, region);
        lightSpaceCasterShader.setUniformf("u_drawPosition", drawX, drawY);
        lightSpaceCasterShader.setUniformf(
            "u_origin", render.originX, render.originY);
        lightSpaceCasterShader.setUniformf(
            "u_size", region.getRegionWidth(), region.getRegionHeight());
        lightSpaceCasterShader.setUniformf("u_scale",
            worldScale * render.scaleWidth, worldScale * render.scaleHeight);
        lightSpaceCasterShader.setUniformf("u_rotation", render.rotation);
        lightSpaceCasterShader.setUniformf("u_footY", footY);
        lightSpaceCasterShader.setUniformf("u_spriteFlip",
            render.flipX ? 1f : 0f, render.flipY ? 1f : 0f);
        lightSpaceCasterShader.setUniformf("u_uvBottomLeft", region.getU(), region.getV2());
        lightSpaceCasterShader.setUniformf("u_uvTopRight", region.getU2(), region.getV());
        setEntityIdUniform(lightSpaceCasterShader, entityId);
        lightSpaceCasterShader.setUniformf("u_shadowDepth", depth);
        region.getTexture().bind(0);
        Mesh mesh;
        if (geometry == null) {
            // 压缩或特殊纹理无法提取轮廓时沿用原切片路径，不制造实心矩形阴影。
            float worldPerTexel = cameraSystem.camera.viewportHeight
                * cameraSystem.camera.zoom / Math.max(1, bufferHeight);
            int layers = MathUtils.clamp(
                MathUtils.ceil(depth / Math.max(worldPerTexel, 0.0001f)) + 1, 2, MAX_VOLUME_LAYERS);
            if (volumeSliceMeshes[layers] == null)
                volumeSliceMeshes[layers] = own(createVolumeSliceMesh(layers));
            mesh = volumeSliceMeshes[layers];
        } else {
            mesh = volumeMeshes.get(geometry);
            if (mesh == null) {
                mesh = own(new Mesh(true, geometry.vertexCount(), 0,
                    new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
                    new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord")));
                mesh.setVertices(geometry.vertices());
                volumeMeshes.put(geometry, mesh);
            }
        }
        mesh.render(lightSpaceCasterShader, GL20.GL_TRIANGLES);
    }

    private void setLightSpaceUniforms(ShaderProgram shader) {
        shader.setUniformf("u_lightAxisU", lightAxisU);
        shader.setUniformf("u_lightAxisV", lightAxisV);
        shader.setUniformf("u_lightAxisDepth", lightAxisDepth);
        shader.setUniformf("u_lightUvMin", lightUvMinX, lightUvMinY);
        shader.setUniformf("u_lightUvSize", lightUvSizeX, lightUvSizeY);
        shader.setUniformf(
            "u_lightDepthRange", lightDepthMin, lightDepthMax);
        if (shader.hasUniform("u_depthBias")) {
            shader.setUniformf("u_depthBias", lightDepthBias);
        }
    }

    /** 两个8位通道共同保存实体ID，零值保留给空像素。 */
    private void setEntityIdUniform(ShaderProgram shader, int entityId) {
        int encoded = (entityId + 1) & 0xffff;
        if (encoded == 0) {
            encoded = 1;
        }
        shader.setUniformf("u_entityId",
            (encoded & 0xff) / 255f,
            ((encoded >>> 8) & 0xff) / 255f);
    }

    private void renderShadowMasks(TopDownShadowLight light) {
        renderGroundShadowMask(light);
        renderReceiverShadowMask(light);
    }

    private void renderGroundShadowMask(TopDownShadowLight light) {
        if (light.isDirectional()) {
            renderSunGroundShadowMask();
            return;
        }
        groundShadowSource.begin();
        clearBuffer();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendEquation(GL_MAX_BLEND_EQUATION);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
        projectedShadowShader.bind();
        projectedShadowShader.setUniformi("u_texture", 0);
        projectedShadowShader.setUniformMatrix(
            "u_projTrans", cameraSystem.camera.combined);
        projectedShadowShader.setUniformf(
            "u_heightRange", config.getHeightRange());
        setLightUniforms(projectedShadowShader, light);
        renderProjectedCasters(light);
        Gdx.gl.glBlendEquation(GL20.GL_FUNC_ADD);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        groundShadowSource.end();
        expandDepth(groundShadowSource, groundShadowMask);
    }

    private void renderSunGroundShadowMask() {
        groundShadowMask.begin();
        clearBuffer();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        sunShadowMap.getColorBufferTexture().bind(0);
        lightSpaceGroundShader.bind();
        lightSpaceGroundShader.setUniformi("u_shadowMap", 0);
        lightSpaceGroundShader.setUniformMatrix(
            "u_invProjTrans", cameraSystem.camera.invProjectionView);
        setLightSpaceUniforms(lightSpaceGroundShader);
        screenQuad.render(lightSpaceGroundShader, GL20.GL_TRIANGLE_FAN);
        groundShadowMask.end();
    }

    private void renderProjectedCasters(TopDownShadowLight light) {
        for (int i = 0; i < sortedShadowEntities.size; i++) {
            int entityId = sortedShadowEntities.get(i);
            if (!isCasterInRange(entityId, light)) {
                continue;
            }
            renderProjectedEntity(entityId, light.isDirectional());
        }
    }

    private void renderReceiverShadowMask(TopDownShadowLight light) {
        receiverShadowMask.begin();
        clearBuffer();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendEquation(GL_MAX_BLEND_EQUATION);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
        spriteBatch.setProjectionMatrix(cameraSystem.camera.combined);
        spriteBatch.setShader(receiverShader);
        spriteBatch.begin();
        receiverShader.setUniformi("u_texture", 0);
        receiverShader.setUniformi("u_heightMap", 1);
        receiverShader.setUniformi("u_sunShadowMap", 2);
        receiverShader.setUniformMatrix(
            "u_projTrans", cameraSystem.camera.combined);
        receiverShader.setUniformf("u_heightRange", config.getHeightRange());
        receiverShader.setUniformf("u_time", shadowTime);
        setLightUniforms(receiverShader, light);
        setLightSpaceUniforms(receiverShader);
        heightMap.getColorBufferTexture().bind(1);
        sunShadowMap.getColorBufferTexture().bind(2);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        for (int i = 0; i < sortedShadowEntities.size; i++) {
            int entityId = sortedShadowEntities.get(i);
            receiverShader.setUniformf("u_footY", getFootY(entityId));
            setEntityIdUniform(receiverShader, entityId);
            drawEntity(entityId);
            spriteBatch.flush();
        }
        spriteBatch.end();
        spriteBatch.setShader(null);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        Gdx.gl.glBlendEquation(GL20.GL_FUNC_ADD);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        receiverShadowMask.end();
    }

    private void setLightUniforms(ShaderProgram shader,
                                  TopDownShadowLight light) {
        shader.setUniformf("u_pointMode", light.isDirectional() ? 0f : 1f);
        shader.setUniformf(
            "u_shadowDirection", light.getShadowDirection(lightDirection));
        if (shader.hasUniform("u_sunShadowLengthScale")) {
            shader.setUniformf("u_sunShadowLengthScale",
                light.getShadowLengthScale());
        }
        light.getShadowPosition(lightPosition);
        shader.setUniformf("u_lightPosition", lightPosition);
        shader.setUniformf("u_lightHeight", light.getShadowHeight());
        if (shader.hasUniform("u_lightRange")) {
            shader.setUniformf("u_lightRange", light.getShadowRange());
        }
    }

    private void renderProjectedEntity(int entityId, boolean directional) {
        Render render = mRender.get(entityId);
        Pos pos = mPos.get(entityId);
        if (!isRenderable(render)) {
            return;
        }
        float soarHeight = getSoarHeight(entityId);
        if (render.textureSheets != null && !render.textureSheets.isEmpty()) {
            for (int i = 0; i < render.textureSheets.size; i++) {
                TextureRegion region = render.textureSheets.get(i);
                if (region != null) {
                    renderProjectedRegion(entityId, render, pos, region,
                        soarHeight + i * render.sheetOffset, getFootY(entityId),
                        directional);
                }
            }
            return;
        }
        renderProjectedRegion(
            entityId, render, pos, render.keyframe, soarHeight, getFootY(entityId),
            directional);
    }

    private void renderProjectedRegion(int entityId, Render render, Pos pos,
                                       TextureRegion region, float extraY,
                                       float footY, boolean directional) {
        if (directional && renderSunShadowRibbonIfNeeded(
            entityId, render, pos, region, extraY, footY)) {
            return;
        }
        projectedShadowShader.bind();
        Texture texture = region.getTexture();
        float drawX = worldScale * (pos.x + render.offsetX);
        float drawY = worldScale * (pos.y + render.offsetY + extraY);
        float scaleX = worldScale * render.scaleWidth;
        float scaleY = worldScale * render.scaleHeight;
        projectedShadowShader.setUniformf("u_drawPosition", drawX, drawY);
        projectedShadowShader.setUniformf(
            "u_origin", render.originX, render.originY);
        projectedShadowShader.setUniformf(
            "u_size", region.getRegionWidth(), region.getRegionHeight());
        projectedShadowShader.setUniformf("u_scale", scaleX, scaleY);
        projectedShadowShader.setUniformf("u_rotation", render.rotation);
        projectedShadowShader.setUniformf("u_footY", footY);
        projectedShadowShader.setUniformf(
            "u_shadowDepth", getShadowDepth(entityId, region));
        setTextureCoordinates(projectedShadowShader, render, region);
        texture.bind(0);
        Mesh mesh = directional ? sunProjectedShadowMesh : projectedShadowMesh;
        mesh.render(
            projectedShadowShader, GL20.GL_TRIANGLE_STRIP);
    }

    /**
     * 太阳接近平行于地面时，普通纹理投影会退化为低于一个像素的线。
     * 此时用真实不透明边界生成连续凸阴影带，避免光栅化后的分段与消失。
     */
    private boolean renderSunShadowRibbonIfNeeded(
        int entityId, Render render, Pos pos, TextureRegion region,
        float extraY, float footY) {
        float fullSpan = getProjectedVerticalSpanTexels(
            render, pos, region, extraY, footY,
            0f, region.getRegionWidth(), 0f, region.getRegionHeight());
        if (fullSpan >= SUN_RIBBON_CHECK_TEXELS) {
            return false;
        }

        OpaqueRegionCache.Bounds opaqueBounds = opaqueRegions.get(region);
        float opaqueSpan = getProjectedVerticalSpanTexels(
            render, pos, region, extraY, footY,
            opaqueBounds.left(), opaqueBounds.right(),
            opaqueBounds.bottom(), opaqueBounds.top());
        if (opaqueSpan >= SUN_RIBBON_SWITCH_TEXELS) {
            return false;
        }

        int hullSize = sunGeometry.buildRibbon(
            render, pos, region, extraY, footY,
            opaqueBounds.left(), opaqueBounds.right(),
            opaqueBounds.bottom(), opaqueBounds.top(),
            getShadowDepth(entityId, region), sunLight.getShadowDirection(lightDirection),
            sunLight.getShadowLengthScale());
        if (hullSize < 3) {
            return false;
        }
        sunShadowRibbonMesh.setVertices(sunGeometry.vertices(), 0, hullSize * 2);
        sunShadowRibbonShader.bind();
        sunShadowRibbonShader.setUniformMatrix(
            "u_projTrans", cameraSystem.camera.combined);
        sunShadowRibbonMesh.render(
            sunShadowRibbonShader, GL20.GL_TRIANGLE_FAN, 0, hullSize);
        return true;
    }

    /** 纹素阈值属于渲染缓冲策略，几何计算只返回世界跨度。 */
    private float getProjectedVerticalSpanTexels(
        Render render, Pos pos, TextureRegion region, float extraY,
        float footY, float left, float right, float bottom, float top) {
        float span = sunGeometry.verticalSpan(render, pos, region, extraY, footY,
            left, right, bottom, top, sunLight.getShadowDirection(lightDirection), sunLight.getShadowLengthScale());
        float visibleWorldHeight = cameraSystem.camera.viewportHeight * cameraSystem.camera.zoom;
        return span / Math.max(visibleWorldHeight / groundShadowSource.getHeight(), 0.0001f);
    }

    private void setTextureCoordinates(ShaderProgram shader, Render render,
                                       TextureRegion region) {
        Texture texture = region.getTexture();
        float inverseWidth = 1f / texture.getWidth();
        float inverseHeight = 1f / texture.getHeight();
        float left = region.getRegionX() * inverseWidth;
        float right = (region.getRegionX() + region.getRegionWidth()) * inverseWidth;
        float bottom = (region.getRegionY() + region.getRegionHeight())
            * inverseHeight;
        float top = region.getRegionY() * inverseHeight;
        if (render.flipX) {
            float swap = left;
            left = right;
            right = swap;
        }
        if (render.flipY) {
            float swap = bottom;
            bottom = top;
            top = swap;
        }
        shader.setUniformf("u_uvBottomLeft", left, bottom);
        shader.setUniformf("u_uvTopRight", right, top);
    }

    private boolean isCasterInRange(int entityId, TopDownShadowLight light) {
        Render render = mRender.get(entityId);
        if (!isRenderable(render)) {
            return false;
        }
        if (light.isDirectional()) {
            return true;
        }
        light.getShadowPosition(lightPosition);
        Pos pos = mPos.get(entityId);
        float centerX = worldScale * (pos.x + render.offsetX)
            + render.keyframe.getRegionWidth() * worldScale
            * render.scaleWidth * 0.5f;
        return lightPosition.dst(centerX, getFootY(entityId))
            <= light.getShadowRange() + getMaximumHeight(entityId);
    }

    private float getMaximumHeight(int entityId) {
        Render render = mRender.get(entityId);
        float drawBottom = worldScale
            * (mPos.get(entityId).y + render.offsetY + getSoarHeight(entityId));
        float drawHeight = render.keyframe.getRegionHeight()
            * Math.abs(worldScale * render.scaleHeight);
        return Math.max(0f, drawBottom + drawHeight - getFootY(entityId));
    }

    private float getMaximumShadowDepth(int entityId) {
        Render render = mRender.get(entityId);
        if (!isRenderable(render)) {
            return 0f;
        }
        TopDownShadow shadow = mTopDownShadow.get(entityId);
        if (shadow.depth > 0f) {
            return shadow.depth * worldScale;
        }

        float maximumWidth = render.keyframe.getRegionWidth();
        if (render.textureSheets != null) {
            for (int i = 0; i < render.textureSheets.size; i++) {
                TextureRegion region = render.textureSheets.get(i);
                if (region != null) {
                    maximumWidth = Math.max(maximumWidth, region.getRegionWidth());
                }
            }
        }
        return maximumWidth * Math.abs(worldScale * render.scaleWidth) / 5f;
    }

    private float getShadowDepth(int entityId, TextureRegion region) {
        TopDownShadow shadow = mTopDownShadow.get(entityId);
        if (shadow.depth > 0f) {
            return shadow.depth * worldScale;
        }
        return region.getRegionWidth()
            * Math.abs(worldScale * mRender.get(entityId).scaleWidth) / 5f;
    }

    private void expandDepth(FrameBuffer source, FrameBuffer target) {
        downsampleDepth(source);
        target.begin();
        clearBuffer();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        depthDownsampleBuffer.getColorBufferTexture().bind(0);
        depthExpandShader.bind();
        depthExpandShader.setUniformi("u_source", 0);
        depthExpandShader.setUniformf("u_heightRange", config.getHeightRange());
        depthExpandShader.setUniformf("u_texelY", 1f / target.getHeight());
        depthExpandShader.setUniformf("u_worldPerTexelY",
            cameraSystem.camera.viewportHeight * cameraSystem.camera.zoom
                / target.getHeight());
        screenQuad.render(depthExpandShader, GL20.GL_TRIANGLE_FAN);
        target.end();
    }

    private void downsampleDepth(FrameBuffer source) {
        depthDownsampleBuffer.begin();
        clearBuffer();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        source.getColorBufferTexture().bind(0);
        depthDownsampleShader.bind();
        depthDownsampleShader.setUniformi("u_source", 0);
        depthDownsampleShader.setUniformf(
            "u_sourceTexelY", 1f / source.getHeight());
        screenQuad.render(depthDownsampleShader, GL20.GL_TRIANGLE_FAN);
        depthDownsampleBuffer.end();
    }

    private void drawEntity(int entityId) {
        Render render = mRender.get(entityId);
        if (!isRenderable(render)) {
            return;
        }
        Pos pos = mPos.get(entityId);
        float soarHeight = getSoarHeight(entityId);
        if (render.textureSheets != null && !render.textureSheets.isEmpty()) {
            for (int i = 0; i < render.textureSheets.size; i++) {
                TextureRegion region = render.textureSheets.get(i);
                if (region != null) {
                    drawRegion(render, pos, region,
                        soarHeight + i * render.sheetOffset);
                }
            }
            return;
        }
        drawRegion(render, pos, render.keyframe, soarHeight);
    }

    private void drawRegion(Render render, Pos pos, TextureRegion region,
                            float extraY) {
        spriteBatch.draw(region.getTexture(),
            worldScale * (pos.x + render.offsetX),
            worldScale * (pos.y + render.offsetY + extraY),
            render.originX, render.originY,
            region.getRegionWidth(), region.getRegionHeight(),
            worldScale * render.scaleWidth,
            worldScale * render.scaleHeight,
            render.rotation,
            region.getRegionX(), region.getRegionY(),
            region.getRegionWidth(), region.getRegionHeight(),
            render.flipX, render.flipY);
    }

    private boolean isRenderable(Render render) {
        return render != null && render.visible && render.keyframe != null
            && render.keyframe.getTexture() != null;
    }

    private float getFootY(int entityId) {
        Pos pos = mPos.get(entityId);
        Render render = mRender.get(entityId);
        ZIndex zIndex = mZIndex.get(entityId);
        return worldScale * (pos.y + render.offsetY
            + getSoarHeight(entityId) + zIndex.offset);
    }

    private float getSoarHeight(int entityId) {
        return mSoarHeight.has(entityId) ? mSoarHeight.get(entityId).height : 0f;
    }

    private void compositeSunShadow() {
        pixelPerfectRenderSystem.resumeWorldTarget();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        bindCompositeMasks(sunCompositeShader);
        sunCompositeShader.setUniformf(
            "u_shadowOpacity", config.getSunShadowOpacity());
        screenQuad.render(sunCompositeShader, GL20.GL_TRIANGLE_FAN);
        finishComposite();
    }

    private void compositePointLight() {
        pixelPerfectRenderSystem.resumeWorldTarget();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
        receiverShadowMask.getColorBufferTexture().bind(3);
        entityMask.getColorBufferTexture().bind(2);
        groundShadowMask.getColorBufferTexture().bind(1);
        pointRayHandler.getLightMapTexture().bind(0);
        pointCompositeShader.bind();
        pointCompositeShader.setUniformi("u_lightMap", 0);
        pointCompositeShader.setUniformi("u_groundShadow", 1);
        pointCompositeShader.setUniformi("u_entityMask", 2);
        pointCompositeShader.setUniformi("u_receiverShadow", 3);
        setCompositeCommonUniforms(pointCompositeShader);
        screenQuad.render(pointCompositeShader, GL20.GL_TRIANGLE_FAN);
        finishComposite();
    }

    private void bindCompositeMasks(ShaderProgram shader) {
        receiverShadowMask.getColorBufferTexture().bind(2);
        entityMask.getColorBufferTexture().bind(1);
        groundShadowMask.getColorBufferTexture().bind(0);
        shader.bind();
        shader.setUniformi("u_groundShadow", 0);
        shader.setUniformi("u_entityMask", 1);
        shader.setUniformi("u_receiverShadow", 2);
        setCompositeCommonUniforms(shader);
    }

    private void setCompositeCommonUniforms(ShaderProgram shader) {
        shader.setUniformf("u_shadowTexel",
            1f / bufferWidth, 1f / bufferHeight);
        shader.setUniformf("u_shadowUvOffset",
            shadowUvOffsetX, shadowUvOffsetY);
        shader.setUniformf("u_time", shadowTime);
    }

    private void finishComposite() {
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void resizeBuffersIfNeeded() {
        int width = Math.max(1, Math.round(
            pixelPerfectRenderSystem.getRenderWidth()
                * config.getResolutionScale()));
        int height = Math.max(1, Math.round(
            pixelPerfectRenderSystem.getRenderHeight()
                * config.getResolutionScale()));
        if (width == bufferWidth && height == bufferHeight) {
            return;
        }
        disposeBuffers();
        bufferWidth = width;
        bufferHeight = height;
        depthBufferHeight = Math.max(1, (height + 1) / 2);
        pointRayHandler.resizeFBO(width, height);
        heightMapSource = createBuffer(Texture.TextureFilter.Nearest);
        heightMap = createDepthBuffer(Texture.TextureFilter.Linear);
        depthDownsampleBuffer = createDepthBuffer(Texture.TextureFilter.Nearest);
        entityMask = createBuffer(Texture.TextureFilter.Nearest);
        groundShadowSource = createBuffer(Texture.TextureFilter.Nearest);
        groundShadowMask = createDepthBuffer(Texture.TextureFilter.Linear);
        receiverShadowMask = createBuffer(Texture.TextureFilter.Linear);
        sunShadowMap = new FrameBuffer(
            Pixmap.Format.RGBA8888, bufferWidth, bufferHeight, true);
        frameBuffers.add(sunShadowMap);
        sunShadowMap.getColorBufferTexture().setFilter(
            Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
    }

    private FrameBuffer createBuffer(Texture.TextureFilter filter) {
        return createBuffer(bufferHeight, filter);
    }

    private FrameBuffer createDepthBuffer(Texture.TextureFilter filter) {
        return createBuffer(depthBufferHeight, filter);
    }

    private FrameBuffer createBuffer(int height, Texture.TextureFilter filter) {
        FrameBuffer buffer = new FrameBuffer(
            Pixmap.Format.RGBA8888, bufferWidth, height, false);
        frameBuffers.add(buffer);
        buffer.getColorBufferTexture().setFilter(filter, filter);
        return buffer;
    }

    /** 缓冲尺寸变化或初始化失败时释放已经创建的部分，不依赖某个哨兵字段是否赋值。 */
    private void disposeBuffers() {
        while (frameBuffers.notEmpty()) frameBuffers.pop().dispose();
        heightMapSource = heightMap = depthDownsampleBuffer = entityMask = null;
        groundShadowSource = groundShadowMask = receiverShadowMask = sunShadowMap = null;
    }

    private void clearBuffer() {
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }



    private Mesh createProjectedShadowMesh(int segments) {
        int vertexCount = (segments + 1) * 2;
        Mesh mesh = new Mesh(true, vertexCount, 0,
            new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"),
            new VertexAttribute(
                VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord"));
        float[] vertices = new float[vertexCount * 4];
        int offset = 0;
        for (int i = 0; i <= segments; i++) {
            float vertical = i / (float) segments;
            vertices[offset++] = 0f;
            vertices[offset++] = vertical;
            vertices[offset++] = 0f;
            vertices[offset++] = vertical;
            vertices[offset++] = 1f;
            vertices[offset++] = vertical;
            vertices[offset++] = 1f;
            vertices[offset++] = vertical;
        }
        mesh.setVertices(vertices);
        return mesh;
    }

    private Mesh createVolumeSliceMesh(int layers) {
        int vertexCount = layers * 6;
        Mesh mesh = new Mesh(true, vertexCount, 0,
            new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
            new VertexAttribute(
                VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord"));
        float[] vertices = new float[vertexCount * 5];
        int offset = 0;
        for (int layer = 0; layer < layers; layer++) {
            float depthRatio = layer / (float) (layers - 1);
            offset = setVolumeVertex(
                vertices, offset, 0f, 0f, depthRatio, 0f, 0f);
            offset = setVolumeVertex(
                vertices, offset, 1f, 0f, depthRatio, 1f, 0f);
            offset = setVolumeVertex(
                vertices, offset, 1f, 1f, depthRatio, 1f, 1f);
            offset = setVolumeVertex(
                vertices, offset, 0f, 0f, depthRatio, 0f, 0f);
            offset = setVolumeVertex(
                vertices, offset, 1f, 1f, depthRatio, 1f, 1f);
            offset = setVolumeVertex(
                vertices, offset, 0f, 1f, depthRatio, 0f, 1f);
        }
        mesh.setVertices(vertices);
        return mesh;
    }

    private int setVolumeVertex(float[] vertices, int offset,
                                float x, float y, float depth,
                                float u, float v) {
        vertices[offset++] = x;
        vertices[offset++] = y;
        vertices[offset++] = depth;
        vertices[offset++] = u;
        vertices[offset++] = v;
        return offset;
    }

    private Mesh createSunShadowRibbonMesh() {
        return new Mesh(false, SUN_RIBBON_MAX_VERTICES, 0,
            new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"));
    }

    private ShaderProgram compileShader(String vertex, String fragment,
                                        String name) {
        if (vertex == null || fragment == null) {
            throw new IllegalStateException(name + " shader source is missing");
        }
        ShaderProgram shader = new ShaderProgram(vertex, fragment);
        if (!shader.isCompiled()) {
            Gdx.app.error(TAG, name + " shader compile failed: " + shader.getLog());
            shader.dispose();
            throw new IllegalStateException(name + " shader compile failed");
        }
        return own(shader);
    }

    public TopDownSunLight getSunLight() {
        return sunLight;
    }

    /** 登记系统独占的固定资源，释放顺序与创建顺序相反，FBO另随尺寸独立管理。 */
    private <T extends com.badlogic.gdx.utils.Disposable> T own(T resource) {
        renderResources.add(resource);
        return resource;
    }

    /** 退出与初始化异常共用释放入口，重复调用不再释放同一资源，不触碰共享纹理。 */
    @Override
    protected void dispose() {
        volumeMeshes.clear();
        java.util.Arrays.fill(volumeSliceMeshes, null);
        opaqueRegions.clear();
        try { disposeBuffers(); }
        finally { while (renderResources.notEmpty()) renderResources.pop().dispose(); }
    }
}
