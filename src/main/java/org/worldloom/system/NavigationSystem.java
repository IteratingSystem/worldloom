package org.worldloom.system;

import com.artemis.annotations.All;
import com.artemis.utils.IntBag;
import com.artemis.annotations.Exclude;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ai.pfa.DefaultGraphPath;
import com.badlogic.gdx.ai.pfa.indexed.IndexedAStarPathFinder;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.plugin.extendedcomponentmapper.M;
import org.worldloom.component.B2dBody;
import org.worldloom.component.Inert;
import org.worldloom.component.NavigationAgent;
import org.worldloom.component.Pos;
import org.worldloom.component.MapMembership;
import org.worldloom.event.B2dEvent;
import org.worldloom.navigation.NavigationGrid;
import org.worldloom.navigation.NavigationHeuristic;
import org.worldloom.navigation.NavigationNode;
import org.worldloom.navigation.NavigationStatus;

/**
 * 为每张地图建立独立导航网格，并通过Box2D速度执行导航。
 *
 * <p>A*仅在收到请求时运行，不会逐帧重复寻路。仅静态碰撞改变时失效对应地图缓存。</p>
 */
@All({NavigationAgent.class, Pos.class, B2dBody.class})
@Exclude(Inert.class)
public class NavigationSystem extends IteratingSystem {
    private static final String TAG = NavigationSystem.class.getSimpleName();

    private final float worldScale;
    private final ObjectMap<String, String> roadLayerNames;
    private final float roadCost;
    private final float offRoadCost;
    private final ObjectMap<String, IntMap<NavigationProfile>> profilesByMap = new ObjectMap<>();
    private final IntMap<NavigationProfile> pathProfiles = new IntMap<>();
    private final NavigationHeuristic heuristic;
    private M<NavigationAgent> mNavigationAgent;
    private M<Pos> mPos;
    private M<B2dBody> mB2dBody;
    private TiledMapSystem tiledMapSystem;
    private B2dSystem b2dSystem;
    private String legacyMapName;
    private int nextRequestId = 1;

    /** 使用默认道路权重创建导航；缩放必须是有限正数。 */
    public NavigationSystem(float worldScale) {
        this(worldScale, new ObjectMap<>(), 1f, 4f);
    }

    /** 复制各地图道路配置，拒绝不能参与网格与速度计算的数值。 */
    public NavigationSystem(float worldScale,
                            ObjectMap<String, String> roadLayerNames,
                            float roadCost, float offRoadCost) {
        if (!Float.isFinite(worldScale) || worldScale <= 0f) {
            throw new IllegalArgumentException("world scale must be positive");
        }
        if (roadLayerNames == null || !Float.isFinite(roadCost) || !Float.isFinite(offRoadCost)
            || roadCost <= 0f || offRoadCost < roadCost) {
            throw new IllegalArgumentException("navigation configuration is invalid");
        }
        this.worldScale = worldScale;
        this.roadLayerNames = new ObjectMap<>();
        this.roadLayerNames.putAll(roadLayerNames);
        this.roadCost = roadCost;
        this.offRoadCost = offRoadCost;
        heuristic = new NavigationHeuristic(Math.min(1f, roadCost));
    }

    /**
     * 请求实体移动到地图像素坐标。
     *
     * @return 本次请求编号；无法建立路径时仍返回编号，可通过状态读取失败结果
     */
    public int navigate(int entityId, float targetX, float targetY) {
        return navigate(entityId, targetX, targetY, 0f);
    }

    /**
     * 请求实体移动到地图像素坐标；非法数值在修改现有请求前抛出异常。
     *
     * @return 本次请求编号，实体不具备导航条件时返回-1
     */
    public int navigate(int entityId, float targetX, float targetY,
                        float stopDistance) {
        if (!Float.isFinite(targetX) || !Float.isFinite(targetY)
            || !Float.isFinite(stopDistance) || stopDistance < 0f)
            throw new IllegalArgumentException("Navigation target and stop distance must be finite; distance cannot be negative");
        refreshMapIfNeeded();
        String mapId = mapFor(entityId);
        if (!mPos.has(entityId) || !mB2dBody.has(entityId)) {
            Gdx.app.error(TAG, "Entity is missing Pos or B2dBody: " + entityId);
            return -1;
        }

        NavigationAgent agent = mNavigationAgent.has(entityId)
            ? mNavigationAgent.get(entityId) : mNavigationAgent.create(entityId);
        int requestId = allocateRequestId();
        agent.requestId = requestId;
        agent.targetX = targetX;
        agent.targetY = targetY;
        agent.stopDistance = stopDistance;
        agent.pathMapName = mapId;
        planPath(entityId, agent);
        return requestId;
    }

