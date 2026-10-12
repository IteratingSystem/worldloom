package org.worldloom.serialize;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.managers.TagManager;
import com.artemis.utils.Bag;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.tiled.TiledMapTileSet;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import org.junit.jupiter.api.*;
import org.worldloom.Worldloom;
import org.worldloom.WorldloomConfig;
import org.worldloom.component.*;
import org.worldloom.manager.AssetManager;
import org.worldloom.manager.map.MapManager;
import org.worldloom.serialize.data.*;
import org.worldloom.system.B2dSystem;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Backend-free ECS/Box2D integration checks: no Application or window is started. */
class EntityRestoreTest {
    private World world;
    private TagManager tags;
    private static Application previousApp;
    private static Files previousFiles;

    @BeforeAll
    static void initializeMaps() throws Exception {
        previousApp = Gdx.app;
        previousFiles = Gdx.files;
        Path manifest = java.nio.file.Files.createTempFile("worldloom-test-assets", ".txt");
        manifest.toFile().deleteOnExit();
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
            new Class<?>[]{Application.class}, (p, m, a) -> null);
        Gdx.files = (Files) Proxy.newProxyInstance(Files.class.getClassLoader(),
            new Class<?>[]{Files.class}, (p, m, a) -> new FileHandle(manifest.toFile()));
        Worldloom.configure(WorldloomConfig.builder().worldScale(1f / 16f).build());
        Worldloom.setGameRootClass(EntityRestoreTest.class);
        MapManager.init(new ObjectMap<>(), new ObjectMap<>());
        MapObjects objects = new MapObjects();
        MapObject point = new MapObject();
        point.getProperties().put("id", 1);
        point.getProperties().put("x", 16f);
        point.getProperties().put("y", 32f);
        objects.add(point);
        Texture texture = mock(Texture.class);
        when(texture.getWidth()).thenReturn(16);
        when(texture.getHeight()).thenReturn(16);
        StaticTiledMapTile tile = new StaticTiledMapTile(new TextureRegion(texture));
        tile.setId(1);
        TiledMapTileMapObject ship = new TiledMapTileMapObject(tile, false, false);
        ship.getProperties().put("id", 2);
        ship.getProperties().put("x", 16f);
        ship.getProperties().put("y", 32f);
        objects.add(ship);
        ObjectMap<String, MapObjects> allObjects = new ObjectMap<>();
        allObjects.put("test", objects);
        setPrivate(MapManager.getInstance(), "allMapObjects", allObjects);
        TiledMapTileSet tiles = new TiledMapTileSet();
        tiles.putTile(1, tile);
        Bag<TiledMapTileSet> sets = new Bag<>();
        sets.add(tiles);
        setPrivate(MapManager.getInstance(), "tileSets", sets);
        com.badlogic.gdx.utils.GdxNativesLoader.load();
        Box2D.init();
    }

    @AfterAll
    static void cleanup() throws Exception {
        AssetManager.getInstance().dispose();
        setPrivate(null, MapManager.class, "instance", null);
        setPrivate(null, AssetManager.class, "instance", null);
        Gdx.app = previousApp;
        Gdx.files = previousFiles;
    }

    @BeforeEach
    void world() {
        tags = new TagManager();
        B2dSystem physics = new TestPhysics();
        world = new World(new WorldConfigurationBuilder().with(tags, physics).build()) {
            @SuppressWarnings("unchecked")
            @Override public <T extends com.artemis.BaseSystem> T getSystem(Class<T> type) {
                return type == B2dSystem.class ? (T) super.getSystem(TestPhysics.class) : super.getSystem(type);
            }
        };
    }

    @AfterEach void dispose() { world.dispose(); }

    @com.artemis.annotations.Wire(failOnNull = false)
    private static class TestPhysics extends B2dSystem {
        TestPhysics() { super(0, 0, true, 1f / 16f, false); }
        @Override protected void initialize() { }
    }

    @Test void savedPositionSurvivesReload() {
        EntityDatum datum = entity("player", 19, 1);
        datum.compMirrors.add(component("Pos", "x", 321f, "y", 654f));
        int id = EntitySerializer.buildEntity(world, datum);
        Pos pos = world.getMapper(Pos.class).get(id);
        assertEquals(321f, pos.x);
        assertEquals(654f, pos.y);
    }

    @Test void templatePositionUsesMapCoordinates() {
        EntityDatum datum = entity("marker", 0, 1);
        datum.dataFrom = EntityData.FROM_MAP;
        datum.compMirrors.add(component("Pos"));
        Pos pos = world.getMapper(Pos.class).get(EntitySerializer.buildEntity(world, datum));
        assertEquals(16f, pos.x);
        assertEquals(32f, pos.y);
    }

    @Test void zeroAndPartialCoordinatesAreValid() {
        EntityDatum datum = entity("marker", 0, 1);
        datum.compMirrors.add(component("Pos", "x", 0f));
        Pos pos = world.getMapper(Pos.class).get(EntitySerializer.buildEntity(world, datum));
        assertEquals(0f, pos.x);
        assertEquals(32f, pos.y);
    }

    @Test void spawnedTemplateCoordinatesOverrideMapCoordinates() {
        EntityDatum datum = entity("drop", 0, 2);
        datum.dataFrom = EntityData.FROM_MAP;
        datum.compMirrors.add(component("Pos", "x", 0f, "y", 480f));
        datum.compMirrors.add(component("B2dBody", "defType", "DynamicBody"));
        int id = EntitySerializer.buildEntity(world, datum);
        assertEquals(0f, world.getMapper(Pos.class).get(id).x);
        assertEquals(480f, world.getMapper(Pos.class).get(id).y);
        assertEquals(new Vector2(0, 30), world.getMapper(B2dBody.class).get(id).body.getPosition());
    }

    @Test void legacyBodyWithoutAngleAndWithPartialPositionLoads() {
        EntityDatum datum = entity("ship", 21, 2);
        datum.compMirrors.add(component("Pos", "x", 0));
        datum.compMirrors.add(component("B2dBody", "defType", "DynamicBody"));
        int id = EntitySerializer.buildEntity(world, datum);
        Body body = world.getMapper(B2dBody.class).get(id).body;
        assertEquals(0f, body.getAngle());
        assertEquals(new Vector2(0, 2), body.getPosition());
    }

    @Test void savedBodyHeadingAndPositionRoundTrip() {
        EntityDatum datum = entity("ship", 51, 2);
        datum.compMirrors.add(component("Pos", "x", 128f, "y", 256f));
        datum.compMirrors.add(component("B2dBody", "defType", "DynamicBody"));
        int id = EntitySerializer.buildEntity(world, datum);
        B2dBody body = world.getMapper(B2dBody.class).get(id);
        body.body.setTransform(8, 16, 1.234f);
        EntityDatum saved = EntitySerializer.createEntityDatum(world, id);
        int restored = EntitySerializer.buildEntity(world, saved);
        Body restoredBody = world.getMapper(B2dBody.class).get(restored).body;
        assertEquals(1.234f, restoredBody.getAngle(), 0.0001f);
        assertEquals(new Vector2(8, 16), restoredBody.getPosition());
    }

    @Test void batchRemapsReferencesEvenWhenTargetComesLater() {
        world.create(); // Deliberately make runtime IDs differ from saved IDs.
        EntityDatum ship = entity("ship", 45, 1);
        ship.compMirrors.add(component("User", "id", 93));
        ship.compMirrors.add(component("Owner", "id", 93));
        EntityDatum player = entity("player", 93, 1);
        EntityData batch = new EntityData();
        batch.add(ship); batch.add(player);
        EntitySerializer.buildEntities(world, batch);
        assertEquals(tags.getEntityId("player"), world.getMapper(User.class).get(tags.getEntityId("ship")).id);
        assertEquals(tags.getEntityId("player"), world.getMapper(Owner.class).get(tags.getEntityId("ship")).id);
        assertEquals(45, ship.entityId, "Building must not rewrite snapshot identities");
    }

    @Test void missingSavedReferenceCannotPointToUnrelatedRuntimeEntity() {
        world.create();
        EntityDatum ship = entity("ship", 45, 1);
        ship.compMirrors.add(component("User", "id", 0));
        EntityData batch = new EntityData(); batch.add(ship);
        EntitySerializer.buildEntities(world, batch);
        assertEquals(-1, world.getMapper(User.class).get(tags.getEntityId("ship")).id);
    }

    @Test void sourceSnapshotCanBeRebuiltTwiceWithoutRewritingReferences() {
        EntityDatum ship = entity("ship", 45, 1);
        ship.compMirrors.add(component("Owner", "id", 93));
        EntityData batch = new EntityData(); batch.add(ship); batch.add(entity("player", 93, 1));
        EntitySerializer.buildEntities(world, batch);
        EntitySerializer.buildEntities(world, batch);
        assertEquals(tags.getEntityId("player"), world.getMapper(Owner.class).get(tags.getEntityId("ship")).id);
        assertEquals(45, ship.entityId);
        assertEquals(93, ship.getCompMirror("Owner").getValue("id"));
    }

    @Test void serializedTagPreservesOwnerWhenTargetIsCarriedAcrossMaps() {
        int player = world.create(); tags.register("PLAYER", player);
        EntityDatum ship = entity("ship", 45, 1);
        ship.compMirrors.add(component("Owner", "id", player));
        int shipId = EntitySerializer.buildEntity(world, ship);
        EntityData saved = new EntityData(); saved.add(EntitySerializer.createEntityDatum(world, shipId));
        saved = EntitySerializer.toEntityBag(EntitySerializer.toJson(saved));
        assertEquals("PLAYER", saved.first().getCompMirror("Owner").properties.first().entityReferenceTag);
        // The target is already alive (e.g. Portal carried PLAYER), absent from this restored batch.
        EntitySerializer.buildEntities(world, saved);
        assertEquals(player, world.getMapper(Owner.class).get(tags.getEntityId("ship")).id);
    }

    @Test void emptyReferenceStaysEmptyEvenIfBatchContainsZeroId() {
        EntityDatum ship = entity("ship", 45, 1);
        ship.compMirrors.add(component("User", "id", -1));
        EntityData batch = new EntityData(); batch.add(ship); batch.add(entity("player", 0, 1));
        EntitySerializer.buildEntities(world, batch);
        assertEquals(-1, world.getMapper(User.class).get(tags.getEntityId("ship")).id);
    }

    @Test void templateReferencesRemainRuntimeReferences() {
        int player = world.create();
        EntityDatum ship = entity("ship", 0, 1);
        ship.dataFrom = EntityData.FROM_MAP;
        ship.compMirrors.add(component("User", "id", player));
        int id = EntitySerializer.buildEntity(world, ship);
        assertEquals(player, world.getMapper(User.class).get(id).id);
    }

    @Test void broadphaseContactIsNotARealContact() {
        com.badlogic.gdx.physics.box2d.World physics = world.getSystem(B2dSystem.class).box2DWorld;
        BodyDef definition = new BodyDef(); definition.type = BodyDef.BodyType.DynamicBody;
        Body first = physics.createBody(definition);
        definition.position.set(0.09f, 0);
        Body second = physics.createBody(definition);
        CircleShape shape = new CircleShape(); shape.setRadius(0.02f);
        Fixture fixture = first.createFixture(shape, 1);
        second.createFixture(shape, 1);
        shape.dispose();
        physics.step(1f / 60f, 6, 2);
        assertFalse(physics.getContactList().isEmpty(), "Overlapping fat AABBs produce a broadphase contact");
        assertFalse(physics.getContactList().first().isTouching());
        B2dBody body = new B2dBody(); body.body = first; body.b2dWorld = physics;
        assertEquals(0, body.getContactFixtures(fixture).size());
        second.setTransform(0.01f, 0, 0);
        physics.step(1f / 60f, 6, 2);
        assertEquals(1, body.getContactFixtures(fixture).size(), "Actual touching contacts remain available");
    }

    private static EntityDatum entity(String name, int savedId, int objectId) {
        EntityDatum datum = new EntityDatum(); datum.name = name; datum.entityId = savedId;
        datum.fromMap = "test"; datum.mapObjectId = objectId; datum.dataFrom = EntityData.FROM_WORLD;
        datum.compMirrors = new Array<>(); return datum;
    }

    private static CompMirror component(String name, Object... values) {
        CompMirror comp = new CompMirror(); comp.simpleName = name; comp.properties = new Properties();
        for (int i = 0; i < values.length; i += 2) comp.setValue((String) values[i], values[i + 1]);
        return comp;
    }

    private static void setPrivate(Object target, String name, Object value) throws Exception {
        setPrivate(target, target.getClass(), name, value);
    }
    private static void setPrivate(Object target, Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
}
