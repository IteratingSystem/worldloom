package org.worldloom.system;

import box2dLight.RayHandler;
import com.artemis.World;
import com.artemis.Aspect;
import com.artemis.EntitySubscription;
import com.artemis.ComponentMapper;
import com.artemis.utils.IntBag;
import org.worldloom.component.Pos;
import org.worldloom.component.Inert;
import org.worldloom.render.MapVisibility;

/** 观察图点光的生命周期与逐灯绘制；物理世界及RayHandler均由外部系统持有。 */
final class ShadowPointLights {
    private final World world;
    private final float worldScale;
    private final RayHandler pointRayHandler;
    private final ComponentMapper<Pos> mPos;
    private final ComponentMapper<org.worldloom.component.TopDownPointLight> mTopDownPointLight;
    private final EntitySubscription pointLightSubscription;

    private final IntBag viewedLights = new IntBag();
    private final com.badlogic.gdx.math.Rectangle viewBounds = new com.badlogic.gdx.math.Rectangle();

    /** 只订阅现有实体，后台地图灯光不创建渲染资源。 */
    ShadowPointLights(World world, float scale, RayHandler handler) {
        this.world = world;
        worldScale = scale;
        pointRayHandler = handler;
        mPos = world.getMapper(Pos.class);
        mTopDownPointLight = world.getMapper(org.worldloom.component.TopDownPointLight.class);
        pointLightSubscription = world.getAspectSubscriptionManager().get(
            Aspect.all(Pos.class, org.worldloom.component.TopDownPointLight.class).exclude(Inert.class));
    }

    /** 复用本帧已筛选的灯光；完全不能影响视域的灯不触发合成。 */
    boolean hasViewedLights() { return !viewedLights.isEmpty(); }

    /** 按完整视锥与一纹素对齐余量构造保守范围，保留屏外可照入的光源。 */
    private void updateView(com.badlogic.gdx.graphics.OrthographicCamera camera, int width, int height) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (var corner : camera.frustum.planePoints) {
            minX = Math.min(minX, corner.x); minY = Math.min(minY, corner.y);
            maxX = Math.max(maxX, corner.x); maxY = Math.max(maxY, corner.y);
        }
        float padX = Math.abs(camera.viewportWidth * camera.zoom) / Math.max(1, width);
        float padY = Math.abs(camera.viewportHeight * camera.zoom) / Math.max(1, height);
        viewBounds.set(minX - padX, minY - padY, maxX - minX + 2 * padX, maxY - minY + 2 * padY);
    }

    /** 圆形照明范围与保守视域相交才需要逐灯更新；非有限配置保留旧处理路径。 */
    private boolean affectsView(float x, float y, float radius) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(radius)) return true;
        float dx = Math.max(viewBounds.x - x, Math.max(0f, x - viewBounds.x - viewBounds.width));
        float dy = Math.max(viewBounds.y - y, Math.max(0f, y - viewBounds.y - viewBounds.height));
        return (double) dx * dx + (double) dy * dy <= (double) radius * radius;
    }

    /** 将遮挡世界绑定到观察图，仅为本图创建点光源，避免后台地图污染光照。 */
    void synchronize(com.badlogic.gdx.physics.box2d.World physics,
                     com.badlogic.gdx.graphics.OrthographicCamera camera, int width, int height) {
        updateView(camera, width, height);
        viewedLights.clear();
        pointRayHandler.setWorld(physics);
        IntBag entities = pointLightSubscription.getEntities();
        int[] ids = entities.getData();
        for (int i = 0; i < entities.size(); i++) {
            int entityId = ids[i];
            Pos pos = mPos.get(entityId);
            org.worldloom.component.TopDownPointLight component =
                mTopDownPointLight.get(entityId);
            float x = worldScale * (pos.x + component.offsetX);
            float y = worldScale * (pos.y + component.offsetY);
            float radius = component.light == null ? Math.max(0.01f, component.distance * worldScale)
                : component.light.getDistance();
            if (!MapVisibility.inViewedMap(world, entityId) || !component.onOff || !affectsView(x, y, radius)) {
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
            viewedLights.add(entityId);
        }
    }

    /** 合成观察图所有灯光的影响，屏外光源仍可照亮视域内区域。 */
    void render(com.badlogic.gdx.graphics.OrthographicCamera camera, java.util.function.Consumer<org.worldloom.light.TopDownShadowLight> composite) {
        IntBag entities = viewedLights;
        int[] ids = entities.getData();
        pointRayHandler.setCombinedMatrix(camera);
        try {
            for (int i = 0; i < entities.size(); i++) {
                org.worldloom.component.TopDownPointLight component =
                    mTopDownPointLight.get(ids[i]);
                if (!MapVisibility.inViewedMap(world, ids[i]) || !component.onOff || component.light == null) {
                    continue;
                }
                component.light.setActive(true);
                pointRayHandler.update();
                pointRayHandler.prepareRender();
                try {
                    composite.accept(component.light);
                } finally {
                    component.light.setActive(false);
                }
            }
        } finally {
            for (int i = 0; i < entities.size(); i++) {
                org.worldloom.component.TopDownPointLight component =
                    mTopDownPointLight.get(ids[i]);
                if (component.light != null) {
                    component.light.setActive(component.onOff && MapVisibility.inViewedMap(world, ids[i]));
                }
            }
        }
    }
}
