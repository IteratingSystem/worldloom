package org.worldloom.system;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.annotations.Wire;
import com.artemis.utils.Bag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import net.mostlyoriginal.api.plugin.extendedcomponentmapper.M;
import net.mostlyoriginal.api.system.delegate.EntityProcessAgent;
import net.mostlyoriginal.api.system.delegate.EntityProcessPrincipal;
import net.mostlyoriginal.api.utils.BagUtils;
import org.worldloom.component.Render;
import org.worldloom.component.ZIndex;
import org.worldloom.render.MapVisibility;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;

/**
 * @Auther WenLong
 * @Date 2024/6/6 9:34
 * @Description 渲染管线
 **/
@Wire
public class RenderBatchingSystem extends BaseSystem implements EntityProcessPrincipal {
    private final static String TAG = RenderBatchingSystem.class.getSimpleName();
    private M<Render> mRender;
    private M<ZIndex> mZIndex;
    private RenderTiledSystem renderTiledSystem;
    private CameraSystem cameraSystem;

    private Batch batch;
    protected final Bag<Job> sortedJobs = new Bag<>();
    private final Bag<Job> visibleJobs = new Bag<>();
    private final Rectangle viewBounds = new Rectangle();

    /** 接入引擎统一的Artemis世界，绘制注册不会拆分后台实体。 */
    @Override
    protected void setWorld(World world) {
        super.setWorld(world);
    }

    /** 初始化地图渲染器共享批次。 */
    @Override
    protected void initialize() {
        super.initialize();
        updateBatch();
    }
    /** 地图切换时继续使用地图渲染器提供的共享批次。 */
    private void updateBatch(){
        batch = renderTiledSystem.mapRenderer.getBatch();
    }

    /** 在像素对齐后的实际世界相机视域内筛选并排序任务，保留屏外实体注册。 */
    @Override
    protected void processSystem() {
        updateViewBounds();
        visibleJobs.clear();
        for (int i = 0; i < sortedJobs.size(); i++) {
            Job job = sortedJobs.get(i);
            if (!MapVisibility.inViewedMap(world, job.entityId)) {
                continue;
            }
            if (job.agent instanceof RenderFrameSystem frames
                && !frames.intersectsView(job.entityId, viewBounds)) {
                continue;
            }
            visibleJobs.add(job);
        }
        BagUtils.sort(visibleJobs);
        final Object[] data = visibleJobs.getData();
        updateBatch();
        batch.begin();
        for (int i = 0,s = visibleJobs.size();i < s;i++){
            final Job job = (Job)data[i];
            final EntityProcessAgent agent = job.agent;
            agent.process(job.entityId);
        }
        batch.flush();
        batch.end();
    }

    /** 从相机视锥的世界坐标构造保守矩形，兼容缩放、旋转与像素缓冲扩边。 */
    private void updateViewBounds() {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (Vector3 corner : cameraSystem.camera.frustum.planePoints) {
            minX = Math.min(minX, corner.x);
            minY = Math.min(minY, corner.y);
            maxX = Math.max(maxX, corner.x);
            maxY = Math.max(maxY, corner.y);
        }
        viewBounds.set(minX, minY, maxX - minX, maxY - minY);
    }

    /** 登记实体绘制代理，屏外裁剪期间继续保留注册。 */
    @Override
    public void registerAgent(int entityId, EntityProcessAgent agent) {
        if (!mRender.has(entityId) || !mZIndex.has(entityId)){
            Gdx.app.log(TAG,"Lack of necessary components:[Render] or [ZIndex]");
            return;
        }

        Job job = new Job(entityId, agent);
        sortedJobs.add(job);
    }

    /** 实体离开订阅或真实销毁时移除对应绘制代理。 */
    @Override
    public void unregisterAgent(int entityId, EntityProcessAgent agent) {
        final Object[] data = sortedJobs.getData();
        for (int i = 0,s = sortedJobs.size(); i < s; i++){
            final Job e2 = (Job) data[i];
            if (e2.entityId == entityId && e2.agent == agent){
                sortedJobs.remove(i);
                break;
            }
        }
    }


    public class Job implements Comparable<Job>{
        public final int entityId;
        public final EntityProcessAgent agent;

        /** 保存实体与代理引用，层级在每次排序时读取最新组件。 */
        public Job(final int entityId,final EntityProcessAgent agent){
            this.entityId = entityId;
            this.agent = agent;
        }
        /** 按完整浮点层级比较，保留不足一个像素的深度差并满足排序传递性。 */
        @Override
        public int compareTo(Job o) {
            int entityIdO = o.entityId;
            float startNum = 0;
            float endNum = 0;
            if (mZIndex.has(entityIdO)) {
                ZIndex zIndex0 = mZIndex.get(entityIdO);
                endNum = zIndex0.index;
            }
            if (mZIndex.has(entityId)){
                ZIndex zIndex = mZIndex.get(entityId);
                startNum = zIndex.index;
            }
            return Float.compare(startNum, endNum);
        }
    }
}
