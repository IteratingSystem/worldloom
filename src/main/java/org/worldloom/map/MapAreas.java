package org.worldloom.map;

import com.badlogic.gdx.maps.MapGroupLayer;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapLayers;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TiledMapImageLayer;
import com.badlogic.gdx.math.Rectangle;
import java.util.*;

/** 单张地图的只读区域索引；显式对象层才参与，隐藏状态不改变区域规则。 */
public final class MapAreas {
    private final List<MapArea> all;
    private final Map<String,List<MapArea>> layers = new LinkedHashMap<>();
    private final Map<String,List<MapArea>> names = new HashMap<>();
    private final Map<Integer,MapArea> ids = new LinkedHashMap<>();

    /** 从已加载地图构建索引，登记层缺失、歧义或不是对象层时立即拒绝。 */
    public MapAreas(String mapId,TiledMap map,String... layerNames) {
        this(mapId, map, false, layerNames);
    }

    /** 收集任意对象层的有效面积形状，供业务按对象组件筛选，不要求登记图层。 */
    public static MapAreas fromShapes(String mapId, TiledMap map) {
        return new MapAreas(mapId, map, true);
    }

    /** 显式登记保持严格校验；自动模式跳过瓦片、点、折线及无面积的对象。 */
    private MapAreas(String mapId, TiledMap map, boolean scanAll, String... layerNames) {
        if(mapId==null || mapId.isBlank() || map==null) throw new IllegalArgumentException("区域地图不能为空");
        for(String layer:layerNames) {
            if(layer==null || layer.isBlank() || layers.putIfAbsent(layer,new ArrayList<>())!=null)
                throw new IllegalArgumentException("区域层不能为空或重复："+layer);
        }
        Set<String> found = new HashSet<>();
        collect(mapId,map.getLayers(),0,0,found,scanAll);
        if(!scanAll && !found.equals(layers.keySet())) throw new IllegalArgumentException("地图缺少登记区域层："+mapId+"/"+layers.keySet());
        layers.replaceAll((key,value)->List.copyOf(value));
        names.replaceAll((key,value)->List.copyOf(value));
        all=List.copyOf(ids.values());
    }

    /** 递归累加组偏移；自动收集与显式登记共用几何快照，不创建实体或物理对象。 */
    private void collect(String mapId,MapLayers source,float parentX,float parentY,Set<String> found,boolean scanAll) {
        for(MapLayer layer:source) {
            float x=parentX+layer.getOffsetX(),y=parentY-layer.getOffsetY();
            boolean objectLayer = !(layer instanceof MapGroupLayer || layer instanceof TiledMapTileLayer || layer instanceof TiledMapImageLayer);
            if(scanAll ? objectLayer : layers.containsKey(layer.getName())) {
                if(!objectLayer || !scanAll && !found.add(layer.getName())) throw new IllegalArgumentException("区域层必须为唯一对象层："+mapId+"/"+layer.getName());
                List<MapArea> entries = layers.computeIfAbsent(layer.getName(), ignored -> new ArrayList<>());
                for(MapObject object:layer.getObjects()) {
                    if(scanAll && !hasArea(object)) continue;
                    MapArea area=new MapArea(mapId,layer.getName(),object,x,y);
                    if(ids.putIfAbsent(area.objectId(),area)!=null) throw new IllegalArgumentException("区域编号重复："+area.objectId());
                    entries.add(area);
                    if(!area.name().isEmpty()) names.computeIfAbsent(area.name(),ignored->new ArrayList<>()).add(area);
                }
            }
            if(layer instanceof MapGroupLayer group) collect(mapId,group.getLayers(),x,y,found,scanAll);
        }
    }

    /** 区分面积形状与实体瓦片、点标记等对象；实际几何仍由MapArea完整校验。 */
    private static boolean hasArea(MapObject object) {
        if (object instanceof com.badlogic.gdx.maps.objects.PolygonMapObject) return true;
        if (object instanceof com.badlogic.gdx.maps.objects.RectangleMapObject rectangle)
            return rectangle.getRectangle().width > 0 && rectangle.getRectangle().height > 0;
        if (object instanceof com.badlogic.gdx.maps.objects.EllipseMapObject ellipse)
            return ellipse.getEllipse().width > 0 && ellipse.getEllipse().height > 0;
        return object instanceof CapsuleMapObject capsule && capsule.bounds().width > 0 && capsule.bounds().height > 0;
    }

    /** 获取全部已登记区域，返回不可变集合。 */
    public List<MapArea> all() { return all; }
    /** 按图层分组获取区域，未登记或空层均返回空集合。 */
    public List<MapArea> layer(String layerName) { return layers.getOrDefault(layerName,List.of()); }
    /** 按对象名称标签获取全部同名区域，不把名称当作唯一身份。 */
    public List<MapArea> named(String name) { return names.getOrDefault(name,List.of()); }
    /** 按地图内对象编号获取精确区域，未找到返回null。 */
    public MapArea get(int objectId) { return ids.get(objectId); }
    /** 判断指定层是否至少有一个区域包含此点。 */
    public boolean contains(String layer,float x,float y) {
        for(MapArea area:layer(layer)) if(area.contains(x,y)) return true;
        return false;
    }
    /** 矩形必须完整落入同一个区域；相邻区域不自动拼接为更大许可范围。 */
    public boolean contains(String layer,Rectangle bounds) {
        for(MapArea area:layer(layer)) if(area.contains(bounds)) return true;
        return false;
    }
    /** 判断矩形是否接触指定层中的任意区域。 */
    public boolean overlaps(String layer,Rectangle bounds) {
        for(MapArea area:layer(layer)) if(area.overlaps(bounds)) return true;
        return false;
    }
}
