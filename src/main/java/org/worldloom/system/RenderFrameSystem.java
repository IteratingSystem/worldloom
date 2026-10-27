package org.worldloom.system;

import com.artemis.annotations.All;
import com.artemis.annotations.Exclude;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Rectangle;
import net.mostlyoriginal.api.plugin.extendedcomponentmapper.M;
import net.mostlyoriginal.api.system.delegate.DeferredEntityProcessingSystem;
import net.mostlyoriginal.api.system.delegate.EntityProcessPrincipal;
import org.worldloom.component.*;
import org.worldloom.render.MapVisibility;
import org.worldloom.render.RenderGeometry;

/** 在观察地图内绘制精灵，裁剪只影响绘制，不影响动画和游戏逻辑更新。 */
@Wire
@All({Render.class, Pos.class})
@Exclude(Inert.class)
public class RenderFrameSystem extends DeferredEntityProcessingSystem {
    private RenderTiledSystem renderTiledSystem;
    private M<Render> mRender;
    private M<Pos> mPos;
    private M<ShaderComp> mShaderComp;
    private M<SoarHeight> mSoarHeight;
    private final Rectangle drawBounds = new Rectangle();
    private final float worldScale;
    private Batch batch;
    private ShaderProgram shaderProgram;

    /** 使用渲染管线与世界缩放创建精灵绘制代理。 */
    public RenderFrameSystem(EntityProcessPrincipal principal, float worldScale) {
        super(principal);
        this.worldScale = worldScale;
    }

    /** 取得当前地图渲染器共用的批次。 */
    @Override
    protected void initialize() {
        updateBatch();
    }

    /** 地图切换后重新取得地图渲染器的批次引用。 */
    private void updateBatch() {
        batch = renderTiledSystem.mapRenderer.getBatch();
    }

    /** 根据实际绘制包围盒判断视域相交，自定义着色器保守保留以免误裁顶点位移。 */
    public boolean intersectsView(int entityId, Rectangle view) {
        Render render = mRender.get(entityId);
        if (!render.visible || render.keyframe == null || render.keyframe.getTexture() == null) {
            return false;
        }
        if (mShaderComp.has(entityId)) {
            return true;
        }
        float height = mSoarHeight.has(entityId) ? mSoarHeight.get(entityId).height : 0f;
        RenderGeometry.bounds(render, mPos.get(entityId), height, worldScale, drawBounds);
        return view.x <= drawBounds.x + drawBounds.width && view.x + view.width >= drawBounds.x
            && view.y <= drawBounds.y + drawBounds.height && view.y + view.height >= drawBounds.y;
    }

    /** 应用实体着色器参数；该回调仅用于表现，不能承载游戏逻辑时钟。 */
    private void setShaderUniforms(int entityId) {
        shaderProgram = null;
        if (!mShaderComp.has(entityId)) {
            return;
        }
        ShaderComp shaderComp = mShaderComp.get(entityId);
        shaderProgram = shaderComp.shaderProgram;
        if (shaderComp.shaderUniforms != null) {
            shaderComp.shaderUniforms.setUniforms(world.getDelta());
        }
    }

    /** 绘制观察图内的当前动画帧及其全部堆叠层。 */
    @Override
    protected void process(int entityId) {
        Render render = mRender.get(entityId);
        if (!MapVisibility.inViewedMap(world, entityId) || render.keyframe == null
            || render.keyframe.getTexture() == null || !render.visible) {
            return;
        }
        Pos pos = mPos.get(entityId);
        float height = mSoarHeight.has(entityId) ? mSoarHeight.get(entityId).height : 0f;
        updateBatch();
        setShaderUniforms(entityId);
        batch.setShader(shaderProgram);
        if (render.textureSheets != null && !render.textureSheets.isEmpty()) {
            for (int i = 0; i < render.textureSheets.size; i++) {
                TextureRegion region = render.textureSheets.get(i);
                if (region != null && region.getTexture() != null) {
                    RenderGeometry.draw(batch, render, pos, region, height, i, worldScale);
                }
            }
        } else {
            RenderGeometry.draw(batch, render, pos, render.keyframe, height, 0, worldScale);
        }
        batch.setShader(null);
    }
}
