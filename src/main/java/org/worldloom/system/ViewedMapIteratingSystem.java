package org.worldloom.system;

import com.artemis.Aspect;
import com.artemis.systems.IteratingSystem;
import org.worldloom.render.MapVisibility;

/** 纯表现系统的统一地图边界；生命、AI、动画时钟等全图逻辑不能继承此类。 */
public abstract class ViewedMapIteratingSystem extends IteratingSystem {
    /** 由具体系统的Artemis注解决定订阅，地图过滤不改变订阅及实体生命周期。 */
    protected ViewedMapIteratingSystem() { }

    /** 显式指定组件订阅，适用于不使用Artemis注解的表现系统。 */
    protected ViewedMapIteratingSystem(Aspect.Builder aspect) { super(aspect); }

    /** 每帧根据真实归属分派，不缓存可见标记，跨门后立即使用新地图。 */
    @Override protected final void process(int entityId) {
        if (MapVisibility.inViewedMap(world, entityId)) processViewed(entityId);
        else processOutsideView(entityId);
    }

    /** 更新当前观察图的表现，不应承载必须在后台推进的业务。 */
    protected abstract void processViewed(int entityId);

    /** 离图时允许停用已创建的表现资源；默认无操作，不等同实体removed。 */
    protected void processOutsideView(int entityId) { }
}