    /** 首次请求和拓扑变更复用同一规划过程；自动重规划保留请求编号与原目标。 */
    private void planPath(int entityId, NavigationAgent agent) {
        pathProfiles.remove(entityId);
        agent.path.clear();
        agent.nextWaypoint = 0;
        Pos pos = mPos.get(entityId);
        if (!validGeometry(agent, pos)) {
            fail(entityId, agent, "Navigation agent configuration is invalid");
            return;
        }

        float arrivalDistance = Math.max(agent.waypointTolerance,
            agent.stopDistance);
        if (Vector2.dst(pos.x, pos.y, agent.targetX, agent.targetY) <= arrivalDistance) {
            succeed(entityId, agent);
            return;
        }

        NavigationProfile profile = profileFor(agent.pathMapName, agent.radius);
        NavigationGrid grid = profile.grid;
        NavigationNode start = grid.getNodeAt(pos.x, pos.y);
        NavigationNode end = grid.getNodeAt(agent.targetX, agent.targetY);
        if (start == null || end == null) {
            fail(entityId, agent, "Navigation start or target is blocked or outside the map");
            return;
        }

        DefaultGraphPath<NavigationNode> nodePath = new DefaultGraphPath<>();
        if (!profile.pathFinder.searchNodePath(start, end, heuristic, nodePath)) {
            fail(entityId, agent, "No navigation path was found");
            return;
        }

        // 起点就是实体当前所在单元格，不需要再走向起点中心。
        for (int i = 1; i < nodePath.getCount(); i++) {
            NavigationNode node = nodePath.get(i);
            agent.path.add(new Vector2(node.x, node.y));
        }
        if (agent.path.isEmpty()
            || !agent.path.peek().epsilonEquals(agent.targetX, agent.targetY, 0.001f)) {
            agent.path.add(new Vector2(agent.targetX, agent.targetY));
        }
        agent.status = NavigationStatus.MOVING;
        pathProfiles.put(entityId, profile);
    }

    /** 取消当前导航并立即停止实体。 */
    public void cancel(int entityId) {
        if (!mNavigationAgent.has(entityId)) {
            return;
        }
        NavigationAgent agent = mNavigationAgent.get(entityId);
        agent.path.clear();
        agent.nextWaypoint = 0;
        agent.status = NavigationStatus.IDLE;
        pathProfiles.remove(entityId);
        stopBody(entityId);
    }

    /** 读取指定请求的状态；请求已被替换时返回失败。 */
    public NavigationStatus getStatus(int entityId, int requestId) {
        if (!mNavigationAgent.has(entityId)) {
            return NavigationStatus.FAILED;
        }
        NavigationAgent agent = mNavigationAgent.get(entityId);
        return agent.requestId == requestId ? agent.status : NavigationStatus.FAILED;
    }

    /** 全部静态几何失效；下次请求或正在移动实体更新时重建并重规划。 */
    public void invalidate() {
        profilesByMap.clear();
        Gdx.app.debug(TAG, "Navigation grids invalidated");
    }

    /** 仅失效发生静态碰撞变更的地图，正在移动的请求保留目标并在下次更新重规划。 */
    public void invalidate(String mapId) { profilesByMap.remove(mapId); }

    /** 保留旧事件入口；物理系统直接编辑静态几何也会同步失效，重复失效保持幂等。 */
    @Subscribe
    public void onEvent(B2dEvent event) {
        if (event.type == B2dEvent.DEL_TILE_COLLIDER || event.type == B2dEvent.CREATE_TILE_COLLIDER)
            invalidate(event.mapId == null ? tiledMapSystem.getCurrent() : event.mapId);
    }

    @Override
    protected void begin() {
        refreshMapIfNeeded();
    }

