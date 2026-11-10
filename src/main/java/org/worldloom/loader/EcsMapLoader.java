package org.worldloom.loader;

import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.XmlReader;
import org.worldloom.map.CapsuleMapObject;

/** 由原生TMX加载器合并类默认值，仅补齐类型来源、空类、字段校验及引擎形状/列表扩展。 */
public class EcsMapLoader extends TmxMapLoader {
    private final TiledPropertySource propertySource;
    private boolean projectLoaded;

    /** 保存独立类型文件的内存项目视图；默认值由首次加载地图时的原生项目解析处理。 */
    public EcsMapLoader(FileHandleResolver resolver, String propTypePath) {
        super(resolver);
        propertySource = new TiledPropertySource(resolver.resolve(propTypePath));
    }

    /** 只有配置的类型文件使用内存项目视图，地图、TSX及图片仍走调用方的解析器。 */
    @Override public FileHandle resolve(String fileName) {
        return propertySource != null && propertySource.path().equals(fileName) ? propertySource : super.resolve(fileName);
    }

    /** 一次建立原生类型索引，补回原生跳过的空类；后续地图共享只读默认值定义。 */
    @Override protected void loadProjectFile(String ignored) {
        if (projectLoaded) return;
        super.loadProjectFile(propertySource.path());
        for (var entry : propertySource.index.members)
            if (entry.value.isEmpty()) projectClassInfo.put(entry.key, new Array<>());
        projectLoaded = true;
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

    /** 原生合并默认值与覆盖；保留未声明属性供组件边界报错，避免原生静默丢弃。 */
    @Override
    protected void loadClassProperties(String className, MapProperties properties, XmlReader.Element element) {
        properties.remove("type");
        XmlReader.Element values = propertyValues(element);
        super.loadClassProperties(className, properties, values);
        var declared = propertySource.index.members.get(className);
        if (values != null) for (XmlReader.Element property : values.getChildrenByName("property"))
            if (!declared.contains(property.getAttribute("name"))) loadExtraProperty(properties, property);
    }

    /** 嵌套类的纯默认值也由原生递归读取，元信息不进入业务字段。 */
    @Override protected void loadJsonClassProperties(String className, MapProperties properties, JsonValue values) {
        properties.remove("type");
        super.loadJsonClassProperties(className, properties, values);
    }

    /** 仅在当前类的直接成员中查覆盖，防原生递归查找把子类同名字段套到父类上。 */
    @Override protected XmlReader.Element getPropertyByName(XmlReader.Element element, String name) {
        XmlReader.Element values = propertyValues(element);
        if (values != null) for (XmlReader.Element property : values.getChildrenByName("property"))
            if (name.equals(property.getAttribute("name"))) return property;
        return null;
    }

    /** 原生递归有时传入类property节点，统一取其properties容器。 */
    private static XmlReader.Element propertyValues(XmlReader.Element element) {
        return element == null || "properties".equals(element.getName()) ? element : element.getChildByName("properties");
    }

    /** 只解析原生未处理的未知字段，保留原值交给实体/区域组件的严格字段边界。 */
    private void loadExtraProperty(MapProperties properties, XmlReader.Element property) {
        String name = property.getAttribute("name"), type = property.getAttribute("type", null);
        String value = property.getAttribute("value", property.getText());
        if ("object".equals(type)) loadObjectProperty(properties, name, value);
        else if ("class".equals(type)) {
            MapProperties nested = new MapProperties();
            loadClassProperties(property.getAttribute("propertytype"), nested, property.getChildByName("properties"));
            properties.put(name, nested);
        } else if ("list".equals(type)) properties.put(name, parseListProperty(property));
        else loadBasicProperty(properties, name, value, type);
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
