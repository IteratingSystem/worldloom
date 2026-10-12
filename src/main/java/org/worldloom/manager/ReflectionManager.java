package org.worldloom.manager;

import com.badlogic.gdx.Gdx;
import org.worldloom.Worldloom;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


/**
 * 反射管理器
 *
 * <p></p>
 *
 * @author WenLong
 * @version 1.0.0
 * @date 2026/6/30 11:50
 * @see ReflectionManager
 */
public class ReflectionManager {
    public final static String TAG = ReflectionManager.class.getName();

    // 游戏项目的根类
    private static Class ROOT_CLASS_GAME;
    // 引擎项目的根类
    private final static Class ROOT_CLASS_ENGINE = Worldloom.class;
    // 单例
    private static ReflectionManager instance;

    // 缓存：根类 -> Reflections 实例（避免重复 classpath 扫描）
    private final HashMap<Class, Reflections> reflectionsCache = new HashMap<>();
    // 缓存：(rootClass, type) -> 子类型集合（避免重复 getSubTypesOf 查询）
    private final HashMap<Map.Entry<Class, Class>, Set> subTypesCache = new HashMap<>();

    // 单例限制构造器
    private ReflectionManager() {
        if (ROOT_CLASS_GAME == null) {
            Gdx.app.error(TAG, "Game root class is null,Please run 'setRootClass(Class<Object> ROOT_CLASS_GAME)' from 'ReflectionManager'");
            return;
        }
    }
    // 设置游戏项目根类,使用此管理器的游戏项目需要设置
    public static void setRootClass(Class ROOT_CLASS_GAME){
        ReflectionManager.ROOT_CLASS_GAME = ROOT_CLASS_GAME;
    }
    // 获取单例
    public static ReflectionManager getInstance() {
        if (instance == null) {
            instance = new ReflectionManager();
        }
        return instance;
    }


    // 获取游戏项目中所有继承于传入类型的类
    @SuppressWarnings("unchecked")
    public <T> Set<Class<? extends T>> getSubTypesOfWithEngineAndGame(Class<T> type) {
        Set<Class<? extends T>> subTypes = new java.util.HashSet<>(getSubTypesOfWithEngine(type));
        subTypes.addAll(getSubTypesOfWithGame(type));
        return subTypes;
    }
    @SuppressWarnings("unchecked")
    public <T> Set<Class<? extends T>> getSubTypesOfWithGame(Class<T> type) {
        Map.Entry<Class, Class> key = new AbstractMap.SimpleImmutableEntry<>(ROOT_CLASS_GAME, type);
        Set result = subTypesCache.get(key);
        if (result == null) {
            result = getScopedSubTypes(ROOT_CLASS_GAME, type);
            subTypesCache.put(key, result);
        }
        return result;
    }
    @SuppressWarnings("unchecked")
    public <T> Set<Class<? extends T>> getSubTypesOfWithEngine(Class<T> type) {
        Map.Entry<Class, Class> key = new AbstractMap.SimpleImmutableEntry<>(ROOT_CLASS_ENGINE, type);
        Set result = subTypesCache.get(key);
        if (result == null) {
            result = getScopedSubTypes(ROOT_CLASS_ENGINE, type);
            subTypesCache.put(key, result);
        }
        return result;
    }

    private <T> Set<Class<? extends T>> getScopedSubTypes(Class<?> rootClass, Class<T> type) {
        String rootPackage = rootClass.getPackageName();
        Set<Class<? extends T>> result = new java.util.HashSet<>(getReflections(rootClass).getSubTypesOf(type));
        // Reflections 会扩展外部父类，以便发现继承引擎组件的游戏类。
        // 查询结果不能将这些外部父类作为游戏包内的类返回。
        result.removeIf(candidate -> !candidate.getPackageName().equals(rootPackage)
            && !candidate.getPackageName().startsWith(rootPackage + "."));
        return result;
    }

    // 通关类来得到包含其路径与子路径内所有类的反射工具示例
    public Reflections getReflections(Class rootClass){
        return reflectionsCache.computeIfAbsent(rootClass, k -> {
            URL codeSource = k.getProtectionDomain().getCodeSource().getLocation();
            return new Reflections(new ConfigurationBuilder()
                    .addUrls(codeSource)
                    // 共享的 IDE 编译输出中可能残留旧版本或无关包中的类。
                    // 扫描仅限于根包及其子包，并严格匹配包名边界，
                    // 例如 com.game 不能匹配 com.gameextra。
                    .filterInputsBy(new FilterBuilder().includePackage(k.getPackageName()))
                    .setScanners(
                            // 扫描所有子类型
                            Scanners.SubTypes.filterResultsBy(s -> true),
                            // 扫描类上的注解
                            Scanners.TypesAnnotated
                    ));
        });
    }

    // 通过class创建其对象
    public <T> T createObject(Class<T> clazz, Class<?>[] paramTypes, Object[] paramValues) {
        try {
            Constructor<?> constructor;

            // 1. 处理 null 或空数组：明确调用无参构造
            if (paramTypes == null || paramTypes.length == 0) {
                constructor = clazz.getDeclaredConstructor();
            } else {
                constructor = clazz.getDeclaredConstructor(paramTypes);
            }

            // 2. 暴力破解访问权限（解决 private/protected 问题）
            constructor.setAccessible(true);

            // 3. 处理 null 参数值
            Object[] args = (paramValues == null) ? new Object[0] : paramValues;

            @SuppressWarnings("unchecked")
            T instance = (T) constructor.newInstance(args);
            return instance;
        } catch (Exception e) {
            Gdx.app.error(TAG, "Failed to createInstance: " + clazz.getSimpleName(), e);
            throw new RuntimeException(e);
        }
    }
}
