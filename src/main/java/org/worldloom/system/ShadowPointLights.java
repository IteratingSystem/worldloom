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

    /** 只统计观察图开启的光源，后台地图不会触发无意义的灯光合成。 */
    boolean hasViewedLights() {
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
    void synchronize(com.badlogic.gdx.physics.box2d.World physics) {
        pointRayHandler.setWorld(physics);
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
    void render(com.badlogic.gdx.graphics.OrthographicCamera camera, java.util.function.Consumer<org.worldloom.light.TopDownShadowLight> composite) {
        IntBag entities = pointLightSubscription.getEntities();
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
