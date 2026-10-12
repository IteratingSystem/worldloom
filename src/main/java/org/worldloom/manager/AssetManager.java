package org.worldloom.manager;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.badlogic.gdx.ai.btree.utils.BehaviorTreeLoader;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.loaders.AssetLoader;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.bladecoder.ink.runtime.Story;
import org.worldloom.loader.EcsMapLoader;
import org.worldloom.loader.InkStoryLoader;
import org.worldloom.audio.AudioAssetPath;
import java.util.ArrayList;


/**
 * @Auther WenLong
 * @Date 2025/2/11 16:23
 * @Description 资源管理器
 **/
public class AssetManager {
    private static final String TAG = AssetManager.class.getSimpleName();
    public static final String TILED_EXT = ".tmx";
    public static final String TREE_EXT = ".tree";
    public static final String STORY_EXT = ".ink.json";
    public static final String NOISE_EXT = ".noise.png";
    public static final String TEXTURE_EXT = ".png";
    public static final String SOUND_DIR = AudioAssetPath.SOUND_DIRECTORY;
    public static final String MUSIC_DIR = AudioAssetPath.MUSIC_DIRECTORY;
    private static AssetManager instance;
    private static FileHandleResolver resolver;
    /** Canonical manifest snapshot shared by loading, discovery and shader/part indexes. */
    public String[] assetPathList = new String[0];
    private static com.badlogic.gdx.assets.AssetManager gdxAssetManager;

    private AssetManager() {
        // 私有构造器，防止外部直接实例化
        FileHandle assetsHandle = Gdx.files.internal("assets.txt");
        if (!assetsHandle.exists()) {
            Gdx.app.error(TAG,"assets.txt is not exists!");
            return;
        }
        assetPathList = parseAssetPaths(assetsHandle.readString());
    }

    private static String[] parseAssetPaths(String manifest) {
        ArrayList<String> paths = new ArrayList<>();
        for (String line : manifest.split("\\R")) {
            String path = line.trim();
            if (path.startsWith("\uFEFF")) path = path.substring(1).trim();
            path = path.replace('\\', '/');
            if (!path.isEmpty()) paths.add(path);
        }
        return paths.toArray(String[]::new);
    }
    private static void initialize(){
        resolver = new InternalFileHandleResolver();
        instance = new AssetManager();
        gdxAssetManager = new com.badlogic.gdx.assets.AssetManager(resolver);
    }

    /**
     * 获取单例实例
     * @return AssetManager 实例
     */
    public static AssetManager getInstance() {
        if (instance == null) {
            initialize();
        }
        return instance;
    }


    public void setLoaders(String propTypePath) {
        gdxAssetManager.setLoader(TiledMap.class, new EcsMapLoader(resolver,propTypePath));
        gdxAssetManager.setLoader(BehaviorTree.class, new BehaviorTreeLoader(resolver));
        gdxAssetManager.setLoader(Story.class, new InkStoryLoader(resolver));
    }
    public <T, P extends AssetLoaderParameters<T>> void setLoader(Class<T> type, AssetLoader<T, P> loader) {
        gdxAssetManager.setLoader(type,loader);
    }

    public com.badlogic.gdx.assets.AssetManager getGdxAssetManager(){
        return gdxAssetManager;
    }

    /**
     * 加载载指定文件到类型
     * @param path
     * @param aClass
     * @param <T>
     */
    public <T> void loadAsset(String path, Class<T> aClass) {
        gdxAssetManager.load(path, aClass);
    }


    /**
     * 自动加载默认资源,目前包含地图与行为树文件,逻辑是靠识别特定的文件后缀,因此没有特别后缀的文件需要手动加载
     * 依赖于assets模块中的assets.txt
     */
    public void loadAssets() {
        for (String path : assetPathList) {
            if (path.startsWith(SOUND_DIR) && isAudioPath(path)) {
                loadAsset(path, Sound.class);
                continue;
            }
            if (path.startsWith(MUSIC_DIR) && isAudioPath(path)) {
                loadAsset(path, Music.class);
                continue;
            }
            if (path.endsWith(TILED_EXT)) {
                loadAsset(path,TiledMap.class);
                continue;
            }
            if (path.endsWith(TREE_EXT)) {
                loadAsset(path,BehaviorTree.class);
                continue;
            }
            if (path.endsWith(STORY_EXT)) {
                loadAsset(path,Story.class);
                continue;
            }
            if (path.endsWith(NOISE_EXT)) {
                loadAsset(path, Texture.class);
                continue;
            }
            if (path.endsWith(TEXTURE_EXT)) {
                loadAsset(path, Texture.class);
                continue;
            }
        }
    }

    private boolean isAudioPath(String path) {
        String lowerPath = path.toLowerCase();
        return lowerPath.endsWith(".ogg") || lowerPath.endsWith(".wav")
            || lowerPath.endsWith(".mp3");
    }


    /**
     * gdxAssetManager.get的套娃,用于获取已经加载过的资源得到对象
     * @param path
     * @param aClass
     * @return
     * @param <T>
     */
    public <T> T getObejct(String path,Class<T> aClass) {
        return gdxAssetManager.get(path, aClass);
    }

    /**
     * 传入类型,获取已加载的所有此类型的对象,最后返回为ObjectMap<name, object>,其中name是文件名,非路径也无后缀
     * @param aClass
     * @return
     * @param <T>
     */
    public <T> ObjectMap<String, T> getObjects(String ext,Class<T> aClass) {
        ObjectMap<String, T> objectMap = new ObjectMap<>();
        FileHandle fileHandle;
        for (String path : assetPathList) {
            if (path.endsWith(ext)) {
                fileHandle = Gdx.files.internal(path);
                if (!fileHandle.exists()){
                    Gdx.app.log(TAG,"getObjects continue: fileHandle is exists,path: "+path);
                    continue;
                }
                T obejct = getObejct(path, aClass);
                String name = fileHandle.name().replace(ext,"");
                objectMap.put(name,obejct);
            }
        }

        return objectMap;
    }


    public void update(){
        gdxAssetManager.update();
    }
    public float getProgress(){
        return gdxAssetManager.getProgress();
    }
    /**
     * 释放资源
     */
    public void dispose() {

        if (gdxAssetManager != null) {
            gdxAssetManager.dispose();
        }
    }
}
