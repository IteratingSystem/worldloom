package org.worldloom.system;

import com.artemis.BaseSystem;
import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.managers.TagManager;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.maps.tiled.TiledMap;
import org.worldloom.Worldloom;
import org.worldloom.box2d.BodyPlacement;
import org.worldloom.component.MapMembership;
import org.worldloom.component.MapAttachment;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import java.util.HashSet;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.worldloom.component.B2dBody;
import org.worldloom.component.Portal;
import org.worldloom.component.Pos;
import org.worldloom.event.CameraEvent;
import org.worldloom.event.MapEvent;
import org.worldloom.manager.map.GameSnapshotManager;
import org.worldloom.serialize.EntityBuilder;
import org.worldloom.serialize.EntityDeleter;
import org.worldloom.serialize.EntitySerializer;
import org.worldloom.serialize.data.EntityData;
import org.worldloom.serialize.data.EntityDatum;

/**
 * 常驻模式保留ECS实体，仅迁移地图归属和物理身体；旧单图模式保留存档切图兼容。
 */
public class MapTransitionSystem extends BaseSystem {
    private static final String TAG = MapTransitionSystem.class.getSimpleName();

    private final Array<PortalRequest> pending = new Array<>();

    /** 在物理世界均解锁的系统安全点执行门传送，接触回调只登记请求。 */
    @Override
    protected void processSystem() {
        if (!world.getSystem(B2dSystem.class).areWorldsUnlocked()) return;
        while (pending.notEmpty()) {
            PortalRequest request = pending.removeIndex(0);
            MapIdentitySystem identities = world.getSystem(MapIdentitySystem.class);
            int target = identities.find(request.mapId, request.targetTag);
            ComponentMapper<Pos> positions = world.getMapper(Pos.class);
            if (target < 0 || !positions.has(target)) {
                Gdx.app.error(TAG, "目标落脚点不存在，保留原位置: " + request.mapId + "/" + request.targetTag);
                continue;
            }
            Pos landing = positions.get(target);
            if (transferAll(request.entityIds, request.mapId, landing.x, landing.y) && request.switchMap) {
                observe(request.mapId, request.playerId);
            }
        }
    }

    /** 保存门目标的值，避免接触回调结束后组件被修改导致请求串图。 */
    private record PortalRequest(String mapId, String targetTag, int[] entityIds, int playerId, boolean switchMap) { }

    /** 主线程安全点直接迁移一个常驻实体；世界锁定或落点无效时返回false供业务重试。 */
    public boolean transfer(int entityId, String targetMap, float x, float y) {
        if (!world.getSystem(B2dSystem.class).isResidentMode())
            throw new IllegalStateException("显式实体迁移需要启用常驻地图");
        return transferAll(new int[]{entityId}, targetMap, x, y);
    }

    /** 可靠主机复制入口：跳过落点占用判定，仍验证地图、实体、坐标和资源安全性。 */
    public boolean transferAuthoritative(int entityId, String targetMap, float x, float y) {
        return transferAll(new int[]{entityId}, targetMap, x, y, true);
    }

    /** 切换本机观察图；实体已由迁移或权威同步安置，不再次修改任何人物位置。 */
    public void observe(String mapId, int playerId) {
        world.getSystem(B2dSystem.class).getWorld(mapId);
        MapMembership membership = world.getMapper(MapMembership.class).get(playerId);
        if (membership == null || !mapId.equals(membership.mapId))
            throw new IllegalArgumentException("观察目标不在目标地图");
        changeRuntimeMap(mapId);
        jumpCameraTo(playerId);
        GameSnapshotManager.getInstance().changeCurrentMap(mapId);
    }

    /** 正常传送使用碰撞预检并递增换图代数。 */
    private boolean transferAll(int[] entityIds, String targetMap, float x, float y) {
        return transferAll(entityIds, targetMap, x, y, false);
    }

