package org.worldloom.serialize;

import com.artemis.*;
import com.artemis.managers.TagManager;
import com.artemis.utils.Bag;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.maps.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.graphics.Color;
import org.worldloom.component.MapMembership;
import org.worldloom.component.PersistentIdentity;
import org.worldloom.component.StateComp;
import org.worldloom.system.MapIdentitySystem;
import org.worldloom.ui.inventory.SlotDatum;
import org.worldloom.Worldloom;
import org.worldloom.component.parent.SerializeComponent;
import org.worldloom.manager.JsonManager;
import org.worldloom.manager.ReflectionManager;
import org.worldloom.serialize.data.*;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

/**
 * @Auther WenLong
 * @Date 2025/7/10 11:02
 * @Description
 **/
public class EntitySerializer {
    private final static String TAG = EntitySerializer.class.getSimpleName();
    private static final ClassValue<Field[]> SERIALIZED_FIELDS = new ClassValue<>() {
        /** 每个组件类型仅解析一次公开序列化字段，包含继承字段。 */
        @Override protected Field[] computeValue(Class<?> type) {
            return java.util.Arrays.stream(type.getFields())
                .filter(field -> field.isAnnotationPresent(SerializeParam.class)).toArray(Field[]::new);
        }
    };
    private static final ClassValue<Map<String, Field>> RESTORE_FIELDS = new ClassValue<>() {
        /** 以最具体的声明优先建立恢复字段索引，不缓存实体或World引用。 */
        @Override protected Map<String, Field> computeValue(Class<?> type) {
            Map<String, Field> fields = new HashMap<>();
            for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass())
                for (Field field : current.getDeclaredFields()) fields.putIfAbsent(field.getName(), field);
            return fields;
        }
    };

    public static EntityData createEntityData(String mapName, MapObjects mapObjects){
        EntityData entityDateList = new EntityData();
        for (MapObject mapObject : mapObjects) {
            String entityName = mapObject.getName();
            MapProperties properties = mapObject.getProperties();

            EntityDatum entityDatum = new EntityDatum();
            entityDatum.compMirrors = new Array<>();
            entityDatum.mapObjectId = mapObject.getProperties().get("id",0,Integer.class);
            entityDatum.fromMap = mapName;
            entityDatum.mapId = mapName;

            entityDatum.name = entityName;
            entityDatum.type = properties.get("type", "", String.class);

            // 获取所有组件
            ReflectionManager reflectionManager = ReflectionManager.getInstance();
            var compClasses = reflectionManager.getSubTypesBySimpleName(Component.class).values();

            for (Class<? extends Component> compClass : compClasses) {
                String simpleName = compClass.getSimpleName();
                MapProperties property = properties.get(simpleName, null, MapProperties.class);
                if (property == null) {
                    continue;
                }
                CompMirror compMirror = new CompMirror();
                compMirror.properties = new Properties();
                compMirror.simpleName = simpleName;

                Field[] fields = SERIALIZED_FIELDS.get(compClass);
                for (int i = 0; i < fields.length; i++) {
                    Field field = fields[i];
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
        MapMembership membership = world.getMapper(MapMembership.class).get(entityId);
        PersistentIdentity identity = world.getMapper(PersistentIdentity.class).get(entityId);
        if (membership != null) {
            entity.mapId = membership.mapId;
            entity.name = membership.localTag;
        }
        if (identity != null) entity.entityKey = identity.entityKey;

        boolean isSetMapMsg = false;
        for (Component component : allComps) {
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
            Field[] fields = SERIALIZED_FIELDS.get(compClass);

            CompMirror compMirror = new CompMirror();
            compMirror.simpleName = compName;
            compMirror.properties = new Properties();
            for (Field field : fields) {
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
                prop.value = copySnapshotValue(value);
                prop.type = type.getName();
                if (field.isAnnotationPresent(EntityReference.class) && value instanceof Number reference
                    && reference.intValue() >= 0) {
                    prop.entityReferenceTag = tagManager.getTag(reference.intValue());
                    PersistentIdentity target = world.getMapper(PersistentIdentity.class).get(reference.intValue());
                    if (target != null) prop.entityReferenceKey = target.entityKey;
                }

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

    /** 实例化已明确归属的模板，常驻模式拒绝把素材来源或观察图当作出生地图。 */
    public static int buildEntity(World world, EntityDatum entityDatum) {
        if (Worldloom.config().isResidentMode()) {
            if (entityDatum.mapId == null || !Worldloom.config().isResidentMap(entityDatum.mapId))
                throw new IllegalArgumentException("常驻动态实体必须显式指定正式出生地图");
            return buildEntity(world, entityDatum, entityDatum.mapId);
        }
        EntityData batch = new EntityData();
        batch.add(entityDatum);
        return restoreBatch(world, batch).first().entityId;
    }

    /** 在明确目标地图动态生成新实例，始终分配新身份，不抢占模板局部名称。 */
    public static int buildEntity(World world, EntityDatum source, String mapId) {
        Worldloom.config().requireRuntimeMap(mapId);
        EntityDatum spawn = copyEntityDatum(source);
        spawn.mapId = mapId;
        spawn.entityKey = "spawn:" + UUID.randomUUID();
        spawn.name = "";
        EntityData batch = new EntityData();
        batch.add(spawn);
        return restoreBatch(world, batch).first().entityId;
    }

    /** 恢复联机传来的单实体快照，保留其身份并显式指定当前地图。 */
    public static int restoreEntity(World world, EntityDatum source, String mapId) {
        Worldloom.config().requireRuntimeMap(mapId);
        EntityDatum saved = copyEntityDatum(source);
        saved.mapId = mapId;
        EntityData batch = new EntityData();
        batch.add(saved);
        return restoreBatch(world, batch).first().entityId;
    }

    /**
     * 复制实体镜像及可变属性，用于模板实例化、库存和掉落；保留来源及实体引用标识。
     * 仅复制可序列化数据，不复制World、纹理或物理资源；null输入返回null。
     */
    public static EntityDatum copyEntityDatum(EntityDatum source) {
        if (source == null) return null;
        EntityDatum copy = new EntityDatum();
        copy.entityId = source.entityId;
        copy.dataFrom = source.dataFrom;
        copy.fromMap = source.fromMap;
        copy.mapId = source.mapId;
        copy.entityKey = source.entityKey;
        copy.mapObjectId = source.mapObjectId;
        copy.name = source.name;
        copy.type = source.type;
        copy.compMirrors = new Array<>();
        if (source.compMirrors != null)
            for (CompMirror component : source.compMirrors) copy.compMirrors.add(copyComponentMirror(component));
        return copy;
    }

    /** 复制单个组件镜像；容器与可变值隔离，不丢失EntityReference的稳定标签。 */
    public static CompMirror copyComponentMirror(CompMirror source) {
        if (source == null) return null;
        CompMirror copy = new CompMirror();
        copy.simpleName = source.simpleName;
        copy.properties = new Properties();
        if (source.properties != null) for (Property property : source.properties) {
            Property value = new Property();
            value.key = property.key;
            value.type = property.type;
            value.value = copySnapshotValue(property.value);
            value.entityReferenceTag = property.entityReferenceTag;
            value.entityReferenceKey = property.entityReferenceKey;
            copy.properties.add(value);
        }
        return copy;
    }

    /** 常用镜像类型直接复制；其余DTO沿用现有JSON契约，不另引入反射复制框架。 */
    private static Object copySnapshotValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number
            || value instanceof Boolean || value instanceof Character || value instanceof Enum<?>) return value;
        if (value instanceof EntityDatum entity) return copyEntityDatum(entity);
        if (value instanceof Color color) return new Color(color);
        if (value instanceof SlotDatum slot) {
            SlotDatum copy = new SlotDatum();
            copy.copy(slot);
            copy.selected = slot.selected;
            copy.entityDatum = copyEntityDatum(slot.entityDatum);
            return copy;
        }
        if (value instanceof Array<?> array) {
            Array<Object> copy = new Array<>(array.ordered, array.size);
            for (Object element : array) copy.add(copySnapshotValue(element));
            return copy;
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            Object copy = java.lang.reflect.Array.newInstance(value.getClass().getComponentType(), length);
            for (int i = 0; i < length; i++)
                java.lang.reflect.Array.set(copy, i, copySnapshotValue(java.lang.reflect.Array.get(value, i)));
            return copy;
        }
        return JsonManager.getJson().fromJson(value.getClass(), JsonManager.toJson(value));
    }

    /** 按阶段恢复普通字段或实体引用，全部字段完成前不加载组件资源。 */
    @SuppressWarnings("unchecked")
    private static void restoreFields(World world, EntityDatum entityDatum, Map<Integer, Integer> restoredIds, boolean references) {
        int entityId = entityDatum.entityId;

        Array<CompMirror> components = entityDatum.compMirrors;
        ReflectionManager reflectionManager = ReflectionManager.getInstance();
        var compClasses = reflectionManager.getSubTypesBySimpleName(Component.class).values();
        Map<String, CompMirror> mirrors = new HashMap<>();
        for (CompMirror mirror : components) {
            if (mirrors.putIfAbsent(mirror.simpleName, mirror) != null)
                throw new IllegalArgumentException("重复组件镜像：" + mirror.simpleName);
        }

        for (Class<? extends Component> aClass : compClasses) {
            String simpleName = aClass.getSimpleName();
            CompMirror compMirror = mirrors.get(simpleName);
            if (compMirror == null) continue;

            ComponentMapper<? extends Component> mapper = world.getMapper(aClass);
            if (mapper == null) {
                continue;
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
                    if (field.isAnnotationPresent(EntityReference.class) != references) continue;
                    field.setAccessible(true); // 允许访问私有字段

                    Class<?> type = field.getType();
                    if (field.isAnnotationPresent(EntityReference.class)) {
                        if (type != int.class) {
                            throw new IllegalArgumentException("实体引用仅支持int字段：" + field);
                        }
                        int oldId = ((Number) coerceType(value, int.class)).intValue();
                        int resolved = -1;
                        MapIdentitySystem identities = world.getSystem(MapIdentitySystem.class);
                        if (prop.entityReferenceKey != null && identities != null)
                            resolved = identities.resolve(prop.entityReferenceKey);
                        if (resolved < 0 && entityDatum.dataFrom == EntityData.FROM_WORLD)
                            resolved = restoredIds.getOrDefault(oldId, -1);
                        if (resolved < 0 && prop.entityReferenceTag != null)
                            resolved = world.getSystem(TagManager.class).getEntityId(prop.entityReferenceTag);
                        value = oldId < 0 ? -1 : resolved;
                    }
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

                    if (value instanceof Array<?> arrayValue && arrayValue.notEmpty()) {
                        restoreArrayElements(field, key, arrayValue);
                    }

                    field.set(component, value);


                } catch (NoSuchFieldException | IllegalAccessException e) {
                    throw new RuntimeException("Failed to set field " + key + " on " + aClass.getName(), e);
                }
            }

        }
    }

    /**
     * 从类型缓存查找字段，包括父类，避免每次读档或实体同步重复反射查找。
     */
    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        return RESTORE_FIELDS.get(clazz).get(fieldName);
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


    /** 恢复已有快照，优先使用保存的地图归属。 */
    public static void buildEntities(World world, EntityData entityData) {
        if (entityData != null) restoreBatch(world, entityData);
    }

    /** 指定单图归属后恢复，不修改输入快照或素材来源。 */
    public static void buildEntities(World world, EntityData entityData, String mapId) {
        if (entityData == null) return;
        EntityData batch = new EntityData();
        appendMap(batch, entityData, mapId);
        restoreBatch(world, batch);
    }

    /** 全图共用分配、身份、引用及资源屏障，跨图前向引用也能正确恢复。 */
    public static void buildEntities(World world, ObjectMap<String, EntityData> maps) {
        EntityData batch = new EntityData();
        for (ObjectMap.Entry<String, EntityData> map : maps)
            appendMap(batch, map.value, map.key);
        restoreBatch(world, batch);
    }

    /** 复制每图快照并附上当前归属，不覆盖不可变的fromMap来源。 */
    private static void appendMap(EntityData batch, EntityData source, String mapId) {
        Worldloom.config().requireRuntimeMap(mapId);
        if (source == null) return;
        for (EntityDatum saved : source) {
            EntityDatum copy = copyEntityDatum(saved);
            copy.mapId = mapId;
            batch.add(copy);
        }
    }

    /** 全部字段就绪后才加载资源，最后启动状态机；失败时撤销本批实体。 */
    private static Array<EntityDatum> restoreBatch(World world, EntityData source) {
        // 整批先验证，模板或错误目标不能在失败前创建半个ECS/资源。
        for (EntityDatum saved : source) {
            String mapId = saved.mapId == null || saved.mapId.isBlank() ? saved.fromMap : saved.mapId;
            if (mapId != null && !mapId.isBlank()) Worldloom.config().requireRuntimeMap(mapId);
        }
        Array<EntityDatum> runtime = new Array<>(source.size);
        Map<Integer, Integer> restoredIds = new HashMap<>();
        MapIdentitySystem identities = world.getSystem(MapIdentitySystem.class);
        try {
            for (EntityDatum saved : source) {
                EntityDatum entity = copyEntityDatum(saved);
                entity.entityId = world.create();
                runtime.add(entity);
                if (saved.dataFrom == EntityData.FROM_WORLD)
                    restoredIds.put(saved.entityId, entity.entityId);
            }
            for (EntityDatum entity : runtime) restoreFields(world, entity, restoredIds, false);
            for (EntityDatum entity : runtime) {
                restoreIdentity(world, entity);
                if (identities != null && world.getMapper(MapMembership.class).has(entity.entityId))
                    identities.refresh(entity.entityId);
            }
            // 保留旧业务的全局标签，但重复局部名称不覆盖已有别名。
            TagManager tags = world.getSystem(TagManager.class);
            for (EntityDatum entity : runtime)
                if (entity.name != null && !entity.name.isBlank() && tags.getEntityId(entity.name) < 0)
                    tags.register(entity.name, entity.entityId);
            for (EntityDatum entity : runtime) restoreFields(world, entity, restoredIds, true);
            for (EntityDatum entity : runtime) reloadComponents(world, entity, false);
            for (EntityDatum entity : runtime) reloadComponents(world, entity, true);
            return runtime;
        } catch (RuntimeException | Error failure) {
            for (EntityDatum entity : runtime) {
                if (identities != null) identities.forget(entity.entityId);
                try { EntityDeleter.deleteEntity(world, entity.entityId); }
                catch (RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            }
            throw failure;
        }
    }

    /** 将运行元数据写入归属组件；明确实例化参数优先于镜像里的模板值。 */
    private static void restoreIdentity(World world, EntityDatum entity) {
        boolean resident = Worldloom.config().isResidentMode();
        if (!resident && !world.getMapper(MapMembership.class).has(entity.entityId)) return;
        MapMembership membership = world.getMapper(MapMembership.class).create(entity.entityId);
        PersistentIdentity identity = world.getMapper(PersistentIdentity.class).create(entity.entityId);
        String mapId = entity.mapId;
        if (mapId == null || mapId.isBlank()) mapId = membership.mapId;
        if (mapId == null || mapId.isBlank()) mapId = entity.fromMap;
        Worldloom.config().requireRuntimeMap(mapId);
        membership.mapId = mapId;
        membership.localTag = entity.name == null ? "" : entity.name;
        String key = entity.entityKey;
        if (key == null || key.isBlank()) key = identity.entityKey;
        if (key == null || key.isBlank())
            key = entity.dataFrom == EntityData.FROM_MAP && entity.mapObjectId > 0
                ? "map:" + mapId + ":" + entity.mapObjectId : "spawn:" + UUID.randomUUID();
        identity.entityKey = key;
        entity.mapId = mapId;
        entity.entityKey = key;
    }

    /** 按阶段恢复组件资源，状态机必须等其它实体和资源全部就绪。 */
    private static void reloadComponents(World world, EntityDatum entity, boolean states) {
        Bag<Component> components = world.getEntity(entity.entityId).getComponents(new Bag<>());
        for (Component component : components)
            if (component instanceof SerializeComponent serializable && (component instanceof StateComp) == states)
                serializable.reload(world, entity);
    }
    public static String toJson(EntityData entityData){
        return JsonManager.toJson(entityData);
    }
    public static EntityData toEntityBag(String entityDataList){
        return JsonManager.fromJson(EntityData.class,entityDataList);
    }
}
