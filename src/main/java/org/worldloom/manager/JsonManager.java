package org.worldloom.manager;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.Null;
import com.badlogic.gdx.utils.SerializationException;
import java.math.BigDecimal;
import java.util.Objects;

/** 统一JSON对象映射；存档序列化与严格配置读取分别维护解析器策略。 */
public class JsonManager {
    private static Json json;
    private static final ThreadLocal<Json> CONFIG_JSON = ThreadLocal.withInitial(() -> {
        Json config = new ConfigJson();
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
     * 将配置文本直接映射为明确类型；拒绝未知字段、空根节点、多态元信息及有损标量转换。
     * 配置解析器按线程复用，独立于可定制的存档解析器；字段范围与引用由调用方校验。
     */
    public static <T> T readConfig(Class<T> type, String text) {
        return Objects.requireNonNull(CONFIG_JSON.get().fromJson(type, text),
            "Config root must not be null: " + type.getSimpleName());
    }

    /** 按UTF-8读取配置并映射对象，映射错误中保留文件位置。 */
    public static <T> T readConfig(Class<T> type, FileHandle file) {
        try {
            return readConfig(type, file.readString("UTF-8"));
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Failed to load config: " + file.path(), error);
        }
    }

    /** 仅在配置映射边界检查标量；不改变共享存档解析器或业务DTO的字段结构。 */
    private static final class ConfigJson extends Json {
        /** 为配置解析器建立独立策略实例。 */
        private ConfigJson() {}

        /** 嵌套对象、数组和字典均经过同一标量检查，错误附带JSON字段位置。 */
        @Override @SuppressWarnings({"unchecked", "rawtypes"})
        public <T> T readValue(Class<T> type, Class elementType, JsonValue value) {
            if (type == null || value == null) return super.readValue(type, elementType, value);
            try {
                if (value.isNull()) {
                    if (type.isPrimitive()) throw new IllegalArgumentException("Primitive config value must not be null");
                    return super.readValue(type, elementType, value);
                }
                if (type == boolean.class || type == Boolean.class) {
                    if (!value.isBoolean()) throw new IllegalArgumentException("Expected a JSON boolean");
                    return (T) Boolean.valueOf(value.asBoolean());
                }
                if (type == byte.class || type == Byte.class || type == short.class || type == Short.class
                    || type == int.class || type == Integer.class || type == long.class || type == Long.class
                    || type == float.class || type == Float.class || type == double.class || type == Double.class) {
                    if (!value.isNumber()) throw new IllegalArgumentException("Expected a JSON number");
                    String number = value.asString();
                    if (type == float.class || type == Float.class) {
                        float result = Float.parseFloat(number);
                        if (!Float.isFinite(result)) throw new IllegalArgumentException("Expected a finite float");
                        return (T) Float.valueOf(result);
                    }
                    if (type == double.class || type == Double.class) {
                        double result = Double.parseDouble(number);
                        if (!Double.isFinite(result)) throw new IllegalArgumentException("Expected a finite double");
                        return (T) Double.valueOf(result);
                    }
                    BigDecimal exact = new BigDecimal(number);
                    if (type == byte.class || type == Byte.class) return (T) Byte.valueOf(exact.byteValueExact());
                    if (type == short.class || type == Short.class) return (T) Short.valueOf(exact.shortValueExact());
                    if (type == int.class || type == Integer.class) return (T) Integer.valueOf(exact.intValueExact());
                    return (T) Long.valueOf(exact.longValueExact());
                }
            } catch (IllegalArgumentException | ArithmeticException cause) {
                SerializationException error = new SerializationException("Invalid config value for " + type.getSimpleName(), cause);
                error.addTrace(value.trace());
                throw error;
            }
            return super.readValue(type, elementType, value);
        }
    }
}
