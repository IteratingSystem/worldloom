package org.worldloom.system;

import com.artemis.BaseSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.utils.ScreenUtils;

/**
 * @Author: WenLong
 * @Date: 2024-09-09-17:06
 * @Description: 渲染瓦片地图系统
 */

public class RenderTiledSystem extends BaseSystem {
    private final static String TAG = RenderTiledSystem.class.getSimpleName();

    //其它世界
    private TiledMapSystem tiledMapSystem;
    private B2dSystem b2dSystem;
    private CameraSystem cameraSystem;
    private RenderFrameSystem renderFrameSystem;
    private TiledMap tiledMap;
    public OrthogonalTiledMapRenderer mapRenderer;
    private float worldScale;

    public RenderTiledSystem(float worldScale){
        this.worldScale = worldScale;
    }

    /** 切图时复用渲染器及其批次，避免每次进入室内都遗留一个SpriteBatch。 */
    public void changeMap(){
        tiledMap = tiledMapSystem.getTiledMap();
        if (mapRenderer == null) mapRenderer = new OrthogonalTiledMapRenderer(tiledMap,worldScale);
        else mapRenderer.setMap(tiledMap);
    }

    @Override
    protected void initialize() {
        changeMap();
    }

    @Override
    protected void processSystem() {
        if (tiledMap == null){
            Gdx.app.error(TAG,"The variable tiledMap is null!");
            return;
        }

        ScreenUtils.clear(0.0f,0.0f,0.0f,1);
        mapRenderer.setView(cameraSystem.camera);
        mapRenderer.render();
    }

    public void renderLayer(String layerName){
        MapLayer mapLayer = tiledMap.getLayers().get(layerName);
        if (mapLayer == null) {
            Gdx.app.error(TAG,"Failed to renderLayer,Do not find layer:"+layerName);
            return;
        }

        if (mapLayer instanceof TiledMapTileLayer tiledMapTileLayer) {
            mapRenderer.setView(cameraSystem.camera);
            mapRenderer.renderTileLayer(tiledMapTileLayer);
        }
    }

    /** 只释放本系统创建的渲染器；共享TiledMap仍由AssetManager持有。 */
    @Override protected void dispose() {
        if (mapRenderer != null) {
            mapRenderer.dispose();
            mapRenderer = null;
        }
        tiledMap = null;
    }
}
