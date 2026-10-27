package org.worldloom.system;

import com.artemis.BaseSystem;
import com.artemis.Component;
import com.artemis.utils.Bag;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.utils.ObjectMap;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.worldloom.component.Pos;
import org.worldloom.component.Render;
import org.worldloom.component.ZIndex;
import org.worldloom.event.EntityEvent;
import org.worldloom.serialize.EntityDeleter;
import org.worldloom.serialize.ComponentConfig;
import org.worldloom.serialize.EntityBuilder;
import org.worldloom.serialize.EntitySerializer;
import org.worldloom.serialize.data.EntityData;
import org.worldloom.serialize.data.EntityDatum;
import org.worldloom.Worldloom;
import org.worldloom.manager.map.GameSnapshotManager;


/**
 * @Auther WenLong
 * @Date 2025/2/12 16:29
 * @Description
 **/
public class EntityFactory extends BaseSystem {
    private final static String TAG = EntityFactory.class.getSimpleName();
    private TiledMapSystem tiledMapSystem;
    private ObjectMap<String, MapObject> prefabricatedObjects;

    public EntityFactory(){
        Bag<Class<? extends Component>> autoCompClasses = new Bag<>();
        autoCompClasses.add(Pos.class);
        autoCompClasses.add(Render.class);
        autoCompClasses.add(ZIndex.class);

        ComponentConfig componentConfig = new ComponentConfig();
        componentConfig.autoCompClasses = autoCompClasses;
    }
    @Override
    protected void initialize() {
    }

    @Override
    protected void processSystem() {

    }
    private void delAndCreateAll(){
        EntityDeleter.deleteAll(world);
        EntityBuilder.buildEntities(world,tiledMapSystem.getCurrent());
    }
    private void delAndCreateAll(EntityData EntityData){
        EntityDeleter.deleteAll(world);
        EntityBuilder.buildEntities(world, EntityData);
    }
    /** 常驻会话统一分配全部正式地图实体，模板地图仅供后续显式实例化。 */
    private void buildAll(){
        if (Worldloom.config().getResidentMaps().isEmpty()) {
            EntityBuilder.buildEntities(world,tiledMapSystem.getCurrent());
            return;
        }
        ObjectMap<String, EntityData> maps = new ObjectMap<>();
        for (String map : Worldloom.config().getResidentMaps()) {
            maps.put(map, GameSnapshotManager.getInstance().getEntityData(map));
        }
        EntitySerializer.buildEntities(world, maps);
    }
    private void buildEntities(EntityData entityData){
        EntityBuilder.buildEntities(world, entityData);
    }
    private int buildEntity(EntityDatum entityDatum){
        return EntityBuilder.buildEntity(world, entityDatum);
    }
    private EntityData createEntityData(){
        return EntitySerializer.createEntityData(world);
    }
    private EntityDatum createEntityDatum(int entityId){
        return EntitySerializer.createEntityDatum(world,entityId);
    }
    private String serializerEntitiesJson(){
        EntityData EntityData = EntitySerializer.createEntityData(world);
        return EntitySerializer.toJson(EntityData);
    }
    private void deleteEntity(int entityId){
        EntityDeleter.deleteEntity(world,entityId);
    }
    private void deleteAll(){
        EntityDeleter.deleteAll(world);
    }
    private void filterDeleteAll(String[] filterEntity){
        EntityDeleter.deleteAll(world,filterEntity);
    }
    @Subscribe
    public void onEvent(EntityEvent event){
        if (event.type == EntityEvent.BUILD_ALL){
            buildAll();
            return;
        }
        if (event.type == EntityEvent.BUILD_ENTITIES){
            buildEntities(event.entityData);
            return;
        }
        if (event.type == EntityEvent.DEL_AND_CREATE_ALL){
            if (event.entityData == null){
                delAndCreateAll();
                return;
            }
            delAndCreateAll(event.entityData);
            return;
        }
        if (event.type == EntityEvent.SERIALIZER_ENTITIES){
            event.serializerEntitiesStr = serializerEntitiesJson();
            return;
        }
        if (event.type == EntityEvent.DELETE_ENTITY){
            deleteEntity(event.entityId);
            return;
        }
        if (event.type == EntityEvent.DELETE_ALL){
            deleteAll();
            return;
        }
        if (event.type == EntityEvent.FILTER_DEL_ALL){
            filterDeleteAll(event.entityTags);
            return;
        }
        if (event.type == EntityEvent.BUILD_ENTITY){
            event.entityId = buildEntity(event.entityDatum);
            event.entity = world.getEntity(event.entityId);
            return;
        }
        if (event.type == EntityEvent.CREATE_ENTITY_DATUM){
            event.entityDatum = createEntityDatum(event.entityId);
            return;
        }
    }

}