    /** 更新移动前再次校验可动态修改的组件，避免非法速度进入Box2D。 */
    @Override
    protected void process(int entityId) {
        NavigationAgent agent = mNavigationAgent.get(entityId);
        if (agent.status != NavigationStatus.MOVING) {
            return;
        }
        if (!validGeometry(agent, mPos.get(entityId))) {
            fail(entityId, agent, "Navigation geometry is invalid");
            return;
        }
        if (!mapFor(entityId).equals(agent.pathMapName)) {
            fail(entityId, agent, "Navigation path belongs to a different map");
            return;
        }
        B2dBody b2dBody = mB2dBody.get(entityId);
        if (b2dBody.body == null) {
            fail(entityId, agent, "Navigation entity has no Box2D body");
            return;
        }

        if (pathProfiles.get(entityId) != profileFor(agent.pathMapName, agent.radius)) {
            planPath(entityId, agent);
            if (agent.status != NavigationStatus.MOVING) return;
        }

        Pos pos = mPos.get(entityId);
        float arrivalDistance = Math.max(agent.waypointTolerance,
            agent.stopDistance);
        if (Vector2.dst(pos.x, pos.y, agent.targetX, agent.targetY)
            <= arrivalDistance) {
            succeed(entityId, agent);
            return;
        }

        while (agent.nextWaypoint < agent.path.size) {
            Vector2 waypoint = agent.path.get(agent.nextWaypoint);
            if (Vector2.dst(pos.x, pos.y, waypoint.x, waypoint.y)
                > agent.waypointTolerance) {
                moveTowards(b2dBody.body, pos, waypoint, agent.speed);
                return;
            }
            agent.nextWaypoint++;
        }
        succeed(entityId, agent);
    }

    /** 检查请求、当前位置和动态导航参数；失败时只能停止，不能继续物理移动。 */
    private static boolean validGeometry(NavigationAgent agent, Pos pos) {
        return Float.isFinite(pos.x) && Float.isFinite(pos.y)
            && Float.isFinite(agent.targetX) && Float.isFinite(agent.targetY)
            && Float.isFinite(agent.speed) && agent.speed > 0f
            && Float.isFinite(agent.radius) && agent.radius >= 0f
            && Float.isFinite(agent.waypointTolerance) && agent.waypointTolerance >= 0f
            && Float.isFinite(agent.stopDistance) && agent.stopDistance >= 0f;
    }

    /** 实际物理子步前按实时Body位置限速，避免渲染帧与物理频率不同而越过路径点。 */
    public void preparePhysicsStep(com.badlogic.gdx.physics.box2d.World physics, float seconds) {
        if (!Float.isFinite(seconds) || seconds <= 0)
            throw new IllegalArgumentException("Navigation physics step must be finite and positive");
        IntBag ids = getSubscription().getEntities();
        for (int i = 0; i < ids.size(); i++) {
            int id = ids.get(i);
            NavigationAgent agent = mNavigationAgent.get(id);
            B2dBody component = mB2dBody.get(id);
            Body body = component.body;
            if (agent.status != NavigationStatus.MOVING || agent.nextWaypoint < 0 || agent.nextWaypoint >= agent.path.size
                || body == null || body.getWorld() != physics || !body.isActive()) continue;
            limitPhysicsMovement(body, agent.path.get(agent.nextWaypoint), seconds);
        }
    }

    /** 只约束导航已给出的速度，不重跑行为树、寻路或角色状态，碰撞位置以物理体为准。 */
    private void limitPhysicsMovement(Body body, Vector2 waypoint, float seconds) {
        float dx = waypoint.x * worldScale - body.getPosition().x;
        float dy = waypoint.y * worldScale - body.getPosition().y;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        Vector2 velocity = body.getLinearVelocity();
        float speed = velocity.len();
        float maximum = distance / seconds;
        if (speed > maximum) body.setLinearVelocity(velocity.x * maximum / speed, velocity.y * maximum / speed);
    }

