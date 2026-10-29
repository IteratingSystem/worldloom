package org.worldloom.system;

import com.artemis.BaseSystem;
import org.worldloom.Worldloom;
import org.worldloom.map.MapAreas;
import java.util.HashMap;
import java.util.Map;

/** 当前World的静态区域查询入口；按明确地图索引，不逐帧扫描、不创建区域实体。 */
public final class MapAreaSystem extends BaseSystem {
    private TiledMapSystem tiledMapSystem;
    private final Map<String,MapAreas> maps = new HashMap<>();

    /** 在玩法系统初始化前预加载明确登记的区域，配置错误尽早定位到地图与图层。 */
    @Override protected void initialize() {
        for(var entry:Worldloom.config().getAreaLayers())
            maps.put(entry.key,new MapAreas(entry.key,tiledMapSystem.getTiledMap(entry.key),entry.value));
    }

    /** 指定运行地图获取索引；未登记区域的地图返回空索引，不回退到玩家观察图。 */
    public MapAreas map(String mapId) {
        Worldloom.config().requireRuntimeMap(mapId);
        return maps.computeIfAbsent(mapId,id->new MapAreas(id,tiledMapSystem.getTiledMap(id)));
    }

    /** 按实体实际归属查询，供组件和跨地图系统共用。 */
    public MapAreas forEntity(int entityId) {
        return map(world.getSystem(MapIdentitySystem.class).getMapId(entityId));
    }

    /** 静态区域只在加载时建立索引，正常帧无需更新。 */
    @Override protected void processSystem() { }

    /** 世界关闭时释放索引，不持有跨会话地图资源。 */
    @Override protected void dispose() { maps.clear(); }
}
