package org.worldloom.system;

import com.artemis.BaseSystem;
import com.artemis.Aspect;
import com.artemis.utils.Bag;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectLongMap;
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
    private final ObjectMap<World, DefContactListener> contacts = new ObjectMap<>();
    private final ObjectLongMap<World> geometryRevisions = new ObjectLongMap<>();
    private static final double PHYSICS_STEP = 1.0 / 60;
    private static final int MAX_SUBSTEPS = 6;
    private double physicsRemainder;
    /** 当前观察地图的旧接口；实体业务应调用getWorldForEntity。 */
    public World box2DWorld;
    public TiledTileCollisionToBox2d tiledTileCollisionToBox2d;
    public Bag<Body> tileCollider = new Bag<>();

    /** 只保存物理参数；原生世界延迟到initialize创建，安装系统失败不占用原生资源。 */
    public B2dSystem(float gx, float gy, boolean doSleep, float worldScale,
                     boolean combineTileCollisions) {
        this.gx = gx; this.gy = gy; this.doSleep = doSleep;
        this.worldScale = worldScale; this.combineTileCollisions = combineTileCollisions;
    }

    /** 配置常驻图时一次创建全部物理环境；模板图不会进入此目录。 */
    @Override protected void initialize() {
        Array<String> maps = Worldloom.config().getResidentMaps();
        if (maps.isEmpty()) maps.add(tiledMapSystem.getCurrent());
        for (String mapId : maps) {
            if (tiledMapSystem.getTiledMap(mapId) == null)
                throw new IllegalArgumentException("物理地图不存在: " + mapId);
            World physics = createWorld();
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
        DefContactListener listener = new DefContactListener();
        contacts.put(physics, listener);
        physics.setContactListener(listener);
        return physics;
    }

    /** 从夹具所属物理世界的接触索引追加结果；非本系统管理的世界由调用方处理。 */
    public boolean collectContacts(Fixture fixture, Bag<Fixture> output) {
        DefContactListener listener = contacts.get(fixture.getBody().getWorld());
        if (listener == null) return false;
        listener.collectContacts(fixture, output);
        return true;
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

    /** 读取指定图的静态几何版本；业务缓存按版本懒重建，不需要轮询全部身体。 */
    public long getStaticGeometryRevision(String mapId) {
        return geometryRevisions.get(getWorld(mapId), 0L);
    }

    /** 原生静态几何编辑完成后递增版本，并失效所属图导航；必须在主线程调用。 */
    public void staticGeometryChanged(World physics) {
        staticGeometryChanged(physics, true);
    }

    /** 静态传感器也更新几何版本；只有实体碰撞改变才要求导航重建。 */
    public void staticGeometryChanged(World physics, boolean affectsNavigation) {
        if (physics == null) throw new IllegalArgumentException("Physics world cannot be null");
        geometryRevisions.put(physics, geometryRevisions.get(physics, 0L) + 1);
        if (!affectsNavigation) return;
        NavigationSystem navigation = world.getSystem(NavigationSystem.class);
        if (navigation == null) return;
        if (!isResidentMode()) { navigation.invalidate(); return; }
        for (ObjectMap.Entry<String, World> entry : mapWorlds)
            if (entry.value == physics) { navigation.invalidate(entry.key); return; }
    }

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
        staticGeometryChanged(physics);
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
        staticGeometryChanged(physics);
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
        staticGeometryChanged(physics);
    }

    /** 全图共用60Hz物理时钟，每帧最多补六步；过长帧丢弃超出预算的物理时间。 */
    @Override protected void processSystem() {
        float delta = world.getDelta();
        if (!Float.isFinite(delta) || delta < 0)
            throw new IllegalArgumentException("Physics delta must be finite and non-negative");
        physicsRemainder += Math.min((double) delta, PHYSICS_STEP * MAX_SUBSTEPS);
        int steps = Math.min(MAX_SUBSTEPS, (int) ((physicsRemainder + 1e-9) / PHYSICS_STEP));
        if (steps == 0) return;
        physicsRemainder = Math.max(0, physicsRemainder - (double) steps * PHYSICS_STEP);
        if (mapWorlds.isEmpty()) step(box2DWorld, steps);
        else for (World physics : mapWorlds.values()) step(physics, steps);
    }

    /** 同帧补步共享已施加的力，完成后按World原有自动清力策略恢复。 */
    private void step(World physics, int steps) {
        boolean autoClear = physics.getAutoClearForces();
        if (autoClear) physics.setAutoClearForces(false);
        try {
            for (int i = 0; i < steps; i++) physics.step((float) PHYSICS_STEP, 6, 2);
        } finally {
            if (autoClear) {
                physics.clearForces();
                physics.setAutoClearForces(true);
            }
        }
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
        if (mapWorlds.isEmpty()) { if (box2DWorld != null) box2DWorld.dispose(); }
        else for (World physics : mapWorlds.values()) physics.dispose();
        for (DefContactListener listener : contacts.values()) listener.clearContacts();
        contacts.clear();
        geometryRevisions.clear();
        physicsRemainder = 0;
        mapWorlds.clear(); tileColliders.clear(); tileCollider.clear(); box2DWorld = null;
    }
}
