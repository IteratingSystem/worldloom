package org.worldloom.system;

import com.artemis.annotations.All;
import net.mostlyoriginal.api.plugin.extendedcomponentmapper.M;
import org.worldloom.component.PointLight;
import org.worldloom.component.Pos;

/**
 * 更新Box2D Lights传统点光源。
 */
@All({Pos.class, PointLight.class})
public class PointLightSystem extends ViewedMapIteratingSystem {
    private LightSystem lightSystem;
    private M<Pos> mPos;
    private M<PointLight> mPointLight;

    /** 仅创建观察图的灯光，已离图的灯光停用但保留对象以便返回时恢复。 */
    @Override
    protected void processViewed(int entityId) {
        if (lightSystem.rayHandler == null) {
            return;
        }

        PointLight pointLight = mPointLight.get(entityId);
        if (pointLight.light == null) {
            pointLight.light = new box2dLight.PointLight(
                lightSystem.rayHandler,
                pointLight.rays,
                pointLight.color,
                pointLight.distance,
                pointLight.offsetX,
                pointLight.offsetY);
        }

        Pos pos = mPos.get(entityId);
        pointLight.light.setActive(pointLight.onOff);
        pointLight.light.setPosition(
            pos.x + pointLight.offsetX,
            pos.y + pointLight.offsetY);
    }
    /** 离开观察图后立即停用旧灯光，不能仅跳过更新而让光源继续渲染。 */
    @Override protected void processOutsideView(int entityId) {
        PointLight pointLight = mPointLight.get(entityId);
        if (pointLight.light != null) pointLight.light.setActive(false);
    }
}
