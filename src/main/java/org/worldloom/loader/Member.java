package org.worldloom.loader;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;

/**
 * @Auther WenLong
 * @Date 2025/1/22 10:43
 * @Description 旧版Tiled字段转换模型，仅为外部源码兼容保留；正式地图加载不使用此转换器。
 * @deprecated 默认值应由EcsMapLoader交给LibGDX处理，不要在新代码中直接读取此模型。
 **/
@Deprecated(since = "4.19.1")
public class Member implements Json.Serializable {
    private final static String TAG = Member.class.getSimpleName();

    public String name;
    public String type;
    public Object value;
    public String propertyType;

    @Override
    public void write(Json json) {
        json.writeObjectStart();
        json.writeValue("name", name);
        json.writeValue("type", type);
        json.writeValue("propertyType", propertyType);
        json.writeValue("value", value);
        json.writeObjectEnd();
    }

    @Override
    public void read(Json json, JsonValue jsonData) {

        name = jsonData.getString("name","");
        type = jsonData.getString("type","");
        propertyType = jsonData.getString("propertyType","");

        JsonValue jsonValue = jsonData.get("value");
        switch (type) {
            case "String":
            case "string":
                value = jsonData.getString("value");
                break;
            case "float":
                value = jsonData.getFloat("value");
                break;
            case "bool":
                value = jsonData.getBoolean("value");
                break;
            case "int":
                value = jsonData.getInt("value");
                break;
            case "color":
                String opaqueColor = jsonData.getString("value").substring(3);
                String alpha = jsonData.getString("value").substring(1, 3);
                value =  Color.valueOf(opaqueColor + alpha);
                break;
            case "list":
                Array<Object> array = new Array<>();
                if (jsonValue.isArray()) {
                    if (jsonValue.isEmpty()) {
                        value = new Array<>();
                        break;
                    }
                    for (int i = 0; i < jsonValue.size; i++) {
                        JsonValue jsonValue1 = jsonValue.get(i);
                        Member member1 = json.fromJson(Member.class, jsonValue1.toString());
                        if (member1 == null) {
                            continue;
                        }
                        array.add(member1.value);
                    }
                    value = array;
                    break;
                }
                break;
            default:
                // 未知类型可忽略或抛出异常，这里留空
                Gdx.app.error(TAG,"Failed to serializer,unknown type: "+type + " ,name: "+ name);
                break;
        }
    }
}
