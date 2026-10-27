package org.worldloom.system;

import com.artemis.BaseSystem;
import com.artemis.Aspect;
import com.artemis.utils.Bag;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import dev.lyze.projectTrianglePlatforming.TiledTileCollisionToBox2d;
import dev.lyze.projectTrianglePlatforming.TiledTileCollisionToBox2dOptions;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.worldloom.Worldloom;
import org.worldloom.box2d.listener.DefContactListener;
import org.worldloom.component.MapMembership;
import org.worldloom.component.B2dBody;
import org.worldloom.event.B2dEvent;

/** 在同一ECS内维护各正式地图独立的物理世界，观察图只影响兼容入口。 */
public class B2dSystem extends BaseSystem {
    private TiledMapSystem tiledMapSystem;
    private final float worldScale, gx, gy;
    private final boolean doSleep, combineTileCollisions;
    private final ObjectMap<String, World> mapWorlds = new ObjectMap<>();
    private final ObjectMap<String, Bag<Body>> tileColliders = new ObjectMap<>();
    /** 当前观察地图的旧接口；实体业务应调用getWorldForEntity。 */
    public World box2DWorld;
    public TiledTileCollisionToBox2d tiledTileCollisionToBox2d;
    public Bag<Body> tileCollider = new Bag<>();

    /** 保存全局物理参数并预建旧单图环境，实际地图注册在initialize完成。 */
    public B2dSystem(float gx, float gy, boolean doSleep, float worldScale,
                     boolean combineTileCollisions) {
        this.gx = gx; this.gy = gy; this.doSleep = doSleep;
        this.worldScale = worldScale; this.combineTileCollisions = combineTileCollisions;
        box2DWorld = createWorld();
    }

    /** 配置常驻图时一次创建全部物理环境；模板图不会进入此目录。 */
    @Override protected void initialize() {
        Array<String> maps = Worldloom.config().getResidentMaps();
        if (maps.isEmpty()) maps.add(tiledMapSystem.getCurrent());
        for (String mapId : maps) {
            if (tiledMapSystem.getTiledMap(mapId) == null)
                throw new IllegalArgumentException("物理地图不存在: " + mapId);
            World physics = mapWorlds.isEmpty() ? box2DWorld : createWorld();
            mapWorlds.put(mapId, physics);
            tileColliders.put(mapId, new Bag<>());
            createTileCollider(mapId);
        }
        selectViewedMap(tiledMapSystem.getCurrent());
    }

    /** 创建不与其它地图共享接触、刚体或坐标空间的Box2D环境。 */
    private World createWorld() {
        World physics = new World(new Vector2(gx, gy), doSleep);
        physics.setContinuousPhysics(true);
        physics.setContactListener(new DefContactListener());
        return physics;
    }

    /** 判断当前会话是否启用了显式正式地图目录。 */
    public boolean isResidentMode() { return Worldloom.config().isResidentMode(); }

    /** 取得地图物理环境，常驻模式禁止把未知地图误映射到观察图。 */
    public World getWorld(String mapId) {
        Worldloom.config().requireRuntimeMap(mapId);
        if (!isResidentMode()) return box2DWorld;
        World physics = mapWorlds.get(mapId);
        if (physics == null) throw new IllegalArgumentException("未注册的物理地图: " + mapId);
        return physics;
    }

    /** 根据人物归属取得物理环境，缺失归属的常驻实体视为配置错误。 */
    public World getWorldForEntity(int entityId) {
        MapMembership membership = world.getMapper(MapMembership.class).get(entityId);
        if (membership == null && isResidentMode())
            throw new IllegalStateException("实体缺少地图归属: " + entityId);
        return getWorld(membership == null ? tiledMapSystem.getCurrent() : membership.mapId);
    }

    /** 提供全部物理环境供集中接触采样，调用方不得修改返回的注册表。 */
    public ObjectMap<String, World> getMapWorlds() { return mapWorlds; }

    /** 仅更新观察图兼容引用，不搬运、释放或步进任何地图。 */
    public void selectViewedMap(String mapId) {
        if (!isResidentMode()) return;
        box2DWorld = getWorld(mapId);
        tileCollider = tileColliders.get(mapId);
    }

    /** 判断结构变更能否安全执行，不在物理接触回调内创建或销毁Body。 */
    public boolean areWorldsUnlocked() {
        for (World physics : mapWorlds.values()) if (physics.isLocked()) return false;
        return true;
    }

