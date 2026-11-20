package org.worldloom.system;

import com.artemis.annotations.All;
import com.artemis.annotations.Exclude;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.utils.ObjectMap;
import net.mostlyoriginal.api.plugin.extendedcomponentmapper.M;
import org.worldloom.component.Inert;
import org.worldloom.component.Pos;
import org.worldloom.component.ZIndex;

/** 按实体所属地图的高度维护脚部深度，固定层级继续使用显式index。 */
@All({ZIndex.class, Pos.class})
@Exclude(Inert.class)
public class ZIndexSystem extends IteratingSystem {
    private M<Pos> mPos;
    private M<ZIndex> mZIndex;
    private TiledMapSystem tiledMapSystem;
    private MapIdentitySystem mapIdentitySystem;
    private final ObjectMap<TiledMap, Float> heights = new ObjectMap<>();

    /** 每帧每图只读取一次尺寸；切图、原位尺寸编辑和资源替换无需持久缓存失效。 */
    @Override protected void begin() { heights.clear(); }

    /** 后台实体也按自身地图排序，不受本机观察地图尺寸影响。 */
    @Override protected void process(int entityId) {
        ZIndex zIndex = mZIndex.get(entityId);
        if (!zIndex.followY) return;
        TiledMap map = tiledMapSystem.getTiledMap(mapIdentitySystem.getMapId(entityId));
        Float height = heights.get(map);
        if (height == null) { height = mapHeight(map); heights.put(map, height); }
        Pos pos = mPos.get(entityId);
        zIndex.index = height - (pos.y + zIndex.offset);
    }

    /** 首选TMX整体高度，兼容首层为对象组；手工地图没有元数据时回退首个瓦片层。 */
    private float mapHeight(TiledMap map) {
        if (map == null) throw new IllegalStateException("Depth map is missing");
        Object rows = map.getProperties().get("height");
        Object tileHeight = map.getProperties().get("tileheight");
        if (rows instanceof Number h && tileHeight instanceof Number t) return h.floatValue() * t.floatValue();
        for (MapLayer layer : map.getLayers())
            if (layer instanceof TiledMapTileLayer tiles) return (float) tiles.getHeight() * tiles.getTileHeight();
        throw new IllegalStateException("Depth map has no height metadata or tile layer");
    }
}
