package org.worldloom.manager;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.I18NBundleLoader;
import com.badlogic.gdx.utils.I18NBundle;

import java.util.Locale;


/**
 * @Auther WenLong
 * @Date 2025/6/18 17:20
 * @Description 多语言Bundle管理器
 **/
public class BundleManager {
    private final static String TAG = BundleManager.class.getSimpleName();
    private static I18NBundle bundle;
    private static AssetManager assetManager;
    private static String path;
    public static void initialize(AssetManager assetManager,String path,String language){
        BundleManager.assetManager = assetManager;
        BundleManager.path = path;

        I18NBundleLoader.I18NBundleParameter bundleParameter = new I18NBundleLoader.I18NBundleParameter(Locale.of(language));
        assetManager.load(path, I18NBundle.class,bundleParameter);
        assetManager.finishLoading();
        bundle = assetManager.get(path, I18NBundle.class);
    }
    public static void changeLanguage(String language){
        I18NBundleLoader.I18NBundleParameter bundleParameter = new I18NBundleLoader.I18NBundleParameter(Locale.of(language));
        assetManager.unload(path);
        assetManager.load(path, I18NBundle.class,bundleParameter);
        assetManager.finishLoading();
        bundle = assetManager.get(path, I18NBundle.class);
    }

    public static I18NBundle getBundle(){
        if (bundle == null){
            Gdx.app.error(TAG,"bundle is null,please use BundleManager.initialize(AssetManager assetManager,String path)!");
        }
        return bundle;
    }

    public static String get(String key){
        return getBundle().get(key);
    }

    /** 资源容器释放时清除借用引用，防止重建前继续使用旧容器切换语言。 */
    static void clearCache() {
        bundle = null;
        assetManager = null;
        path = null;
    }
}
