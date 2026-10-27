package org.worldloom.serialize;

import com.artemis.World;
import org.worldloom.manager.map.GameSnapshotManager;
import org.worldloom.serialize.data.EntityData;
import org.worldloom.serialize.data.EntityDatum;

/**
 * @Auther WenLong
 * @Date 2025/5/13 10:14
 * @Description
 **/
public class EntityBuilder {
    private final static String TAG = EntityBuilder.class.getSimpleName();

    /** 将整张世界地图实例化；模板只允许复制单个数据并指定真实出生图。 */
    public static void buildEntities(World world,String mapName) {
        org.worldloom.Worldloom.config().requireRuntimeMap(mapName);
        EntityData entityData = GameSnapshotManager.getInstance().getEntityData(mapName);
        EntitySerializer.buildEntities(world, entityData, mapName);
    }
    public static void buildEntities(World world, EntityData entityData) {
        EntitySerializer.buildEntities(world, entityData);
    }
    public static int buildEntity(World world, EntityDatum entityDatum) {
        return EntitySerializer.buildEntity(world, entityDatum);
    }
}
