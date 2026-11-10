package org.worldloom.system;

import com.artemis.Aspect;
import com.artemis.BaseEntitySystem;
import org.worldloom.component.MapMembership;
import org.worldloom.component.PersistentIdentity;
import org.worldloom.Worldloom;
import com.artemis.managers.TagManager;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

/** 主线程维护人物稳定身份与地图局部名称索引，跨门后显式刷新。 */
public final class MapIdentitySystem extends BaseEntitySystem {
    private final Map<String, Integer> identities = new HashMap<>();
    private final Map<String, Map<String, Set<Integer>>> localNames = new HashMap<>();
    private final Map<Integer, Binding> bindings = new HashMap<>();
    private final Set<IntConsumer> listeners = new LinkedHashSet<>();

    /** 保存已登记字段，移除旧索引时不读取已经变更的组件。 */
    private record Binding(String entityKey, String mapId, String localTag) { }

    /** 订阅带地图归属和稳定身份的实体，不逐帧扫描其它业务组件。 */
    public MapIdentitySystem() {
        super(Aspect.all(MapMembership.class, PersistentIdentity.class));
    }

    /** 解析稳定身份；未登记或身份为空时返回-1。 */
    public int resolve(String entityKey) {
        return entityKey == null ? -1 : identities.getOrDefault(entityKey, -1);
    }

    /** 按地图解析唯一局部名称；重复装饰名称不可当锚点，歧义时返回-1。 */
    public int find(String mapId, String localTag) {
        Map<String, Set<Integer>> names = localNames.get(mapId);
        Set<Integer> ids = names == null ? null : names.get(localTag);
        return ids == null || ids.size() != 1 ? -1 : ids.iterator().next();
    }

    /** 从调用实体所在图查找局部名称，全局PLAYER等兼容别名也必须在同图。 */
    public int resolveLocal(int sourceId, String localTag) {
        TagManager tags = world.getSystem(TagManager.class);
        if (!Worldloom.config().isResidentMode()) return tags.getEntityId(localTag);
        if (sourceId < 0) return -1;
        MapMembership source = world.getMapper(MapMembership.class).get(sourceId);
        if (source == null) return -1;
        int local = find(source.mapId, localTag);
        if (local >= 0) return local;
        int global = tags.getEntityId(localTag);
        return sameMap(sourceId, global) ? global : -1;
    }

    /** 统一取得实体运行图；只有旧单图无归属实体可回退到观察图，常驻缺归属立即报错。 */
    public String getMapId(int entityId) {
        MapMembership membership = world.getMapper(MapMembership.class).get(entityId);
        if (membership != null && membership.mapId != null && !membership.mapId.isBlank()) {
            Worldloom.config().requireRuntimeMap(membership.mapId);
            return membership.mapId;
        }
        if (Worldloom.config().isResidentMode())
            throw new IllegalStateException("常驻实体缺少地图归属：" + entityId);
        return world.getSystem(TiledMapSystem.class).getCurrent();
    }

    /** 比较两个已建立地图归属的实体，不将两个缺少归属的实体视为同图。 */
    public boolean sameMap(int firstId, int secondId) {
        if (firstId < 0 || secondId < 0) return false;
        MapMembership first = world.getMapper(MapMembership.class).get(firstId);
        MapMembership second = world.getMapper(MapMembership.class).get(secondId);
        return first != null && second != null && first.mapId != null && !first.mapId.isBlank()
            && first.mapId.equals(second.mapId);
    }

    /** 登记新实体或刷新跨门后的归属；重复稳定身份会拒绝覆盖已有实体。 */
    public void refresh(int entityId) {
        MapMembership membership = world.getMapper(MapMembership.class).get(entityId);
        PersistentIdentity identity = world.getMapper(PersistentIdentity.class).get(entityId);
        if (membership == null || identity == null) {
            forget(entityId);
            return;
        }
        if (membership.mapId == null || membership.mapId.isBlank()
            || identity.entityKey == null || identity.entityKey.isBlank()) {
            throw new IllegalArgumentException("实体地图及稳定身份不能为空：" + entityId);
        }
        int existing = resolve(identity.entityKey);
        if (existing >= 0 && existing != entityId) {
            throw new IllegalArgumentException("重复实体稳定身份：" + identity.entityKey);
        }
        Binding next = new Binding(identity.entityKey, membership.mapId,
            membership.localTag == null ? "" : membership.localTag);
        if (next.equals(bindings.get(entityId))) return;
        unbind(entityId);
        identities.put(next.entityKey(), entityId);
        bindings.put(entityId, next);
        if (!next.localTag().isBlank()) {
            localNames.computeIfAbsent(next.mapId(), ignored -> new HashMap<>())
                .computeIfAbsent(next.localTag(), ignored -> new LinkedHashSet<>()).add(entityId);
        }
        notifyChanged(entityId);
    }

    /** 真实销毁时移除索引，支持尚未收到Artemis删除通知的安全点清理。 */
    public void forget(int entityId) {
        if (unbind(entityId)) notifyChanged(entityId);
    }

    /** 内部先移除旧绑定，刷新操作仅在完整的新绑定提交后通知一次。 */
    private boolean unbind(int entityId) {
        Binding old = bindings.remove(entityId);
        if (old == null) return false;
        identities.remove(old.entityKey(), entityId);
        Map<String, Set<Integer>> names = localNames.get(old.mapId());
        if (names == null) return true;
        Set<Integer> ids = names.get(old.localTag());
        if (ids != null) {
            ids.remove(entityId);
            if (ids.isEmpty()) names.remove(old.localTag());
        }
        if (names.isEmpty()) localNames.remove(old.mapId());
        return true;
    }

    /** 主线程订阅身份或地图绑定变更；回调看到提交后的索引，注销时resolve返回-1，不回放既有实体。 */
    public void addListener(IntConsumer listener) { listeners.add(java.util.Objects.requireNonNull(listener)); }

    /** 所属玩法缓存销毁时解除订阅；监听器不属于存档数据。 */
    public void removeListener(IntConsumer listener) { listeners.remove(listener); }

    /** 变更频率远低于查询；通知快照允许回调自行退订，监听失败不回滚已提交的身份。 */
    private void notifyChanged(int entityId) {
        for (IntConsumer listener : java.util.List.copyOf(listeners)) {
            try { listener.accept(entityId); }
            catch (RuntimeException error) {
                if (com.badlogic.gdx.Gdx.app != null) com.badlogic.gdx.Gdx.app.error("MapIdentitySystem", "Identity listener failed: " + entityId, error);
                else System.err.println("Identity listener failed: " + entityId + ": " + error);
            }
        }
    }

    /** 接入Artemis延迟插入通知，和构建阶段的显式登记保持幂等。 */
    @Override protected void inserted(int entityId) { refresh(entityId); }

    /** 实体删除或失去归属组件时清理其索引。 */
    @Override protected void removed(int entityId) { forget(entityId); }

    /** 索引由生命周期事件和迁移命令维护，不每帧重建。 */
    @Override protected void processSystem() { }

    /** 会话结束后释放全部运行期索引，不保存实体编号。 */
    @Override protected void dispose() {
        identities.clear();
        localNames.clear();
        bindings.clear();
        listeners.clear();
    }
}
