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
import org.worldloom.Worldloom;
import java.util.ArrayList;
import java.util.Objects;


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
    private final FileHandleResolver resolver = new InternalFileHandleResolver();
    /** 加载、资源发现及图集索引共享的资源清单快照。 */
    public String[] assetPathList = new String[0];
    private final ManagedAssets gdxAssetManager;
    private boolean disposed;

    /** 读取资源清单；成功后创建资源容器，地图类型文件延迟至请求地图加载器时读取。 */
    private AssetManager() {
        FileHandle assetsHandle = Gdx.files.internal("assets.txt");
        if (!assetsHandle.exists()) {
            throw new IllegalStateException("Asset manifest does not exist: assets.txt");
        }
        assetPathList = parseAssetPaths(assetsHandle.readString());
        gdxAssetManager = new ManagedAssets(resolver);
    }

    /** 规范化清单换行、BOM及路径分隔符，跳过空行。 */
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

    /**
     * 获取单例实例
     * @return AssetManager 实例
     */
    public static AssetManager getInstance() {
        if (instance == null) {
            instance = new AssetManager();
        }
        return instance;
    }


    /**
     * 兼容旧入口：设置地图类型来源，同路径重复调用无副作用，不覆盖自定义加载器。
     * 默认地图加载器已创建后禁止更换来源；新项目使用WorldloomConfig配置路径。
     */
    @Deprecated(since = "4.19.1")
    public void setLoaders(String propTypePath) {
        ensureActive();
        gdxAssetManager.configurePropertyTypes(propTypePath);
    }

    /** 已释放的旧句柄明确失败，避免它访问后来创建的新资源容器。 */
    private void ensureActive() {
        if (disposed) throw new IllegalStateException("AssetManager has been disposed");
    }

    /** 显式安装业务加载器；按需默认注册只填空缺，不覆盖业务选择。 */
    public <T, P extends AssetLoaderParameters<T>> void setLoader(Class<T> type, AssetLoader<T, P> loader) {
        ensureActive();
        gdxAssetManager.setLoader(type,loader);
    }

    /** 返回借用的底层容器；直接加载及依赖加载同样按资源类型延迟注册，不应由调用方释放。 */
    public com.badlogic.gdx.assets.AssetManager getGdxAssetManager(){
        ensureActive();
        return gdxAssetManager;
    }

    /**
     * 加载载指定文件到类型
     * @param path
     * @param aClass
     * @param <T>
     */
    public <T> void loadAsset(String path, Class<T> aClass) {
        ensureActive();
        gdxAssetManager.load(path, aClass);
    }


    /**
     * 自动加载默认资源,目前包含地图与行为树文件,逻辑是靠识别特定的文件后缀,因此没有特别后缀的文件需要手动加载
     * 依赖于assets模块中的assets.txt
     */
    public void loadAssets() {
        ensureActive();
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

    /** 判断受支持的音频扩展名。 */
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
        ensureActive();
        return gdxAssetManager.get(path, aClass);
    }

    /**
     * 传入类型,获取已加载的所有此类型的对象,最后返回为ObjectMap<name, object>,其中name是文件名,非路径也无后缀
     * @param aClass
     * @return
     * @param <T>
     */
    public <T> ObjectMap<String, T> getObjects(String ext,Class<T> aClass) {
        ensureActive();
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


    /** 在主线程推进资源队列。 */
    public void update(){
        ensureActive();
        gdxAssetManager.update();
    }
    /** 返回当前资源加载进度。 */
    public float getProgress(){
        ensureActive();
        return gdxAssetManager.getProgress();
    }
    /** 幂等释放本实例资源并清除借用缓存；下次获取单例时创建独立的新容器。 */
    public void dispose() {
        if (disposed) return;
        disposed = true;
        org.worldloom.manager.map.MapManager.clearAnimationIndexes();
        SkinManager.clearCache();
        BundleManager.clearCache();
        if (instance == this) instance = null;
        try {
            gdxAssetManager.dispose();
        } finally {
            assetPathList = new String[0];
        }
    }

    /** 在原生查询入口补默认加载器，覆盖直接load及依赖加载，不为皮肤或图片预读地图配置。 */
    private static final class ManagedAssets extends com.badlogic.gdx.assets.AssetManager {
        private String propertyTypesPath;
        private String initializedTypesPath;
        private boolean disposed;

        /** 保留LibGDX自带加载器，额外的引擎加载器按类型首次查询时创建。 */
        ManagedAssets(FileHandleResolver resolver) { super(resolver); }

        /** 固定默认地图加载器的来源；重复配置不覆盖任何已有加载器。 */
        synchronized void configurePropertyTypes(String path) {
            Objects.requireNonNull(path, "Tiled property type path cannot be null");
            if (path.isBlank()) throw new IllegalArgumentException("Tiled property type path cannot be blank");
            if (initializedTypesPath != null && !initializedTypesPath.equals(path))
                throw new IllegalStateException("Tiled property type source is already initialized: " + initializedTypesPath);
            propertyTypesPath = path;
        }

        /** 先尊重包括后缀在内的自定义加载器，仅在目标类型缺省时创建对应引擎加载器。 */
        @Override public synchronized <T> AssetLoader getLoader(Class<T> type, String fileName) {
            if (disposed) throw new IllegalStateException("AssetManager has been disposed");
            AssetLoader loader = super.getLoader(type, fileName);
            if (loader != null) return loader;
            if (type == TiledMap.class) {
                String path = propertyTypesPath == null ? Worldloom.config().getTiledPropertyTypesPath() : propertyTypesPath;
                EcsMapLoader maps = new EcsMapLoader(getFileHandleResolver(), path);
                super.setLoader(TiledMap.class, maps);
                initializedTypesPath = path;
            } else if (type == BehaviorTree.class) {
                super.setLoader(BehaviorTree.class, new BehaviorTreeLoader(getFileHandleResolver()));
            } else if (type == Story.class) {
                super.setLoader(Story.class, new InkStoryLoader(getFileHandleResolver()));
            }
            return super.getLoader(type, fileName);
        }

        /** 底层容器也只释放一次；清理排队任务期间仍允许原生查询已注册加载器。 */
        @Override public synchronized void dispose() {
            if (disposed) return;
            try { super.dispose(); }
            finally { disposed = true; }
        }
    }
}
