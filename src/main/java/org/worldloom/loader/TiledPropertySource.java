package org.worldloom.loader;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import org.worldloom.manager.JsonManager;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** 将独立类型数组作为只读Tiled项目交给原生加载器；不生成文件、不自行解析默认值。 */
final class TiledPropertySource extends FileHandle {
    private final byte[] projectBytes;
    final Index index;

    /** 一次读取类型文件并建立字段名索引；原生项目文件也可直接作为来源。 */
    TiledPropertySource(FileHandle source) {
        super(source.path());
        String text = source.readString("UTF-8");
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        text = text.stripLeading();
        String project = text.startsWith("[") ? "{\"propertyTypes\":" + text + "}" : text;
        index = JsonManager.readConfig(Index.class, project);
        projectBytes = project.getBytes(StandardCharsets.UTF_8);
    }

    /** 每次返回独立的内存流，复用原生项目解析入口且支持JAR内部资源。 */
    @Override public InputStream read() { return new ByteArrayInputStream(projectBytes); }

    /** 读取边界只映射类与字段名称；默认值的类型转换及合并全部留给LibGDX。 */
    public static final class Index implements Json.Serializable {
        final ObjectMap<String, ObjectSet<String>> members = new ObjectMap<>();

        /** 映射编辑器格式中的类名和成员名，拒绝重复声明并保留空标记类。 */
        @Override public void read(Json json, JsonValue root) {
            JsonValue definitions = root.get("propertyTypes");
            if (definitions == null || !definitions.isArray())
                throw new IllegalArgumentException("Tiled propertyTypes must be an array");
            ObjectSet<String> names = new ObjectSet<>();
            for (JsonValue definition : definitions) {
                String name = definition.getString("name", "");
                if (name.isBlank() || !names.add(name))
                    throw new IllegalArgumentException("Invalid or duplicate Tiled property type: " + name);
                if (!"class".equals(definition.getString("type", ""))) continue;
                JsonValue fields = definition.get("members");
                if (fields == null || !fields.isArray())
                    throw new IllegalArgumentException("Tiled class members must be an array: " + name);
                ObjectSet<String> fieldNames = new ObjectSet<>();
                for (JsonValue field : fields) {
                    String fieldName = field.getString("name", "");
                    if (fieldName.isBlank() || !fieldNames.add(fieldName))
                        throw new IllegalArgumentException("Invalid or duplicate Tiled field: " + name + "." + fieldName);
                }
                members.put(name, fieldNames);
            }
        }

        /** 索引仅用于读取，禁止把运行缓存写回编辑器文件。 */
        @Override public void write(Json json) { throw new UnsupportedOperationException("Read-only Tiled type index"); }
    }
}
