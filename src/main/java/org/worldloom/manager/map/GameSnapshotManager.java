package org.worldloom.manager.map;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.badlogic.gdx.utils.ObjectMap;
import org.worldloom.manager.JsonManager;
import org.worldloom.Worldloom;
import org.worldloom.serialize.EntitySerializer;
import org.worldloom.serialize.SerializeParam;
import org.worldloom.serialize.SerializeSystem;
import org.worldloom.serialize.data.EntityData;
import org.worldloom.serialize.data.EntityDatum;
import org.worldloom.serialize.data.CompMirror;
import org.worldloom.serialize.data.Properties;
import org.worldloom.serialize.data.Property;
import org.worldloom.system.TiledMapSystem;

import java.lang.reflect.Field;

/** Owns the active multi-map session and its serialization lifecycle. */
public class GameSnapshotManager {
    public static final int SAVE_FORMAT_VERSION = 1;
    private static final String LEGACY_PACKAGE = "org.ltae.";
    private static final String CURRENT_PACKAGE = "org.worldloom.";
    private static GameSnapshotManager instance;
    private GameSnapshot gameSnapshot;

    private GameSnapshotManager() {
    }

    public static GameSnapshotManager getInstance() {
        if (instance == null) {
            instance = new GameSnapshotManager();
        }
        return instance;
    }

    public static void setNull() {
        instance = null;
    }

    public void setSnapshot(GameSnapshot gameSnapshot) {
        if (gameSnapshot == null) {
            throw new IllegalArgumentException("worldState cannot be null");
        }
        if (gameSnapshot.entityData == null) {
            gameSnapshot.entityData = new ObjectMap<>();
        }
        if (gameSnapshot.systemProps == null) {
            gameSnapshot.systemProps = new ObjectMap<>();
        }
        migrateSnapshot(gameSnapshot);
        this.gameSnapshot = gameSnapshot;
    }

    public GameSnapshot getWorldState() {
        requireWorldState();
        return gameSnapshot;
    }

    public String getCurrentMap() {
        return getWorldState().curtMap;
    }

    /** Starts an isolated session from the entities defined in the Tiled maps. */
    public void startNewGame(String initialMap) {
        if (initialMap == null || initialMap.isBlank()) {
            throw new IllegalArgumentException("initialMap cannot be blank");
        }
        GameSnapshot initialState = new GameSnapshot();
        initialState.curtMap = initialMap;
        initialState.entityData = MapManager.getInstance().createInitialEntityData();
        setSnapshot(initialState);
    }

    /** Replaces the active session with a serialized save. */
    public void loadSaveJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("save json cannot be blank");
        }
        setSnapshot(JsonManager.fromJson(GameSnapshot.class, json));
    }

    /** 常驻会话按实际归属捕获全部地图，空图也覆盖旧存档；旧模式只捕获当前图。 */
    public void captureCurrentWorld(World world) {
        requireWorldState();
        TiledMapSystem tiledMapSystem = world.getSystem(TiledMapSystem.class);
        String currentMap = tiledMapSystem.getCurrent();
        gameSnapshot.curtMap = currentMap;
        if (!Worldloom.config().isResidentMode()) {
            gameSnapshot.entityData.put(currentMap, EntitySerializer.createEntityData(world));
        } else {
            ObjectMap<String, EntityData> grouped = new ObjectMap<>();
            for (String mapId : Worldloom.config().getResidentMaps()) grouped.put(mapId, new EntityData());
            for (EntityDatum entity : EntitySerializer.createEntityData(world)) {
                EntityData target = entity.mapId == null ? null : grouped.get(entity.mapId);
                if (target == null)
                    throw new IllegalStateException("存档实体缺少正式地图归属：" + entity.entityId + " / " + entity.mapId);
                target.add(entity);
            }
            for (ObjectMap.Entry<String, EntityData> map : grouped)
                gameSnapshot.entityData.put(map.key, map.value);
        }
        captureSystemProperties(world);
    }

    /** Captures the running world and returns the complete multi-map save. */
    public String createSaveJson(World world) {
        captureCurrentWorld(world);
        return getSaveJson();
    }

    /** @deprecated Use {@link #captureCurrentWorld(World)}. */
    @Deprecated
    public void updateWorldState(World world) {
        captureCurrentWorld(world);
    }

    public void changeCurrentMap(String mapName) {
        if (mapName == null || mapName.isBlank()) {
            throw new IllegalArgumentException("mapName cannot be blank");
        }
        requireWorldState();
        gameSnapshot.curtMap = mapName;
    }

    public EntityData getEntityData(String mapName) {
        requireWorldState();
        EntityData entityData = gameSnapshot.entityData.get(mapName);
        if (entityData == null) {
            entityData = new EntityData();
            gameSnapshot.entityData.put(mapName, entityData);
        }
        return entityData;
    }

    public String getSaveJson() {
        requireWorldState();
        return JsonManager.toJson(gameSnapshot);
    }

    private void captureSystemProperties(World world) {
        for (BaseSystem system : world.getSystems()) {
            Class<? extends BaseSystem> systemClass = system.getClass();
            if (!systemClass.isAnnotationPresent(SerializeSystem.class)) {
                continue;
            }

            if (system instanceof org.worldloom.serialize.SystemSnapshotLifecycle lifecycle) lifecycle.beforeSnapshot();
            Properties properties = new Properties();
            for (Field field : systemClass.getFields()) {
                if (!field.isAnnotationPresent(SerializeParam.class)) {
                    continue;
                }
                field.setAccessible(true);
                Property property = new Property();
                property.key = field.getName();
                property.type = field.getType().getName();
                try {
                    property.value = field.get(system);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Unable to access field: " + field, e);
                }
                properties.add(property);
            }
            gameSnapshot.systemProps.put(systemClass.getName(), properties);
        }
    }

    private void requireWorldState() {
        if (gameSnapshot == null) {
            throw new IllegalStateException("WorldState is not initialized");
        }
    }

    /** 将 LTAE 存档中的完整类名迁移到 Worldloom 包名。 */
    private void migrateSnapshot(GameSnapshot snapshot) {
        if (snapshot.saveFormatVersion > SAVE_FORMAT_VERSION) {
            throw new IllegalArgumentException(
                "save format is newer than this engine: " + snapshot.saveFormatVersion);
        }
        ObjectMap<String, Properties> migratedSystems = new ObjectMap<>();
        for (ObjectMap.Entry<String, Properties> entry : snapshot.systemProps) {
            migrateProperties(entry.value);
            migratedSystems.put(migrateTypeName(entry.key), entry.value);
        }
        snapshot.systemProps = migratedSystems;
        for (EntityData mapEntities : snapshot.entityData.values()) {
            for (EntityDatum entity : mapEntities) {
                if (entity.compMirrors == null) {
                    continue;
                }
                for (CompMirror component : entity.compMirrors) {
                    migrateProperties(component.properties);
                }
            }
        }
        snapshot.saveFormatVersion = SAVE_FORMAT_VERSION;
    }

    private void migrateProperties(Properties properties) {
        if (properties == null) {
            return;
        }
        for (Property property : properties) {
            property.type = migrateTypeName(property.type);
        }
    }

    private String migrateTypeName(String typeName) {
        if (typeName == null || !typeName.contains(LEGACY_PACKAGE)) {
            return typeName;
        }
        return typeName.replace(LEGACY_PACKAGE, CURRENT_PACKAGE);
    }
}