    /** 先验证和准备全部目标身体，任一步失败都释放暂存资源并保留源实体。 */
    private boolean transferAll(int[] entityIds, String targetMap, float x, float y, boolean authoritative) {
        B2dSystem physics = world.getSystem(B2dSystem.class);
        if (!physics.areWorldsUnlocked()) return false;
        ComponentMapper<MapMembership> memberships = world.getMapper(MapMembership.class);
        ComponentMapper<Pos> positions = world.getMapper(Pos.class);
        ComponentMapper<B2dBody> bodies = world.getMapper(B2dBody.class);
        MapIdentitySystem identities = world.getSystem(MapIdentitySystem.class);
        Array<B2dBody.PreparedTransfer> prepared = new Array<>();
        IntArray group = new IntArray();
        IntMap<VectorLanding> landings = new IntMap<>();
        try {
            if (entityIds.length == 0 || !positions.has(entityIds[0]) || !memberships.has(entityIds[0]))
                throw new IllegalArgumentException("迁移根实体缺少坐标或地图归属");
            Pos origin = positions.get(entityIds[0]);
            String sourceMap = memberships.get(entityIds[0]).mapId;
            float dx = x - origin.x, dy = y - origin.y;
            HashSet<Integer> included = new HashSet<>();
            for (int id : entityIds) if (included.add(id)) group.add(id);
            // 仅显式附属实体随宿主迁移；资产Owner不会把船或房屋搬进房间。
            var owners = world.getAspectSubscriptionManager().get(Aspect.all(MapAttachment.class, MapMembership.class, Pos.class)).getEntities();
            for (int index = 0; index < group.size; index++) {
                int ownerId = group.get(index);
                for (int i = 0; i < owners.size(); i++) {
                    int child = owners.get(i);
                    if (world.getMapper(MapAttachment.class).get(child).ownerId == ownerId
                        && sourceMap.equals(memberships.get(child).mapId) && included.add(child)) group.add(child);
                }
            }
            for (int i = 0; i < group.size; i++) {
                int id = group.get(i);
                if (!positions.has(id) || !memberships.has(id) || !sourceMap.equals(memberships.get(id).mapId))
                    throw new IllegalArgumentException("迁移组实体不在同一源地图");
                Pos old = positions.get(id);
                landings.put(id, new VectorLanding(old.x + dx, old.y + dy));
            }
            entityIds = group.toArray();
            var destination = physics.getWorld(targetMap);
            TiledMap map = world.getSystem(TiledMapSystem.class).getTiledMap(targetMap);
            int width = map.getProperties().get("width", Integer.class) * map.getProperties().get("tilewidth", Integer.class);
            int height = map.getProperties().get("height", Integer.class) * map.getProperties().get("tileheight", Integer.class);
            if (!Float.isFinite(x) || !Float.isFinite(y) || x < 0f || y < 0f || x >= width || y >= height)
                throw new IllegalArgumentException("目标坐标超出地图");
            HashSet<Integer> unique = new HashSet<>();
            for (int id : entityIds) {
                if (!unique.add(id) || !world.getEntityManager().isActive(id) || !positions.has(id) || !memberships.has(id))
                    throw new IllegalArgumentException("迁移实体无效或缺少归属: " + id);
                VectorLanding landing = landings.get(id);
                if (!Float.isFinite(landing.x) || !Float.isFinite(landing.y) || landing.x < 0f || landing.y < 0f || landing.x >= width || landing.y >= height)
                    throw new IllegalArgumentException("随行实体目标超出地图");
                MapMembership membership = memberships.get(id);
                if (membership.localTag != null && !membership.localTag.isBlank()) {
                    int conflict = identities.find(targetMap, membership.localTag);
                    if (conflict >= 0 && conflict != id) throw new IllegalStateException("目标地图本地名称冲突: " + membership.localTag);
                }
                if (bodies.has(id) && bodies.get(id).body != null) {
                    float scale = Worldloom.config().getWorldScale();
                    if (!authoritative && !BodyPlacement.canPlace(bodies.get(id).body, destination, landing.x * scale, landing.y * scale))
                        throw new IllegalArgumentException("目标落点被实体或地形阻挡");
                }
            }
            for (int id : entityIds) if (bodies.has(id) && bodies.get(id).body != null)
                prepared.add(bodies.get(id).prepareTransfer(destination, landings.get(id).x, landings.get(id).y));
        } catch (RuntimeException exception) {
            for (B2dBody.PreparedTransfer transfer : prepared) transfer.rollback();
            Gdx.app.error(TAG, "跨图迁移未执行: " + exception.getMessage());
            return false;
        }
        // 已完成全部可失败的资源创建；本线程安全点内提交不会插入物理step。
        for (B2dBody.PreparedTransfer transfer : prepared) transfer.commit();
        NavigationSystem navigation = world.getSystem(NavigationSystem.class);
        for (int id : entityIds) {
            MapMembership membership = memberships.get(id);
            if (!authoritative && !targetMap.equals(membership.mapId)) membership.mapEpoch++;
            membership.mapId = targetMap;
            VectorLanding landing = landings.get(id);
            positions.get(id).set(landing.x, landing.y);
            identities.refresh(id);
            if (navigation != null) navigation.cancel(id);
        }
        return true;
    }

    /** 保存迁移组内每个实体独立的目标像素坐标。 */
    private record VectorLanding(float x, float y) { }

