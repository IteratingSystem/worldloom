package org.worldloom.loader;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.XmlReader;
import org.worldloom.manager.JsonManager;
import org.worldloom.map.CapsuleMapObject;


/**
 * @Auther WenLong
 * @Date 2025/2/13 9:59
 * @Description 重写瓦片地图加载器,主要目的是让地图中对象的自定义属性实现层级关系
 **/
public class EcsMapLoader extends TmxMapLoader {
    private final static String TAG = EcsMapLoader.class.getSimpleName();
    private final ObjectMap<String, PropertyType> propertyTypes = new ObjectMap<>();

    /** 一次映射并索引Tiled自定义类型；缺文件或重复定义立即阻止加载。 */
    public EcsMapLoader(FileHandleResolver resolver, String propTypePath){
        super(resolver);
        PropertyType[] definitions = JsonManager.readConfig(PropertyType[].class, Gdx.files.internal(propTypePath));
        for (PropertyType definition : definitions) {
            if (definition == null || definition.name == null || definition.name.isBlank()
                || propertyTypes.containsKey(definition.name))
                throw new IllegalArgumentException("Invalid or duplicate Tiled property type: " + propTypePath);
            propertyTypes.put(definition.name, definition);
        }
    }

    /** 复用标准对象加载后保留胶囊形身份，并同步编号引用，防止被当成矩形区域。 */
    @Override
    protected void loadObject(TiledMap map, MapObjects objects, XmlReader.Element element, float heightInPixels) {
        int before = objects.getCount();
        super.loadObject(map, objects, element, heightInPixels);
        if (element.getChildByName("capsule") != null && objects.getCount() > before) {
            MapObject loaded = objects.get(before);
            if (!(loaded instanceof RectangleMapObject rectangle))
                throw new GdxRuntimeException("胶囊对象形状冲突：" + element.getAttribute("id", "?"));
            CapsuleMapObject capsule = new CapsuleMapObject(rectangle);
            objects.remove(before);
            objects.add(capsule);
            idToObject.put(element.getIntAttribute("id", 0), capsule);
        }
    }

    /** 加载普通属性和类默认值；类按声明类型查索引，保留属性自身的名称。 */
    @Override
    protected void loadProperties (final MapProperties properties, XmlReader.Element element) {
        if (element == null) return;
        if (!element.getName().equals("properties")) return;

        for (XmlReader.Element property : element.getChildrenByName("property")) {
            final String name = property.getAttribute("name", null);
            String value = property.getAttribute("value", null);
            String type = property.getAttribute("type", null);
            if (value == null) {
                value = property.getText();
            }
            if ("object".equals(type)) {
                // Wait until the end of [loadTiledMap] to fetch the object
                try {
                    // Value should be the id of the object being pointed to
                    final int id = Integer.parseInt(value);
                    // Create [Runnable] to fetch object and add it to props
                    Runnable fetch = new Runnable() {
                        @Override
                        public void run () {
                            MapObject object = idToObject.get(id);
                            properties.put(name, object);
                        }
                    };
                    // [Runnable] should not run until the end of [loadTiledMap]
                    runOnEndOfLoadTiled.add(fetch);
                } catch (Exception exception) {
                    Gdx.app.error(TAG,"Error parsing property [" + name + "] of type \"object\" with value: [" + value + "]");
                    throw new GdxRuntimeException(
                        "Error parsing property [" + name + "] of type \"object\" with value: [" + value + "]", exception);
                }
            }else if ("class".equals(type)) {
                MapProperties childProps = new MapProperties();

                String declaredType = property.getAttribute("propertytype", name);
                PropertyType definition = propertyTypes.get(declaredType);
                if (definition == null || !"class".equals(definition.type) || definition.members == null)
                    throw new GdxRuntimeException("Unknown Tiled class: " + declaredType + ", property: " + name);
                for (Member member : definition.members) childProps.put(member.name, member.value);


                //读取Tiled中配置的值
                for (XmlReader.Element child : property.getChildrenByName("properties")) {
                    if (child == null) continue;
                    for (XmlReader.Element cProperty : child.getChildrenByName("property")){
                        String n = cProperty.getAttribute("name", null);
                        Object v = cProperty.getAttribute("value", null);
                        String t = cProperty.getAttribute("type", null);
                        if (v == null && cProperty.getText()!=null && !cProperty.getText().isBlank()){
                            v = cProperty.getText();
                        }
                        if ("list".equals(t)){
                            v = parseListProperty(cProperty);
                        }
                        Object castValue = castProperty(n, v, t);

                        childProps.put(n, castValue);
                    }
                }
                properties.put(name, childProps);
            } else {
                Object castValue = castProperty(name, value, type);
                properties.put(name, castValue);
            }
        }
    }


    /** 读取Tiled列表项目，不将XML节点传入实体恢复逻辑。 */
    private Array<String> parseListProperty(XmlReader.Element listElement) {
        Array<String> result = new Array<>();
        for (XmlReader.Element item : listElement.getChildrenByName("item")) {
            String value = item.getAttribute("value", null);
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    /** 沿用Tiled标量转换，并保留引擎的列表扩展。 */
    protected Object castProperty (String name, Object value, String type) {
        if (type == null) {
            return value;
        }else if (type.equals("String") || type.equals("string")) {
            return value.toString();
        }else if (type.equals("list")) {
            if (value instanceof Array) {
                return value;
            }
            if (value instanceof XmlReader.Element element) {
                return parseListProperty(element);
            }
        }else {
            return super.castProperty(name,value.toString(),type);
        }
        return null;
    }

}
