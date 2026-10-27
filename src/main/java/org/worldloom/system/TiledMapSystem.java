package org.worldloom.system;

import com.artemis.BaseSystem;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.utils.ObjectMap;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.worldloom.event.MapEvent;
import org.worldloom.manager.map.MapManager;

/**
 * @Auther WenLong
 * @Date 2025/7/10 11:38
 * @Description 管理当前地图
 **/
public class TiledMapSystem extends BaseSystem {
    private RenderTiledSystem renderTiledSystem;
    private B2dSystem b2dSystem;
    private String current;

    public TiledMapSystem(String mapName, ObjectMap<String, String> entityLayers,
                          ObjectMap<String, String[]> phyLayers){
        this(mapName, entityLayers, phyLayers, new ObjectMap<>());
    }
    public TiledMapSystem(String mapName, ObjectMap<String, String> entityLayers,
                          ObjectMap<String, String[]> phyLayers,
                          ObjectMap<String, String[]> hiddenLayers){
        current = mapName;
        MapManager.init(entityLayers, phyLayers, hiddenLayers);
    }
    /** 根据明确地图身份读取资源，不改变本机观察图。 */
    public TiledMap getTiledMap(String mapId) { return MapManager.getInstance().getTiledMap(mapId); }
    /** 根据明确地图身份读取瓦片碰撞层。 */
    public MapLayer[] getPhyLayer(String mapId) { return MapManager.getInstance().getPhyLayer(mapId); }
    public TiledMap getTiledMap(){
        MapManager mapManager = MapManager.getInstance();
        return mapManager.getTiledMap(current);
    }
    public MapLayer[] getPhyLayer(){
        MapManager mapManager = MapManager.getInstance();
        return mapManager.getPhyLayer(current);
    }
    public String getCurrent(){
        return current;
    }
    public void changeCurrent(String current){
        this.current = current;
    }

    private void changeMap(String mapName){
        if (!b2dSystem.isResidentMode()) b2dSystem.delTileCollider();
        changeCurrent(mapName);
        renderTiledSystem.changeMap();
        if (b2dSystem.isResidentMode()) {
            b2dSystem.selectViewedMap(mapName);
        } else {
            b2dSystem.createTileCollider();
        }
    }
    @Override
    protected void initialize() {
        super.initialize();
    }

    @Override
    protected void processSystem() {

    }
    @Subscribe
    public void onEvent(MapEvent event){
        if (event.type == MapEvent.CHANGE_MAP) {
            changeMap(event.mapName);
            return;
        }
    }
}
