package org.worldloom.manager.map;

import com.artemis.utils.Bag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TiledMapTileSet;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import org.worldloom.manager.AssetManager;
import org.worldloom.serialize.EntitySerializer;
import org.worldloom.serialize.data.EntityData;

/**
 * @Auther WenLong
 * @Date 2025/7/9 16:36
 * @Description 地图管理器
 **/
public class MapManager {
    private static String TAG = MapManager.class.getSimpleName();
    private static String EXT = ".tmx";
    private static MapManager instance;
    private ObjectMap<String, TiledMap> allMaps;
    private ObjectMap<String, String> entityLayerNames;
    private ObjectMap<String, String[]> phyLayerNames;
    private ObjectMap<String, MapLayer> entityLayers;
    private ObjectMap<String, MapObjects> allMapObjects;
    //每张地图对应的实体数据，作为原型，地图加载时默认存在的所有实体，也就是新游戏时创建实体的依据
    private ObjectMap<String, EntityData> protoEntityData;
    private Bag<TiledMapTileSet> tileSets;


    private MapManager(ObjectMap<String, String> entityLayerNames,
                       ObjectMap<String, String[]> phyLayerNames,
                       ObjectMap<String, String[]> hiddenLayerNames){
        this.entityLayerNames = entityLayerNames;
        this.phyLayerNames = phyLayerNames;
        allMaps = AssetManager.getInstance().getObjects(EXT,TiledMap.class);
        allMapObjects = new ObjectMap<>();
        protoEntityData =new ObjectMap<>();
        tileSets = new Bag<>();
        Array<String> mapKeys = allMaps.keys().toArray();
        for (String mapName : mapKeys) {
            TiledMap tiledMap = getTiledMap(mapName);
            fillSplit(tiledMap);
            String[] hiddenLayers = hiddenLayerNames.get(mapName);
            if (hiddenLayers != null) {
                for (String layerName : hiddenLayers) {
                    MapLayer layer = tiledMap.getLayers().get(layerName);
                    if (layer == null) {
                        Gdx.app.error(TAG, "Hidden layer is missing: "
                            + mapName + "/" + layerName);
                        continue;
                    }
                    layer.setVisible(false);
                }
            }

            if (!entityLayerNames.containsKey(mapName)) {
                Gdx.app.debug(TAG,"Failed to get map entity,Not set 'entityLayerNames' with tiled map,map name: "+mapName);
                continue;
            }
            String entityLayerName = entityLayerNames.get(mapName);
            MapLayer entityLayer = tiledMap.getLayers().get(entityLayerName);
            MapObjects mapObjects = entityLayer.getObjects();
            allMapObjects.put(mapName,mapObjects);
            protoEntityData.put(mapName,EntitySerializer.createEntityData(mapName,mapObjects));
            for (TiledMapTileSet tileSet : tiledMap.getTileSets()) {
                if (tileSets.contains(tileSet)) {
                    continue;
                }
                tileSets.add(tileSet);
            }
        }
    }
    public static synchronized void init(ObjectMap<String, String> entityLayerNames,ObjectMap<String, String[]> phyLayerNames){
        init(entityLayerNames, phyLayerNames, new ObjectMap<>());
    }
    /** 初始化地图，并按 hiddenLayerNames 隐藏普通渲染层；显式采样仍可读取瓦片。 */
    public static synchronized void init(ObjectMap<String, String> entityLayerNames,
                                         ObjectMap<String, String[]> phyLayerNames,
                                         ObjectMap<String, String[]> hiddenLayerNames){
        if (instance != null){
            return;
        }
        instance = new MapManager(entityLayerNames, phyLayerNames, hiddenLayerNames);
    }
    public static MapManager getInstance(){
        if (instance == null) {
            throw new IllegalStateException(TAG+" is not initialized.Please run "+TAG+".init()!");
        }
        return instance;
    }
    /** Creates an isolated copy of the Tiled-map prototypes for a new game. */
    public ObjectMap<String, EntityData> createInitialEntityData(){
        ObjectMap<String, EntityData> entityData = new ObjectMap<>();
        for (ObjectMap.Entry<String, EntityData> entry : protoEntityData) {
            String json = EntitySerializer.toJson(entry.value);
            entityData.put(entry.key, EntitySerializer.toEntityBag(json));
        }
        return entityData;
    }

