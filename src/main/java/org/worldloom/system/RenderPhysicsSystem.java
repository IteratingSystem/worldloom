package org.worldloom.system;

import com.artemis.BaseSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.utils.Logger;


/**
 * @Auther WenLong
 * @Date 2025/2/12 15:54
 * @Description 渲染物理系统(debug)
 **/
public class RenderPhysicsSystem extends BaseSystem {
    private Box2DDebugRenderer debugRenderer;
    private CameraSystem cameraSystem;
    private B2dSystem b2DSystem;
    /** 仅在实际绘制调试碰撞时创建GL资源，普通会话不占用调试网格或着色器。 */
    @Override
    protected void processSystem() {
        if (Gdx.app.getLogLevel() == Logger.DEBUG){
            if (debugRenderer == null) debugRenderer = new Box2DDebugRenderer();
            debugRenderer.render(b2DSystem.box2DWorld,cameraSystem.camera.combined);
        }
    }

    /** 调试资源由当前世界独占，关闭世界时释放且允许重复清理。 */
    @Override protected void dispose() {
        if (debugRenderer != null) { debugRenderer.dispose(); debugRenderer = null; }
    }
}
