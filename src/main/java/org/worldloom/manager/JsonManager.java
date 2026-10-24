package org.worldloom.manager;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.Null;
import java.util.Objects;

/** 统一JSON对象映射；存档序列化与严格配置读取分别维护解析器策略。 */
public class JsonManager {
    private static Json json;
    private static final ThreadLocal<Json> CONFIG_JSON = ThreadLocal.withInitial(() -> {
        Json config = new Json();
        config.setTypeName(null);
        config.setIgnoreUnknownFields(false);
        return config;
    });

    /** 工具类不允许实例化。 */
    private JsonManager() {}

    /** 获取原有共享序列化器，供主线程存档及已注册的序列化回调使用。 */
    public static Json getJson() {
        if (json == null) {
            json = new Json();
        }
        return json;
    }

    /** 使用原有存档策略序列化对象，保留多态类型信息。 */
    public static String toJson(Object object){
        return getJson().toJson(object);
    }

    /** 按原有存档策略映射文本，允许JSON根节点为null。 */
    public static @Null <T> T fromJson (Class<T> type, String json) {
        return  getJson().fromJson(type,json);
    }

    /** 按原有存档策略映射文件，允许JSON根节点为null。 */
    public static @Null <T> T fromJson (Class<T> type, FileHandle fileHandle) {
        return  getJson().fromJson(type,fileHandle);
    }

    /**
     * 将配置文本直接映射为明确类型；拒绝未知字段、空根节点及多态类型元信息。
     * 配置解析器按线程复用，独立于可定制的存档解析器；字段范围与引用由调用方校验。
     */
    public static <T> T readConfig(Class<T> type, String text) {
        return Objects.requireNonNull(CONFIG_JSON.get().fromJson(type, text),
            "配置对象不能为空：" + type.getSimpleName());
    }

    /** 按UTF-8读取配置并映射对象，映射错误中保留文件位置。 */
    public static <T> T readConfig(Class<T> type, FileHandle file) {
        try {
            return readConfig(type, file.readString("UTF-8"));
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("配置加载失败：" + file.path(), error);
        }
    }
}
