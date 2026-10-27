package org.worldloom.render;

import com.artemis.World;
import org.worldloom.component.MapMembership;
import org.worldloom.system.TiledMapSystem;

/** 将世界表现限定到玩家当前观察的地图，不改变实体的逻辑更新。 */
public final class MapVisibility {
    /** 工具类无需创建实例。 */
    private MapVisibility() { }

    /** 判断实体是否属于观察图；无地图归属的旧实体保持原有表现。 */
    public static boolean inViewedMap(World world, int entityId) {
        MapMembership membership = world.getMapper(MapMembership.class).get(entityId);
        if (membership == null || membership.mapId == null || membership.mapId.isEmpty()) {
            return true;
        }
        return inViewedMap(world, membership.mapId);
    }

    /** 判断固定位置表现的所属地图；空标识表示全局UI或旧式无归属表现。 */
    public static boolean inViewedMap(World world, String mapId) {
        TiledMapSystem maps = world.getSystem(TiledMapSystem.class);
        return mapId == null || mapId.isEmpty() || maps == null || mapId.equals(maps.getCurrent());
    }
}
