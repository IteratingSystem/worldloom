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
import com.badlogic.gdx.utils.Json;
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
    //tiled中"自定义属性"的导出文件地址
    private final String propTypePath;
    private PropertyType[] propertyTypes;
    private FileHandle propClassHandle;
    public EcsMapLoader(FileHandleResolver resolver, String propTypePath){
        super(resolver);
        this.propTypePath = propTypePath;

        propClassHandle = Gdx.files.internal(propTypePath);
        Json json = JsonManager.getJson();
        propertyTypes = json.fromJson(PropertyType[].class,propClassHandle);
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

                //读取自定义类型文件赋予初始值
                if (propClassHandle.exists()) {
                    for (PropertyType propertyType : propertyTypes) {
                        if (!propertyType.name.equals(name)) {
                            continue;
                        }
                        for (Member member : propertyType.members) {
                            String n = member.name;
                            Object v = member.value;
                            String t = member.type;
                            childProps.put(n, v);
                        }
                    }
                }else {
                    Gdx.app.error(TAG,"The custom type export file in Tiled does not exist!Path:"+propTypePath);
                }


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
