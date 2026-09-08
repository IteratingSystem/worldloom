package org.worldloom.serialize;

import com.artemis.*;
import com.artemis.managers.TagManager;
import com.artemis.utils.Bag;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.maps.*;
import com.badlogic.gdx.utils.Array;
import org.worldloom.Worldloom;
import org.worldloom.component.parent.SerializeComponent;
import org.worldloom.manager.JsonManager;
import org.worldloom.manager.ReflectionManager;
import org.worldloom.serialize.data.*;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Set;

/**
 * @Auther WenLong
 * @Date 2025/7/10 11:02
 * @Description
 **/
public class EntitySerializer {
    private final static String TAG = EntitySerializer.class.getSimpleName();

    public static EntityData createEntityData(String mapName, MapObjects mapObjects){
        EntityData entityDateList = new EntityData();
        for (MapObject mapObject : mapObjects) {
            String entityName = mapObject.getName();
            MapProperties properties = mapObject.getProperties();

            EntityDatum entityDatum = new EntityDatum();
            entityDatum.compMirrors = new Array<>();
            entityDatum.mapObjectId = mapObject.getProperties().get("id",0,Integer.class);
            entityDatum.fromMap = mapName;

            entityDatum.name = entityName;
            entityDatum.type = properties.get("type", "", String.class);

            // 获取所有组件
            ReflectionManager reflectionManager = ReflectionManager.getInstance();
            Set<Class<? extends Component>> compClasses = reflectionManager.getSubTypesOfWithEngineAndGame(Component.class);

            for (Class<? extends Component> compClass : compClasses) {
                String simpleName = compClass.getSimpleName();
                MapProperties property = properties.get(simpleName, null, MapProperties.class);
                if (property == null) {
                    continue;
                }
                CompMirror compMirror = new CompMirror();
                compMirror.properties = new Properties();
                compMirror.simpleName = simpleName;

                Field[] fields = compClass.getFields();
                for (int i = 0; i < fields.length; i++) {
                    Field field = fields[i];
                    if (!field.isAnnotationPresent(SerializeParam.class)) {
                        continue;
                    }
                    Class<?> type = field.getType();
                    Property compProp = new Property();
                    compProp.key = field.getName();
                    compProp.type = type.getName();
                    compProp.value = property.get(field.getName(), null, type);
                    compMirror.properties.add(compProp);
                }
                entityDatum.compMirrors.add(compMirror);
            }
            //添加默认组件
            for (Class autoCompClass : Worldloom.config().getAutoComponents()) {
                String simpleName = autoCompClass.getSimpleName();
                if (entityDatum.hasComp(simpleName)) {
                    continue;
                }
                CompMirror compMirror = new CompMirror();
                compMirror.properties = new Properties();
                compMirror.simpleName = simpleName;
                entityDatum.compMirrors.add(compMirror);
            }
            entityDateList.add(entityDatum);
        }
        entityDateList.setDataFrom(EntityData.FROM_MAP);
        return entityDateList;
    }

    public static EntityData createEntityData(World world){
        EntityData entityDateList = new EntityData();

        AspectSubscriptionManager aspectSubscriptionManager = world.getSystem(AspectSubscriptionManager.class);
        EntitySubscription allEntities = aspectSubscriptionManager.get(Aspect.all());
        IntBag entities = allEntities.getEntities();
        for (int i = 0; i < entities.size(); i++) {
            int entityId = entities.get(i);
            EntityDatum entityDatum = createEntityDatum(world,entityId);
            if (entityDatum == null){
                continue;
            }
            entityDateList.add(entityDatum);
        }
        entityDateList.setDataFrom(EntityData.FROM_WORLD);
        return entityDateList;
    }
    public static EntityDatum createEntityDatum(World world, int entityId){
        Bag<Component> allComps = new Bag<>();
        world.getEntity(entityId).getComponents(allComps);
        if (allComps.isEmpty()) {
            return null;
        }

        EntityDatum entity = new EntityDatum();
        entity.entityId = entityId;
        TagManager tagManager = world.getSystem(TagManager.class);
        entity.name = tagManager.getTag(entityId);
        Bag<Component> components = new Bag<>();
        world.getEntity(entityId).getComponents(components);

        boolean isSetMapMsg = false;
        for (Component component : components) {
            if (isSetMapMsg){
                break;
            }
            if (component instanceof SerializeComponent serializeComponent
                && serializeComponent.mapObject != null) {
                entity.mapObjectId = serializeComponent.mapObject.getProperties()
                    .get("id", -1, Integer.class);
                entity.fromMap = serializeComponent.fromMap;
                isSetMapMsg = true;
            }
        }

        entity.compMirrors = new Array<>();
        for (Component component : allComps) {
            if (component instanceof SerializeComponent serializeComponent) {
                serializeComponent.beforeSerialization();
            }


            Class<? extends Component> compClass = component.getClass();
            String compName = compClass.getSimpleName();
            Field[] fields = compClass.getFields();

            CompMirror compMirror = new CompMirror();
            compMirror.simpleName = compName;
            compMirror.properties = new Properties();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(SerializeParam.class)) {
                    continue;
                }
                String key = field.getName();
                Class<?> type = field.getType();
                Object value = null;
                try {
                    value = field.get(component);
                    if (value == null){
                        continue;
                    }
                    if (type.isEnum()) {
                        value = ((Enum<?>) field.get(component)).name();
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
                Property prop = new Property();
                prop.key = key;
                prop.value = value;
                prop.type = type.getName();

                compMirror.properties.add(prop);
            }
            entity.compMirrors.add(compMirror);
        }
        entity.dataFrom = EntityData.FROM_WORLD;
        return entity;
    }
    //覆盖entityDatum,更新了新的数据覆盖上去,相对于复制过去替换掉
    public static void overlayEntityData(EntityData entityData, EntityDatum entityDatum){
        if (entityData.hasEntityData(entityDatum)) {
            for (EntityDatum entity : entityData) {
                if (entity.equals(entityDatum)) {
                    entityData.removeValue(entity,true);
                    entityData.add(entityDatum);
                    break;
                }
            }
            return;
        }
        entityData.add(entityDatum);
    }

