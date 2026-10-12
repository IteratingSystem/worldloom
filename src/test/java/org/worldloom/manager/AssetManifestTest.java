package org.worldloom.manager;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.utils.ObjectMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.worldloom.manager.map.MapManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Real manifest consumers with mocked file/assets I/O; no backend, GL or window. */
class AssetManifestTest {
    private Application previousApp;
    private Files previousFiles;
    private Object previousAssets, previousBacking, previousMaps;
    private FileHandle manifest;
    private com.badlogic.gdx.assets.AssetManager backing;

    @BeforeEach void arrangeIsolatedManagers() throws Exception {
        previousApp = Gdx.app;
        previousFiles = Gdx.files;
        previousAssets = getStatic(AssetManager.class, "instance");
        previousBacking = getStatic(AssetManager.class, "gdxAssetManager");
        previousMaps = getStatic(MapManager.class, "instance");
        Gdx.app = mock(Application.class);
        Gdx.files = mock(Files.class);
        manifest = mock(FileHandle.class);
        when(manifest.exists()).thenReturn(true);
        when(Gdx.files.internal(anyString())).thenAnswer(call -> {
            String path = call.getArgument(0);
            if (path.equals("assets.txt")) return manifest;
            FileHandle file = mock(FileHandle.class);
            when(file.exists()).thenReturn(true);
            when(file.name()).thenReturn(path.substring(path.lastIndexOf('/') + 1));
            return file;
        });
        backing = mock(com.badlogic.gdx.assets.AssetManager.class);
        setStatic(AssetManager.class, "gdxAssetManager", backing);
        setStatic(MapManager.class, "instance", null);
    }

    @AfterEach void restoreManagers() throws Exception {
        Gdx.app = previousApp;
        Gdx.files = previousFiles;
        setStatic(AssetManager.class, "instance", previousAssets);
        setStatic(AssetManager.class, "gdxAssetManager", previousBacking);
        setStatic(MapManager.class, "instance", previousMaps);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "tiled/island.tmx\nai/combat_enemy.tree\n",
        "tiled/island.tmx\r\nai/combat_enemy.tree\r\n",
        "  tiled/island.tmx  \r\n\tai/combat_enemy.tree\t\r\n",
        "tiled\\island.tmx\r\nai\\combat_enemy.tree\r\n",
        "\uFEFFtiled/island.tmx\r\nai/combat_enemy.tree\r\n"
    })
    void manifestFormatting_loadAndDiscoveryUseSameCanonicalPaths(String text) throws Exception {
        AssetManager assets = manager(text);
        TiledMap map = new TiledMap();
        BehaviorTree<?> tree = new BehaviorTree<>();
        when(backing.get("tiled/island.tmx", TiledMap.class)).thenReturn(map);
        when(backing.get("ai/combat_enemy.tree", BehaviorTree.class)).thenReturn(tree);

        assets.loadAssets();
        assertSame(map, assets.getObjects(".tmx", TiledMap.class).get("island"));
        assertSame(tree, assets.getObjects(".tree", BehaviorTree.class).get("combat_enemy"));
        verify(backing).load("tiled/island.tmx", TiledMap.class);
        verify(backing).load("ai/combat_enemy.tree", BehaviorTree.class);
        assertArrayEquals(new String[]{"tiled/island.tmx", "ai/combat_enemy.tree"}, assets.assetPathList);
    }

    @Test void blankLinesAndShaderPaths_publicManifestContainsOnlyCanonicalEntries() throws Exception {
        AssetManager assets = manager("\uFEFF \r\n \t\r\n shader\\water.vert \r\nshader/water.frag\n\n");
        assertArrayEquals(new String[]{"shader/water.vert", "shader/water.frag"}, assets.assetPathList);
    }

    @Test void manifestEditedAfterConstruction_discoveryUsesTheLoadedSnapshot() throws Exception {
        AssetManager assets = manager("tiled/island.tmx\n");
        TiledMap map = new TiledMap();
        when(backing.get("tiled/island.tmx", TiledMap.class)).thenReturn(map);
        assets.loadAssets();
        when(manifest.readString()).thenReturn("tiled/not-loaded.tmx\n");
        assertSame(map, assets.getObjects(".tmx", TiledMap.class).get("island"));
        verify(backing, never()).get("tiled/not-loaded.tmx", TiledMap.class);
    }

    @Test void crlfFiveMapManifest_registersIslandPhysicsAndAllEntityLayers() throws Exception {
        String[] names = {"island", "items", "prefab", "inCHouse1", "inExchange"};
        StringBuilder text = new StringBuilder();
        ObjectMap<String, String> entityLayers = new ObjectMap<>();
        ObjectMap<String, String[]> physicsLayers = new ObjectMap<>();
        for (String name : names) {
            text.append("tiled/").append(name).append(".tmx\r\n");
            TiledMap map = new TiledMap();
            for (String layerName : new String[]{"ENTITY", "SEA_PHY", "BRIDGE"}) {
                MapLayer layer = new MapLayer(); layer.setName(layerName); map.getLayers().add(layer);
            }
            entityLayers.put(name, "ENTITY");
            physicsLayers.put(name, new String[]{"SEA_PHY", "BRIDGE"});
            when(backing.get("tiled/" + name + ".tmx", TiledMap.class)).thenReturn(map);
        }
        AssetManager assets = manager(text.toString());
        assets.loadAssets();
        MapManager.init(entityLayers, physicsLayers);
        MapManager maps = MapManager.getInstance();
        for (String name : names) {
            assertNotNull(maps.getTiledMap(name), name);
            assertNotNull(maps.getMapObjects(name), name + " entities");
            MapLayer[] layers = maps.getPhyLayer(name);
            assertEquals("SEA_PHY", layers[0].getName());
            assertEquals("BRIDGE", layers[1].getName());
        }
    }

    private AssetManager manager(String text) throws Exception {
        when(manifest.readString()).thenReturn(text);
        Constructor<AssetManager> constructor = AssetManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        AssetManager assets = constructor.newInstance();
        setStatic(AssetManager.class, "instance", assets);
        return assets;
    }
    private static Object getStatic(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(null);
    }
    private static void setStatic(Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(null, value);
    }
}