    /** 按剩余距离给出导航速度，最终限速在实际物理子步前根据实时位置复核。 */
    private void moveTowards(Body body, Pos pos, Vector2 waypoint, float speed) {
        float dx = waypoint.x - pos.x;
        float dy = waypoint.y - pos.y;
        float distance = (float)Math.sqrt(dx * dx + dy * dy);
        if (distance <= 0f) {
            body.setLinearVelocity(0f, 0f);
            return;
        }
        float frameSeconds = Math.max(world.getDelta(), 0.000001f);
        float limitedSpeed = Math.min(speed, distance / frameSeconds);
        float velocityScale = limitedSpeed * worldScale / distance;
        body.setLinearVelocity(dx * velocityScale, dy * velocityScale);
    }

    /** 根据实体归属选图，离开观察图不会中断导航。 */
    private String mapFor(int entityId) {
        MapMembership membership = world.getMapper(MapMembership.class).get(entityId);
        if (membership == null && b2dSystem.isResidentMode())
            throw new IllegalStateException("导航实体缺少地图归属: " + entityId);
        return membership == null ? tiledMapSystem.getCurrent() : membership.mapId;
    }

    /** 同地图同净空共享导航网格，不同地图的像素坐标互不混用。 */
    private NavigationProfile profileFor(String mapId, float clearance) {
        IntMap<NavigationProfile> profiles = profilesByMap.get(mapId);
        if (profiles == null) { profiles = new IntMap<>(); profilesByMap.put(mapId, profiles); }
        int key = Float.floatToIntBits(clearance);
        NavigationProfile profile = profiles.get(key);
        if (profile == null) {
            NavigationGrid grid = NavigationGrid.fromBox2D(
                tiledMapSystem.getTiledMap(mapId), b2dSystem.getWorld(mapId),
                worldScale, resolveRoadLayer(mapId), roadCost, offRoadCost, clearance);
            profile = new NavigationProfile(grid);
            profiles.put(key, profile);
        }
        return profile;
    }

    /** 旧单图模式重建同一个物理环境后，清除上张地图的缓存。 */
    private void refreshMapIfNeeded() {
        if (b2dSystem.isResidentMode()) return;
        String currentMap = tiledMapSystem.getCurrent();
        if (!currentMap.equals(legacyMapName)) {
            legacyMapName = currentMap;
            invalidate();
        }
    }

    private TiledMapTileLayer resolveRoadLayer(String mapName) {
        String layerName = roadLayerNames.get(mapName);
        if (layerName == null || layerName.isBlank()) {
            return null;
        }
        TiledMap map = tiledMapSystem.getTiledMap(mapName);
        MapLayer layer = map.getLayers().get(layerName);
        if (layer instanceof TiledMapTileLayer tileLayer) {
            return tileLayer;
        }
        Gdx.app.error(TAG, "Navigation road tile layer was not found: "
            + layerName + ", map: " + mapName);
        return null;
    }

    private void succeed(int entityId, NavigationAgent agent) {
        pathProfiles.remove(entityId);
        agent.status = NavigationStatus.SUCCEEDED;
        agent.path.clear();
        agent.nextWaypoint = 0;
        stopBody(entityId);
    }

    private void fail(int entityId, NavigationAgent agent, String reason) {
        pathProfiles.remove(entityId);
        agent.status = NavigationStatus.FAILED;
        agent.path.clear();
        agent.nextWaypoint = 0;
        stopBody(entityId);
        Gdx.app.debug(TAG, reason + ", entity: " + entityId);
    }

    private void stopBody(int entityId) {
        if (!mB2dBody.has(entityId)) {
            return;
        }
        B2dBody b2dBody = mB2dBody.get(entityId);
        if (b2dBody.body != null) {
            b2dBody.body.setLinearVelocity(0f, 0f);
        }
    }

    private int allocateRequestId() {
        if (nextRequestId == Integer.MAX_VALUE) {
            nextRequestId = 1;
        }
        return nextRequestId++;
    }

    /** 实体删除或移除导航组件时不保留旧网格引用。 */
    @Override protected void removed(int entityId) { pathProfiles.remove(entityId); }

    /** 退出世界统一清理地图网格及运行路径引用。 */
    @Override protected void dispose() { pathProfiles.clear(); profilesByMap.clear(); }

    private static final class NavigationProfile {
        private final NavigationGrid grid;
        private final IndexedAStarPathFinder<NavigationNode> pathFinder;

        private NavigationProfile(NavigationGrid grid) {
            this.grid = grid;
            pathFinder = new IndexedAStarPathFinder<>(grid);
        }
    }
}