    /** 门组件统一入口；常驻模式排队迁移，旧模式继续使用当前图快照切换。 */
    public void teleport(Portal portal, int[] entityIds, int playerEntityId, boolean switchMap) {
        if (portal == null) {
            throw new IllegalArgumentException("portal cannot be null");
        }
        if (portal.targetMap == null || portal.targetMap.isBlank()) {
            throw new IllegalArgumentException("portal targetMap cannot be blank");
        }
        if (entityIds == null || entityIds.length == 0) {
            return;
        }

        if (world.getSystem(B2dSystem.class).isResidentMode()) {
            pending.add(new PortalRequest(portal.targetMap, portal.targetPosEntity, entityIds.clone(), playerEntityId, switchMap));
            return;
        }

        String sourceMap = world.getSystem(TiledMapSystem.class).getCurrent();
        if (portal.targetMap.equals(sourceMap)) {
            relocateEntities(portal.targetPosEntity, entityIds);
            return;
        }

        GameSnapshotManager stateManager = GameSnapshotManager.getInstance();
        stateManager.captureCurrentWorld(world);

        EntityData sourceData = stateManager.getEntityData(sourceMap);
        EntityData targetData = stateManager.getEntityData(portal.targetMap);
        moveEntityData(sourceData, targetData, entityIds, switchMap);

        if (!switchMap) {
            return;
        }

        EntityDeleter.deleteAll(world, entityIds);
        changeRuntimeMap(portal.targetMap);
        EntityBuilder.buildEntities(world, targetData);
        relocateEntities(portal.targetPosEntity, entityIds);
        jumpCameraTo(playerEntityId);
        stateManager.changeCurrentMap(portal.targetMap);
    }

    /** 旧单图模式将离场实体镜像转入目标快照。 */
    private void moveEntityData(EntityData sourceData, EntityData targetData,
                                int[] entityIds, boolean switchMap) {
        for (int entityId : entityIds) {
            removeEntityDataByRuntimeId(sourceData, entityId);

            if (!switchMap) {
                EntityDatum entityDatum = EntitySerializer.createEntityDatum(world, entityId);
                targetData.add(entityDatum);
                EntityDeleter.deleteEntity(world, entityId);
            }
        }
    }

    /** 删除旧单图快照中对应运行时实体的条目。 */
    private void removeEntityDataByRuntimeId(EntityData entityData, int entityId) {
        for (EntityDatum datum : entityData) {
            if (datum.entityId == entityId) {
                entityData.removeValue(datum, false);
                return;
            }
        }
    }

    /** 发送观察地图切换事件，不负责常驻实体增删。 */
    private void changeRuntimeMap(String targetMap) {
        MapEvent mapEvent = new MapEvent(MapEvent.CHANGE_MAP);
        mapEvent.mapName = targetMap;
        world.getSystem(EventSystem.class).dispatch(mapEvent);
    }

    /** 旧单图模式解析落脚点并移动实体，缺失落点保留原位置。 */
    private void relocateEntities(String targetTag, int[] entityIds) {
        TagManager tagManager = world.getSystem(TagManager.class);
        ComponentMapper<Pos> positions = world.getMapper(Pos.class);
        ComponentMapper<B2dBody> bodies = world.getMapper(B2dBody.class);
        int targetId = targetTag == null ? -1 : tagManager.getEntityId(targetTag);

        if (targetId == -1 || !positions.has(targetId)) {
            Gdx.app.error(TAG, "Target position not found: " + targetTag);
            return;
        }

        Pos targetPos = positions.get(targetId);
        for (int entityId : entityIds) {
            setEntityPosition(positions, bodies, entityId, targetPos);
        }
    }

    /** 同步旧单图实体的像素坐标与物理坐标。 */
    private void setEntityPosition(ComponentMapper<Pos> positions,
                                   ComponentMapper<B2dBody> bodies,
                                   int entityId, Pos targetPos) {
        if (!positions.has(entityId)) {
            Gdx.app.error(TAG, "Entity has no Pos component: " + entityId);
            return;
        }
        Pos pos = positions.get(entityId);
        if (targetPos == null) {
            pos.set(0, 0);
        } else {
            pos.copy(targetPos);
        }
        if (bodies.has(entityId)) {
            bodies.get(entityId).setPos(pos);
        }
    }

    /** 仅将本机相机跳转到成功切图的玩家。 */
    private void jumpCameraTo(int entityId) {
        ComponentMapper<Pos> positions = world.getMapper(Pos.class);
        if (!positions.has(entityId)) {
            return;
        }
        CameraEvent cameraEvent = new CameraEvent(CameraEvent.JUMP_POS);
        cameraEvent.pos = positions.get(entityId);
        world.getSystem(EventSystem.class).dispatch(cameraEvent);
    }
}