    @SuppressWarnings("unchecked")
    public static int buildEntity(World world, EntityDatum entityDatum) {
        TagManager tagManager = world.getSystem(TagManager.class);
        int entityId = world.create();
        entityDatum.entityId = entityId;

        // 注册 tag
        if (!"".equals(entityDatum.name)) {
            tagManager.register(entityDatum.name, entityId);
        }

        Array<CompMirror> components = entityDatum.compMirrors;
        ReflectionManager reflectionManager = ReflectionManager.getInstance();
        Set<Class<? extends Component>> compClasses = reflectionManager.getSubTypesOfWithEngineAndGame(Component.class);

        for (Class<? extends Component> aClass : compClasses) {
            String simpleName = aClass.getSimpleName();
            for (CompMirror compMirror : components) {
                if (!simpleName.equals(compMirror.simpleName)) {
                    continue;
                }

                ComponentMapper<? extends Component> mapper = world.getMapper(aClass);
                if (mapper == null) {
                    break;
                }

                Component component = mapper.create(entityId);

                // 赋值属性
                Array<Property> props = compMirror.properties;
                for (Property prop : props) {
                    String key = prop.key;
                    Object value = prop.value;
                    if (value == null) {
                        continue;
                    }
                    try {
                        // 递归查找字段（包括父类）
                        Field field = findField(aClass, key);
                        if (field == null) {
                            Gdx.app.error(TAG, "Failed to findField: " + key + " in " + compMirror.simpleName);
                            continue;
                        }
                        if (!field.isAnnotationPresent(SerializeParam.class)) {
                            Gdx.app.error(TAG,"Field " + key + " is not annotated with " + SerializeParam.class.getSimpleName() + "in "+compMirror.simpleName );
                            continue;
                        }
                        field.setAccessible(true); // 允许访问私有字段

                        Class<?> type = field.getType();
                        if (type.isEnum()) {
                            // 假设 value 为 String（枚举名称）或 Number（枚举序数）
                            if (value instanceof String) {
                                try {
                                    Class<? extends Enum> enumClass = type.asSubclass(Enum.class);
                                    value = Enum.valueOf(enumClass, (String) value);
                                } catch (IllegalArgumentException e) {
                                    Gdx.app.error(TAG, "Invalid enum name: " + value + " for field " + key);
                                    continue;
                                }
                            } else if (value instanceof Number) {
                                // 按枚举序数转换
                                int ordinal = ((Number) value).intValue();
                                Object[] enumConstants = type.getEnumConstants();
                                if (ordinal >= 0 && ordinal < enumConstants.length) {
                                    value = enumConstants[ordinal];
                                } else {
                                    Gdx.app.error(TAG, "Invalid enum ordinal: " + ordinal + " for field " + key);
                                    continue;
                                }
                            } else {
                                Gdx.app.error(TAG, "Unsupported value type for enum field " + key + ": " + value.getClass());
                                continue;
                            }
                        } else {
                            value = coerceType(value, type);
                        }

//                        if (value instanceof Array<?> arrayValue && arrayValue.notEmpty()) {
//                            try {
//                                Type genericType = field.getGenericType();
//                                if (genericType instanceof ParameterizedType pt) {
//                                    Class<?> elementType = (Class<?>) pt.getActualTypeArguments()[0];
//                                    @SuppressWarnings("unchecked")
//                                    Array<Object> rawArray = (Array<Object>) arrayValue;
//                                    for (int i = 0; i < rawArray.size; i++) {
//                                        Object elem = rawArray.get(i);
//                                        if (elem == null) continue;
//                                        if (elementType.isEnum() && elem instanceof String s) {
//                                            try {
//                                                rawArray.set(i, Enum.valueOf(elementType.asSubclass(Enum.class), s));
//                                            } catch (IllegalArgumentException e) {
//                                                Gdx.app.error(TAG, "Invalid enum value: " + s + " for element " + i + " in field " + key);
//                                            }
//                                        } else if (!elementType.isInstance(elem)) {
//                                            Object converted = coerceType(elem, elementType);
//                                            if (converted != null) {
//                                                rawArray.set(i, converted);
//                                            }
//                                        }
//                                    }
//                                }
//                            } catch (Exception e) {
//                                Gdx.app.error(TAG, "Failed to convert Array elements for field " + key + ": " + e.getMessage());
//                            }
//                        }
                        if (value instanceof Array<?> arrayValue && arrayValue.notEmpty()) {
                            restoreArrayElements(field, key, arrayValue);
                        }

                        field.set(component, value);


                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        throw new RuntimeException("Failed to set field " + key + " on " + aClass.getName(), e);
                    }
                }

                // 执行 reload
                if (component instanceof SerializeComponent) {
                    ((SerializeComponent) component).reload(world, entityDatum);
                    break; // 注意：break 只会跳出内部循环，但通常此组件已处理完成，可以继续下一个组件类
                }
            }
        }
        return entityId;
    }