    /** 为当前观察地图创建瓦片静态碰撞，旧调用保留。 */
    public void createTileCollider() { createTileCollider(tiledMapSystem.getCurrent()); }

    /** 为指定地图创建一次瓦片碰撞，不影响任何其它地图的实体Body。 */
    public void createTileCollider(String mapId) {
        World physics = getWorld(mapId);
        if (physics.isLocked()) throw new IllegalStateException("物理步进期间不能创建碰撞");
        Bag<Body> colliders = tileColliders.get(mapId);
        if (colliders == null) { colliders = new Bag<>(); tileColliders.put(mapId, colliders); }
        if (!colliders.isEmpty()) throw new IllegalStateException("瓦片碰撞已存在: " + mapId);
        if (tiledTileCollisionToBox2d == null)
            tiledTileCollisionToBox2d = new TiledTileCollisionToBox2d(TiledTileCollisionToBox2dOptions.builder()
                .scale(worldScale).combineTileCollisions(combineTileCollisions)
                .triangulateInsteadOfThrow(true).build());
        Array<Body> before = new Array<>(); physics.getBodies(before);
        MapLayer[] layers = tiledMapSystem.getPhyLayer(mapId);
        if (layers != null) for (MapLayer layer : layers)
            if (layer instanceof TiledMapTileLayer tileLayer)
                tiledTileCollisionToBox2d.parseLayer(tileLayer, physics);
        Array<Body> after = new Array<>(); physics.getBodies(after);
        for (Body body : after) if (!before.contains(body, true)) colliders.add(body);
        if (mapId.equals(tiledMapSystem.getCurrent())) tileCollider = colliders;
    }

    /** 仅供旧单图重建使用；常驻模式拒绝整图销毁，避免悬空的B2dBody引用。 */
    public void delAllCollider() {
        if (isResidentMode()) throw new IllegalStateException("常驻地图禁止整图删除物理身体；实体销毁请走B2dBody生命周期");
        World physics = getWorld(tiledMapSystem.getCurrent());
        if (physics.isLocked()) throw new IllegalStateException("物理步进期间不能清理碰撞");
        Array<Body> bodies = new Array<>(); physics.getBodies(bodies);
        for (Body body : bodies) physics.destroyBody(body);
        Bag<Body> colliders = tileColliders.get(tiledMapSystem.getCurrent());
        if (colliders != null) colliders.clear();
    }

    /** 仅删除当前观察地图的瓦片碰撞，保留人物碰撞。 */
    public void delTileCollider() { delTileCollider(tiledMapSystem.getCurrent()); }

    /** 只使指定地图的静态瓦片碰撞失效。 */
    public void delTileCollider(String mapId) {
        World physics = getWorld(mapId);
        if (physics.isLocked()) throw new IllegalStateException("物理步进期间不能清理碰撞");
        Bag<Body> colliders = tileColliders.get(mapId);
        if (colliders == null) return;
        for (Body body : colliders) physics.destroyBody(body);
        colliders.clear();
    }

    /** 每个地图以同一正常delta推进一次；不在按实体循环中重复step。 */
    @Override protected void processSystem() {
        if (mapWorlds.isEmpty()) box2DWorld.step(world.getDelta(), 6, 2);
        else for (World physics : mapWorlds.values()) physics.step(world.getDelta(), 6, 2);
    }

    /** 显式物理结构事件只作用于指定地图，旧事件默认当前观察图。 */
    @Subscribe public void onEvent(B2dEvent event) {
        String mapId = event.mapId == null ? tiledMapSystem.getCurrent() : event.mapId;
        if (event.type == B2dEvent.DEL_TILE_COLLIDER) delTileCollider(mapId);
        else if (event.type == B2dEvent.CREATE_TILE_COLLIDER) createTileCollider(mapId);
    }

    /** 退出会话统一释放各图物理环境，兼容引用不重复dispose。 */
    @Override public void dispose() {
        // Artemis退出不会逐组件调用Disposable；先释放组件独占Shape，再释放各图World。
        var entities = world.getAspectSubscriptionManager().get(Aspect.all(B2dBody.class)).getEntities();
        var bodies = world.getMapper(B2dBody.class);
        for (int i = 0; i < entities.size(); i++) bodies.get(entities.get(i)).dispose();
        if (mapWorlds.isEmpty()) box2DWorld.dispose();
        else for (World physics : mapWorlds.values()) physics.dispose();
        mapWorlds.clear(); tileColliders.clear(); tileCollider.clear(); box2DWorld = null;
    }
}
