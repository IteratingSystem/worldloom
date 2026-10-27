package org.worldloom.system;

import box2dLight.RayHandler;
import com.artemis.BaseSystem;
import com.badlogic.gdx.graphics.Color;

/**
 * @author: WenLong
 * @date: 2026/1/14
 * @description: 光线系统, 使用box2dLight
 */

public class LightSystem extends BaseSystem {

    private CameraSystem cameraSystem;
    private B2dSystem b2dSystem;
    private PixelPerfectRenderSystem pixelPerfectRenderSystem;

    public RayHandler rayHandler;
    private boolean enable;
    public LightSystem(boolean enable){
        this.enable = enable;
    }
    @Override
    protected void initialize() {
        super.initialize();

        if(!enable){
            return;
        }
        //光线相关
        RayHandler.useDiffuseLight(true);
        rayHandler = new RayHandler(b2dSystem.box2DWorld);
        rayHandler.setBlurNum(3);
        Color color = new Color(0.5f, 0.5f, 0.5f, 1.0f);
        rayHandler.setAmbientLight(color);
    }

    /** 使用观察图的物理世界计算遮挡，灯光对象继续复用原有处理器。 */
    @Override
    protected void processSystem() {
        if(!enable){
            return;
        }

        if (rayHandler == null) {
            return;
        }
        rayHandler.setWorld(b2dSystem.box2DWorld);
        rayHandler.setCombinedMatrix(cameraSystem.camera);
        rayHandler.update();
        rayHandler.prepareRender();
        // box2dlights结束内部光照缓冲后会绑定默认屏幕，需要恢复世界目标。
        pixelPerfectRenderSystem.resumeWorldTarget();
        rayHandler.renderOnly();
    }
    public void setAmbientLight(Color color){
        if(!enable){
            return;
        }

        rayHandler.setAmbientLight(color);
    }
    @Override
    protected void dispose() {
        super.dispose();
        if (rayHandler != null) {
            rayHandler.dispose();
        }
    }


}
