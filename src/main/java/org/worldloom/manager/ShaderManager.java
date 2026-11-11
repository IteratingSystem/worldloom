package org.worldloom.manager;

import com.badlogic.gdx.Gdx;
import java.util.HashMap;
import java.util.Map;

/** 随资源容器建立Shader路径索引与源码缓存，不持有GL对象。 */
public class ShaderManager {
    private static final String TAG = ShaderManager.class.getSimpleName();
    private static ShaderManager instance;
    public static final String VERTEX_EXT = ".vert", FRAGMENT_EXT = ".frag";
    public String[] assetPathList;
    private final AssetManager owner;
    private final Map<String, String> paths = new HashMap<>(), sources = new HashMap<>();

    /** 只登记当前容器中的Shader，完整路径和目录后缀沿用首次匹配优先。 */
    private ShaderManager() {
        owner = AssetManager.getInstance();
        assetPathList = owner.assetPathList;
        for (String path : assetPathList) {
            if (!path.endsWith(VERTEX_EXT) && !path.endsWith(FRAGMENT_EXT)) continue;
            paths.putIfAbsent(path, path);
            for (int slash = path.indexOf('/'); slash >= 0; slash = path.indexOf('/', slash + 1))
                paths.putIfAbsent(path.substring(slash + 1), path);
        }
    }

    /** 获取当前资源会话的只读查询入口。 */
    public static ShaderManager getInstance() {
        if (instance == null) instance = new ShaderManager();
        return instance;
    }

    /** 取得顶点源码；首次读取后复用字符串，资源重建时缓存失效。 */
    public String getVertexContext(String name) { return getContext(name, VERTEX_EXT, "vertex"); }

    /** 取得片元源码；游戏资源优先，无匹配时读取引擎内置路径。 */
    public String getFragmentContext(String name) { return getContext(name, FRAGMENT_EXT, "fragment"); }

    /** 查询前确认所属容器仍有效，不让旧句柄借用新会话的资源。 */
    private String getContext(String name, String extension, String shaderType) {
        owner.getGdxAssetManager();
        String filename = name + extension;
        String cached = sources.get(filename);
        if (cached != null) return cached;
        var file = Gdx.files.internal(paths.getOrDefault(filename, filename));
        if (file.exists()) {
            String text = file.readString("UTF-8");
            sources.put(filename, text);
            return text;
        }
        Gdx.app.error(TAG, "Failed to get " + shaderType + " shader context: " + name);
        return null;
    }

    /** 资源容器退出时清除路径与文本，不涉及任何ShaderProgram的释放。 */
    static void clearCache() {
        if (instance != null) { instance.paths.clear(); instance.sources.clear(); instance.assetPathList = new String[0]; }
        instance = null;
    }
}
