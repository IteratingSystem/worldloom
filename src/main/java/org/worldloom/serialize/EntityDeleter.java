package org.worldloom.serialize;

import com.artemis.*;
import com.artemis.managers.TagManager;
import com.artemis.utils.Bag;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntSet;
import org.worldloom.system.MapIdentitySystem;

/** 统一删除实体；单个资源释放失败不会留下半删除状态或阻止其它资源清理。 */
public class EntityDeleter {
    public static final String TAG = EntityDeleter.class.getSimpleName();

    /** 尽力释放全部组件后删除身份、标签和实体；释放异常仅记录，避免业务误判并回滚已提交操作。 */
    public static void deleteEntity(World world, int entityId) {
        Entity entity = world.getEntity(entityId);
        Bag<Component> components = entity.getComponents(new Bag<>(Component.class));
        RuntimeException failure = null;
        for (Component component : components) {
            if (!(component instanceof Disposable disposable)) continue;
            try { disposable.dispose(); }
            catch (RuntimeException error) {
                if (failure == null) failure = error;
                else if (failure != error) failure.addSuppressed(error);
            }
        }
        MapIdentitySystem identities = world.getSystem(MapIdentitySystem.class);
        if (identities != null) identities.forget(entityId);
        TagManager tags = world.getSystem(TagManager.class);
        if (tags != null) {
            String tag = tags.getTag(entityId);
            if (tag != null) tags.unregister(tag);
        }
        world.delete(entityId);
        if (failure != null && Gdx.app != null)
            Gdx.app.error(TAG, "Component cleanup failed for deleted entity " + entityId, failure);
    }

    /** 删除全部已发布实体；先复制编号，避免释放回调改变当前订阅。 */
    public static void deleteAll(World world) { deleteAll(world, new int[0]); }

    /** 保留指定标签的实体；编号按数值比较，不受装箱整数缓存范围影响。 */
    public static void deleteAll(World world, String[] filterTags) {
        IntSet retained = new IntSet();
        TagManager tags = world.getSystem(TagManager.class);
        if (tags != null) for (String tag : filterTags) {
            Entity entity = tags.getEntity(tag);
            if (entity != null) retained.add(entity.getId());
        }
        deleteExcept(world, retained);
    }

    /** 删除除指定编号之外的实体，过滤只在本次调用内有效。 */
    public static void deleteAll(World world, int[] filterIds) {
        IntSet retained = new IntSet();
        for (int id : filterIds) retained.add(id);
        deleteExcept(world, retained);
    }

    /** 固定遍历快照，组件释放期间新增的实体不纳入当前删除批次。 */
    private static void deleteExcept(World world, IntSet retained) {
        IntBag entities = world.getAspectSubscriptionManager().get(Aspect.all()).getEntities();
        int[] snapshot = java.util.Arrays.copyOf(entities.getData(), entities.size());
        for (int id : snapshot) if (!retained.contains(id)) deleteEntity(world, id);
    }
}
