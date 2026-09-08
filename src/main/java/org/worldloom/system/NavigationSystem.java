package org.worldloom.system;

import com.artemis.annotations.All;
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
import org.worldloom.event.B2dEvent;
import org.worldloom.navigation.NavigationGrid;
import org.worldloom.navigation.NavigationHeuristic;
import org.worldloom.navigation.NavigationNode;
import org.worldloom.navigation.NavigationStatus;

/**
 * 为当前地图建立导航网格，并通过Box2D速度执行导航。
 *
 * <p>A*仅在收到请求时运行，不会逐帧重复寻路。地图切换后缓存会自动失效。</p>
 */
@All({NavigationAgent.class, Pos.class, B2dBody.class})
@Exclude(Inert.class)
public class NavigationSystem extends IteratingSystem {
    private static final String TAG = NavigationSystem.class.getSimpleName();

    private final float worldScale;
    private final ObjectMap<String, String> roadLayerNames;
    private final float roadCost;
    private final float offRoadCost;
    private final IntMap<NavigationProfile> profilesByClearance = new IntMap<>();
    private final NavigationHeuristic heuristic;
    private M<NavigationAgent> mNavigationAgent;
    private M<Pos> mPos;
    private M<B2dBody> mB2dBody;
    private TiledMapSystem tiledMapSystem;
    private B2dSystem b2dSystem;
    private String gridMapName;
    private TiledMapTileLayer currentRoadLayer;
    private int nextRequestId = 1;

    public NavigationSystem(float worldScale) {
        this(worldScale, new ObjectMap<>(), 1f, 4f);
    }

    public NavigationSystem(float worldScale,
                            ObjectMap<String, String> roadLayerNames,
                            float roadCost, float offRoadCost) {
        if (worldScale <= 0f) {
            throw new IllegalArgumentException("world scale must be positive");
        }
        if (roadLayerNames == null || roadCost <= 0f
            || offRoadCost < roadCost) {
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
     * 请求实体移动到地图像素坐标，并允许在目标指定距离内停止。
     *
     * @return 本次请求编号，实体不具备导航条件时返回-1
     */
    public int navigate(int entityId, float targetX, float targetY,
                        float stopDistance) {
        refreshMapIfNeeded();
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
        agent.stopDistance = Math.max(0f, stopDistance);
        agent.pathMapName = gridMapName;
        agent.path.clear();
        agent.nextWaypoint = 0;

        if (agent.speed <= 0f || agent.radius < 0f
            || agent.waypointTolerance < 0f) {
            fail(entityId, agent, "Navigation agent configuration is invalid");
            return requestId;
        }

        Pos pos = mPos.get(entityId);
        float arrivalDistance = Math.max(agent.waypointTolerance,
            agent.stopDistance);
        if (Vector2.dst(pos.x, pos.y, targetX, targetY) <= arrivalDistance) {
            succeed(entityId, agent);
            return requestId;
        }

        NavigationProfile profile = profileFor(agent.radius);
        NavigationGrid grid = profile.grid;
        NavigationNode start = grid.getNodeAt(pos.x, pos.y);
        NavigationNode end = grid.getNodeAt(targetX, targetY);
        if (start == null || end == null) {
            fail(entityId, agent, "Navigation start or target is blocked or outside the map");
            return requestId;
        }

        DefaultGraphPath<NavigationNode> nodePath = new DefaultGraphPath<>();
        if (!profile.pathFinder.searchNodePath(start, end, heuristic, nodePath)) {
            fail(entityId, agent, "No navigation path was found");
            return requestId;
        }

        // 起点就是实体当前所在单元格，不需要再走向起点中心。
        for (int i = 1; i < nodePath.getCount(); i++) {
            NavigationNode node = nodePath.get(i);
            agent.path.add(new Vector2(node.x, node.y));
        }
        if (agent.path.isEmpty()
            || !agent.path.peek().epsilonEquals(targetX, targetY, 0.001f)) {
            agent.path.add(new Vector2(targetX, targetY));
        }
        agent.status = NavigationStatus.MOVING;
        return requestId;
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

    /** 静态Fixture在当前地图内发生增删后，使导航图在下次请求时重建。 */
    public void invalidate() {
        profilesByClearance.clear();
        Gdx.app.debug(TAG, "Navigation grids invalidated");
    }

    @Subscribe
    public void onEvent(B2dEvent event) {
        if (event.type == B2dEvent.DEL_TILE_COLLIDER
            || event.type == B2dEvent.CREATE_TILE_COLLIDER) {
            invalidate();
        }
    }

    @Override
    protected void begin() {
        refreshMapIfNeeded();
    }

    @Override
    protected void process(int entityId) {
        NavigationAgent agent = mNavigationAgent.get(entityId);
        if (agent.status != NavigationStatus.MOVING) {
            return;
        }
        if (!gridMapName.equals(agent.pathMapName)) {
            fail(entityId, agent, "Navigation path belongs to a different map");
            return;
        }
        B2dBody b2dBody = mB2dBody.get(entityId);
        if (b2dBody.body == null) {
            fail(entityId, agent, "Navigation entity has no Box2D body");
            return;
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

    private NavigationProfile profileFor(float clearance) {
        int key = Float.floatToIntBits(clearance);
        NavigationProfile profile = profilesByClearance.get(key);
        if (profile == null) {
            NavigationGrid grid = NavigationGrid.fromBox2D(
                tiledMapSystem.getTiledMap(), b2dSystem.box2DWorld,
                worldScale, currentRoadLayer, roadCost, offRoadCost, clearance);
            profile = new NavigationProfile(grid);
            profilesByClearance.put(key, profile);
        }
        return profile;
    }

    private void refreshMapIfNeeded() {
        String currentMap = tiledMapSystem.getCurrent();
        if (currentMap.equals(gridMapName)) {
            return;
        }
        gridMapName = currentMap;
        currentRoadLayer = resolveRoadLayer(currentMap);
        invalidate();
        Gdx.app.debug(TAG, "Navigation grids invalidated for map: " + currentMap);
    }

    private TiledMapTileLayer resolveRoadLayer(String mapName) {
        String layerName = roadLayerNames.get(mapName);
        if (layerName == null || layerName.isBlank()) {
            return null;
        }
        TiledMap map = tiledMapSystem.getTiledMap();
        MapLayer layer = map.getLayers().get(layerName);
        if (layer instanceof TiledMapTileLayer tileLayer) {
            return tileLayer;
        }
        Gdx.app.error(TAG, "Navigation road tile layer was not found: "
            + layerName + ", map: " + mapName);
        return null;
    }

    private void succeed(int entityId, NavigationAgent agent) {
        agent.status = NavigationStatus.SUCCEEDED;
        agent.path.clear();
        agent.nextWaypoint = 0;
        stopBody(entityId);
    }

    private void fail(int entityId, NavigationAgent agent, String reason) {
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

    private static final class NavigationProfile {
        private final NavigationGrid grid;
        private final IndexedAStarPathFinder<NavigationNode> pathFinder;

        private NavigationProfile(NavigationGrid grid) {
            this.grid = grid;
            pathFinder = new IndexedAStarPathFinder<>(grid);
        }
    }
}
