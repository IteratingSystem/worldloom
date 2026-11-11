package org.worldloom.system;

import com.artemis.annotations.All;
import com.artemis.annotations.Exclude;
import com.artemis.annotations.Wire;
import com.artemis.Aspect;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntMap;
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
    private final IntMap<ShaderComp> shaderOwners = new IntMap<>();

    /** 使用渲染管线与世界缩放创建精灵绘制代理。 */
    public RenderFrameSystem(EntityProcessPrincipal principal, float worldScale) {
        super(principal);
        this.worldScale = worldScale;
    }

    /** 取得当前地图渲染器共用的批次。 */
    @Override
    protected void initialize() {
        updateBatch();
        var shaders = world.getAspectSubscriptionManager().get(Aspect.all(ShaderComp.class));
        shaders.addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
            /** 保留组件引用，使直接删除实体或移除组件后仍能释放原程序。 */
            @Override public void inserted(IntBag entities) { retainShaders(entities); }
            /** 移除ShaderComp才释放；临时隐藏或移除Render不影响程序生命周期。 */
            @Override public void removed(IntBag entities) {
                for (int i = 0; i < entities.size(); i++) {
                    ShaderComp previous = shaderOwners.remove(entities.get(i));
                    if (previous != null) previous.dispose();
                }
            }
        });
        retainShaders(shaders.getEntities());
    }

    /** 记录全部实体的Shader组件，包括屏外、后台地图和没有Render的实体。 */
    private void retainShaders(IntBag entities) {
        for (int i = 0; i < entities.size(); i++) {
            int id = entities.get(i);
            ShaderComp current = mShaderComp.get(id);
            ShaderComp previous = shaderOwners.put(id, current);
            if (previous != null && previous != current) previous.dispose();
        }
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
        ShaderComp shader = mShaderComp.has(entityId) ? mShaderComp.get(entityId) : null;
        ShaderProgram program = shader == null ? null : shader.shaderProgram;
        if (program == null) {
            drawFrame(render, pos, height);
            return;
        }
        ShaderProgram previous = batch.getShader();
        // 先用旧程序提交上一批顶点，再允许回调绑定或改写Uniform；同程序也必须隔离参数。
        batch.flush();
        batch.setShader(program);
        try {
            if (shader.shaderUniforms != null) shader.shaderUniforms.setUniforms(world.getDelta());
            com.badlogic.gdx.Gdx.gl.glActiveTexture(com.badlogic.gdx.graphics.GL20.GL_TEXTURE0);
            drawFrame(render, pos, height);
        } finally {
            // 回调可绑定辅助纹理；恢复0号槽，并在恢复程序前提交本实体。
            com.badlogic.gdx.Gdx.gl.glActiveTexture(com.badlogic.gdx.graphics.GL20.GL_TEXTURE0);
            batch.flush();
            batch.setShader(previous);
        }
    }

    /** 普通精灵保持现有批处理；自定义Shader只在外层隔离参数，共用相同逐层绘制逻辑。 */
    private void drawFrame(Render render, Pos pos, float height) {
        if (render.textureSheets != null && !render.textureSheets.isEmpty()) {
            for (int i = 0; i < render.textureSheets.size; i++) {
                TextureRegion region = render.textureSheets.get(i);
                if (region != null && region.getTexture() != null)
                    RenderGeometry.draw(batch, render, pos, region, height, i, worldScale);
            }
        } else RenderGeometry.draw(batch, render, pos, render.keyframe, height, 0, worldScale);
    }

    /** World.dispose不逐组件回收GL资源，因此统一释放仍然存活的实体程序。 */
    @Override protected void dispose() {
        for (ShaderComp shader : shaderOwners.values()) shader.dispose();
        shaderOwners.clear();
    }
}