    /** @deprecated Use {@link #createInitialEntityData()}. */
    @Deprecated
    public ObjectMap<String, EntityData> getProtoEntityDate(){
        return createInitialEntityData();
    }


    public TiledMap getTiledMap(String mapName){
        if (!allMaps.containsKey(mapName)){
            Gdx.app.error(TAG,"Failed to getTiledMap,allMaps is not contains is map,name:"+mapName);
            return null;
        }
        TiledMap tiledMap = allMaps.get(mapName);
        return tiledMap;
    }
    public MapObjects getMapObjects(String mapName){
        if (!allMapObjects.containsKey(mapName)) {
            Gdx.app.error(TAG,"Failed to getMapObjects,allMapObjects is not contains mapName:"+mapName);
            return null;
        }
        return allMapObjects.get(mapName);
    }
    public MapObject getMapObject(String mapName,int objectId){
        MapObjects mapObjects = getMapObjects(mapName);
        for (MapObject mapObject : mapObjects) {
            Integer id = mapObject.getProperties().get("id", -1, Integer.class);
            if (objectId == id){
                return mapObject;
            }
        }
        Gdx.app.error(TAG,"Failed to getMapObject,No objects with the same id,objectId:"+objectId);
        return null;
    }
    public String[] getPhyLayerName(String mapName){
        if (!phyLayerNames.containsKey(mapName)){
            Gdx.app.debug(TAG,"Failed to getPhyLayerName,phyLayerNames is not contains mapName:"+mapName);
            return null;
        }
        return phyLayerNames.get(mapName);
    }
    public MapLayer[] getPhyLayer(String mapName){
        String[] phyLayerNames = getPhyLayerName(mapName);
        if (phyLayerNames == null) {
            Gdx.app.debug(TAG, "Failed to getPhyLayer,phyLayerNames is not contains mapName:" + mapName);
            return null;
        }
        TiledMap tiledMap = getTiledMap(mapName);
        MapLayer[] mapLayers = new MapLayer[phyLayerNames.length];
        for (int i = 0; i < phyLayerNames.length; i++) {
            mapLayers[i] = tiledMap.getLayers().get(phyLayerNames[i]);
        }
        return mapLayers;
    }

    public TiledMapTileSet getTileSet(TiledMapTile tiledMapTile) {
        for (TiledMapTileSet tileSet : tileSets) {
            if (tileSet.getTile(tiledMapTile.getId()) == tiledMapTile) {
                return tileSet;
            }
        }
        return null;
    }

    //填充图像边缘区域,修复地图分裂问题
    private void fillSplit(TiledMap tiledMap){
        //解决地图开裂的bug
        for (MapLayer layer : tiledMap.getLayers()) {
            if (layer instanceof TiledMapTileLayer tileLayer) {
                for (int x = 0; x < tileLayer.getWidth(); x++) {
                    for (int y = 0; y < tileLayer.getHeight(); y++) {
                        TiledMapTileLayer.Cell cell = tileLayer.getCell(x, y);
                        if (cell != null) {
                            TextureRegion region = cell.getTile().getTextureRegion();
                            fillSplit(region);
                        }
                    }
                }
            }
        }
    }
    private void fillSplit(TextureRegion[][] region) {
        for (TextureRegion[] array : region) {
            for (TextureRegion texture : array) {
                fillSplit(texture);
            }
        }
    }
    private void fillSplit(TextureRegion region) {
        // 填充值
        float fix = 0.01f;
        float x = region.getRegionX();
        float y = region.getRegionY();
        float width = region.getRegionWidth();
        float height = region.getRegionHeight();
        float invTexWidth = 1f / region.getTexture().getWidth();
        float invTexHeight = 1f / region.getTexture().getHeight();
        region.setRegion((x + fix) * invTexWidth, (y + fix) * invTexHeight, (x + width - fix) * invTexWidth, (y + height - fix) * invTexHeight); // Trims                                                                                                                                      // region
    }
}