    /**
     * 递归查找字段，包括父类
     */
    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            Class<?> superclass = clazz.getSuperclass();
            if (superclass != null && superclass != Object.class) {
                return findField(superclass, fieldName);
            } else {
                return null;
            }
        }
    }

    private static Object coerceType(Object value, Class<?> type) {
        if (value == null || type.isInstance(value)) return value;
        if (value instanceof Number n) {
            if (type == int.class || type == Integer.class) return n.intValue();
            if (type == float.class || type == Float.class) return n.floatValue();
            if (type == double.class || type == Double.class) return n.doubleValue();
            if (type == long.class || type == Long.class) return n.longValue();
            if (type == short.class || type == Short.class) return n.shortValue();
            if (type == byte.class || type == Byte.class) return n.byteValue();
            if (type == boolean.class || type == Boolean.class) return n.intValue() != 0;
        } else if (value instanceof String s) {
            if (type == int.class || type == Integer.class) return Integer.parseInt(s);
            if (type == float.class || type == Float.class) return Float.parseFloat(s);
            if (type == double.class || type == Double.class) return Double.parseDouble(s);
            if (type == long.class || type == Long.class) return Long.parseLong(s);
            if (type == short.class || type == Short.class) return Short.parseShort(s);
            if (type == byte.class || type == Byte.class) return Byte.parseByte(s);
            if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(s);
        }
        return value;
    }

    /** 按字段泛型恢复存档中的数组元素，避免自定义对象被保留为通用Map。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void restoreArrayElements(Field field, String fieldName,
                                     Array<?> arrayValue) {
        Type genericType = field.getGenericType();
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return;
        }
        Type actualType = parameterizedType.getActualTypeArguments()[0];
        if (!(actualType instanceof Class<?> elementType)) {
            // 嵌套泛型由具体组件负责，避免丢失其内部类型信息。
            return;
        }

        Array<Object> rawArray = (Array<Object>) arrayValue;
        for (int i = 0; i < rawArray.size; i++) {
            Object element = rawArray.get(i);
            if (element == null || elementType.isInstance(element)) {
                continue;
            }
            try {
                Object restored;
                if (elementType.isEnum() && element instanceof String name) {
                    restored = Enum.valueOf(elementType.asSubclass(Enum.class), name);
                } else {
                    Object coerced = coerceType(element, elementType);
                    restored = elementType.isInstance(coerced)
                        ? coerced
                        : JsonManager.getJson().fromJson(elementType,
                            JsonManager.getJson().toJson(element));
                }
                rawArray.set(i, restored);
            } catch (RuntimeException exception) {
                Gdx.app.error(TAG, "Failed to restore array element for field "
                    + fieldName + " at index " + i, exception);
            }
        }
    }


    public static void buildEntities(World world, EntityData entityData){
        if (entityData == null) {
            return;
        }
        for (EntityDatum entityDatum : entityData) {
            buildEntity(world,entityDatum);
        }
    }
    public static String toJson(EntityData entityData){
        return JsonManager.toJson(entityData);
    }
    public static EntityData toEntityBag(String entityDataList){
        return JsonManager.fromJson(EntityData.class,entityDataList);
    }
}
