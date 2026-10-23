package org.worldloom.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * 图层采样工具
 */
public class SamplingUtils {
    private static final String TAG = SamplingUtils.class.getSimpleName();
    private static SamplingUtils instance;
    private final ObjectMap<Texture, FrameBuffer> buffers = new ObjectMap<>();
    private OrthogonalTiledMapRenderer mapRenderer;
    private OrthographicCamera camera;

    private SamplingUtils(){
        camera = new OrthographicCamera();
        mapRenderer = new OrthogonalTiledMapRenderer(null,1);

    }

    public static SamplingUtils getInstance(){
        if (instance == null){
            instance = new SamplingUtils();
        }
        return instance;
    }

    public TextureRegion samplingLayer(TiledMap tiledMap,String layerName){
        return samplingLayer(tiledMap,layerName,Texture.TextureFilter.Nearest,Texture.TextureFilter.Nearest);
    }
    public TextureRegion samplingLayer(TiledMap tiledMap,String layerName,Texture.TextureFilter minFilter, Texture.TextureFilter magFilter){
        TiledMapTileLayer mapLayer = null;
        try{
            mapLayer = (TiledMapTileLayer)tiledMap.getLayers().get(layerName);
        } catch (Exception e) {
            Gdx.app.error(TAG,"Failed to samplingLayer,layerName is '"+layerName+"'");
            return null;
        }


        if (mapLayer == null) throw new IllegalArgumentException("Missing tile layer: " + layerName);
        int width = mapLayer.getWidth()*mapLayer.getTileWidth();
        int height = mapLayer.getHeight()*mapLayer.getTileHeight();
        FrameBuffer fbo = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        camera.setToOrtho(true, width, height);
        camera.update();
        mapRenderer.setMap(tiledMap);
        mapRenderer.setView(camera);

        try {
            fbo.begin();
            try {
                Gdx.gl.glClearColor(0, 0, 0, 0);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                mapRenderer.getBatch().begin();
                try { mapRenderer.renderTileLayer(mapLayer); }
                finally { mapRenderer.getBatch().end(); }
            } finally { fbo.end(); }
            Texture texture = fbo.getColorBufferTexture();
            texture.setFilter(minFilter, magFilter);
            TextureRegion result = new TextureRegion(texture);
            buffers.put(texture, fbo);
            return result;
        } catch (RuntimeException | Error failure) {
            fbo.dispose();
            throw failure;
        }
    }

    /** 释放一次采样结果及其帧缓冲；同一结果重复释放无副作用，不触碰资源管理器纹理。 */
    public void release(TextureRegion region) {
        if (region == null) return;
        FrameBuffer buffer = buffers.remove(region.getTexture());
        if (buffer != null) buffer.dispose();
    }

    /** 应用退出时释放剩余采样结果和工具自有批次；例如在全部engine.dispose()之后调用。 */
    public static void disposeInstance() {
        if (instance == null) return;
        for (FrameBuffer buffer : instance.buffers.values()) buffer.dispose();
        instance.buffers.clear();
        instance.mapRenderer.dispose();
        instance = null;
    }
}
