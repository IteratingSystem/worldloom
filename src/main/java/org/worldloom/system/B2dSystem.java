package org.worldloom.system;

import com.artemis.BaseSystem;
import com.artemis.utils.Bag;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import dev.lyze.projectTrianglePlatforming.TiledTileCollisionToBox2d;
import dev.lyze.projectTrianglePlatforming.TiledTileCollisionToBox2dOptions;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.worldloom.box2d.listener.DefContactListener;
import org.worldloom.event.B2dEvent;


/**
 * @Auther WenLong
 * @Date 2025/2/12 15:44
 * @Description Box2D物理系统
 **/
public class B2dSystem extends BaseSystem {
    private TiledMapSystem tiledMapSystem;
    private float worldScale;
    private float gx;   //横向重力
    private float gy;   //纵向重力
    private boolean doSleep;
    private boolean combineTileCollisions;
    public World box2DWorld;
    public TiledTileCollisionToBox2d tiledTileCollisionToBox2d;
    public Bag<Body> tileCollider;

    public B2dSystem(float gx,float gy,boolean doSleep,float worldScale,boolean combineTileCollisions){
        this.worldScale = worldScale;
        this.gx = gx;
        this.gy = gy;
        this.doSleep = doSleep;
        this.combineTileCollisions = combineTileCollisions;

        box2DWorld = new World(new Vector2(gx,gy),doSleep);
        box2DWorld.setContinuousPhysics(true);
        box2DWorld.setContactListener(new DefContactListener());
        tileCollider = new Bag<>();
    }
    @Override
    protected void initialize() {
        createTileCollider();
    }

    /**
     * 创建以瓦片为基本的固定碰撞体
     */
    public void createTileCollider(){
        //保存刚开始的body
        Array<Body> originalBodies = new Array<>();
        box2DWorld.getBodies(originalBodies);
        //创建body
        if (tiledTileCollisionToBox2d == null){
            //绑定tiled中的物理形状
            tiledTileCollisionToBox2d = new TiledTileCollisionToBox2d(TiledTileCollisionToBox2dOptions.builder()
                    .scale(worldScale)
                    .combineTileCollisions(combineTileCollisions)
                    .triangulateInsteadOfThrow(true)
                    .build());
        }

        MapLayer[] phyLayers = tiledMapSystem.getPhyLayer();
        if (phyLayers == null) {
            return;
        }
        //构造tileCollider
        for (MapLayer phyLayer : phyLayers) {
            if (phyLayer instanceof TiledMapTileLayer tileLayer) {
                tiledTileCollisionToBox2d.parseLayer(tileLayer,box2DWorld);
            }
        }
        //记录新增的碰撞物
        tileCollider.clear();
        Array<Body> afterBodies = new Array<>();
        box2DWorld.getBodies(afterBodies);
        for (Body afterBody : afterBodies) {
            if (originalBodies.contains(afterBody,true)) {
                continue;
            }
            tileCollider.add(afterBody);
        }
    }

    /** 清空全部碰撞体；只允许在物理步进之外调用，不推进世界时间。 */
    public void delAllCollider(){
        requireUnlocked();
        Array<Body> bodies = new Array<>();
        box2DWorld.getBodies(bodies);
        // 转换为不可变数组
        Body[] bodyArray = bodies.toArray(Body.class);
        for (Body body : bodyArray) {
            //安全地销毁body，检查是否已经被销毁
            if (body != null && body.getWorld() != null) {
                try {
                    box2DWorld.destroyBody(body);
                } catch (Exception e) {
                    //忽略销毁时的异常，可能body已经被销毁
                }
            }
        }
    }
    
    /**
     * 只销毁tile碰撞体，保留实体body
     * 这个方法用于地图切换时，只清理地图相关的碰撞体
     */
    public void delTileCollider() {
        requireUnlocked();
        //只销毁tile碰撞体
        for (Body body : tileCollider) {
            if (body != null && body.getWorld() != null) {
                try {
                    box2DWorld.destroyBody(body);
                } catch (Exception e) {
                    //忽略销毁时的异常，可能body已经被销毁
                }
            }
        }
        tileCollider.clear();
    }
    /** 清理属于物理结构变更，不能在接触回调内操作被锁定的Box2D世界。 */
    private void requireUnlocked() {
        if (box2DWorld.isLocked()) throw new IllegalStateException("请在物理步进结束后清理碰撞体");
    }
    @Override
    protected void processSystem() {
        box2DWorld.step(world.delta,6,2);
    }
    @Subscribe
    public void onEvent(B2dEvent event){
        if (event.type == B2dEvent.DEL_TILE_COLLIDER){
            delTileCollider();
            return;
        }
        if (event.type == B2dEvent.CREATE_TILE_COLLIDER){
            createTileCollider();
            return;
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        box2DWorld.dispose();
    }
}
