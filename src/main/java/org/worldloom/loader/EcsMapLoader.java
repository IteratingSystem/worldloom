package org.worldloom.loader;

import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.XmlReader;
import org.worldloom.manager.JsonManager;
import org.worldloom.map.CapsuleMapObject;

/** 复用原生TMX加载，只适配独立类型定义、空类、严格字段契约和引擎形状/列表扩展。 */
public class EcsMapLoader extends TmxMapLoader {
    private final ObjectMap<String, PropertyType> propertyTypes = new ObjectMap<>();

    /** 一次映射并索引Tiled自定义类型；使用同一资源解析器，支持内部资源与离线文件。 */
    public EcsMapLoader(FileHandleResolver resolver, String propTypePath) {
        super(resolver);
        PropertyType[] definitions = JsonManager.readConfig(PropertyType[].class, resolver.resolve(propTypePath));
        for (PropertyType definition : definitions) {
            if (definition == null || definition.name == null || definition.name.isBlank()
                || propertyTypes.containsKey(definition.name))
                throw new IllegalArgumentException("Invalid or duplicate Tiled property type: " + propTypePath);
            propertyTypes.put(definition.name, definition);
        }
    }

    /** 复用标准对象加载后保留胶囊身份，并同步延迟对象引用使用的编号索引。 */
    @Override
    protected void loadObject(TiledMap map, MapObjects objects, XmlReader.Element element, float heightInPixels) {
        int before = objects.getCount();
        super.loadObject(map, objects, element, heightInPixels);
        if (element.getChildByName("capsule") != null && objects.getCount() > before) {
            MapObject loaded = objects.get(before);
            if (!(loaded instanceof RectangleMapObject rectangle))
                throw new GdxRuntimeException("Conflicting capsule object shape: " + element.getAttribute("id", "?"));
            CapsuleMapObject capsule = new CapsuleMapObject(rectangle);
            objects.remove(before);
            objects.add(capsule);
            idToObject.put(element.getIntAttribute("id", 0), capsule);
        }
    }

    /** 标量、类分派和对象引用交给原生逻辑，仅补回引擎旧列表扩展的XML项目。 */
    @Override
    protected void loadProperties(MapProperties properties, XmlReader.Element element) {
        super.loadProperties(properties, element);
        if (element == null || !"properties".equals(element.getName())) return;
        for (XmlReader.Element property : element.getChildrenByName("property"))
            if ("list".equals(property.getAttribute("type", null)))
                properties.put(property.getAttribute("name"), parseListProperty(property));
    }

    /** 合并独立类型文件的默认值与显式字段；保留未知字段供组件边界报错，不被原生逻辑静默丢弃。 */
    @Override
    protected void loadClassProperties(String className, MapProperties properties, XmlReader.Element element) {
        PropertyType definition = propertyTypes.get(className);
        if (definition == null || !"class".equals(definition.type) || definition.members == null)
            throw new GdxRuntimeException("Unknown Tiled class: " + className);
        // 原生加载器插入的是类元信息，不能把它当成组件字段；真实同名字段随后从定义恢复。
        properties.remove("type");
        for (Member member : definition.members) {
            Object value = member.value;
            if (value instanceof Color color) value = new Color(color);
            else if (value instanceof Array<?> array) value = new Array<>(array);
            properties.put(member.name, value);
        }
        loadProperties(properties, element);
    }

    /** 原生不支持list和旧大写String；列表内容在XML扩展入口读取，其余类型沿用原生转换。 */
    @Override
    protected void loadBasicProperty(MapProperties properties, String name, String value, String type) {
        if (!"list".equals(type))
            super.loadBasicProperty(properties, name, value, "String".equals(type) ? "string" : type);
    }

    /** 读取列表项目，不将XML节点传入组件恢复过程。 */
    private Array<String> parseListProperty(XmlReader.Element element) {
        Array<String> result = new Array<>();
        for (XmlReader.Element item : element.getChildrenByName("item")) {
            String value = item.getAttribute("value", null);
            if (value != null) result.add(value);
        }
        return result;
    }
}
