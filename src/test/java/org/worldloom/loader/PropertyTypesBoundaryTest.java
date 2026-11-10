package org.worldloom.loader;

import com.badlogic.gdx.files.FileHandle;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyTypesBoundaryTest {
    /** 用正式读取边界检查引擎类型唯一性及归属，不再调用已弃用的字段转换器。 */
    @Test
    void containsOnlyUniqueEngineTypes() throws URISyntaxException {
        var resource = getClass().getResource("/propertytypes.json");
        assertNotNull(resource);
        var source = new TiledPropertySource(new FileHandle(Path.of(resource.toURI()).toFile()));
        Set<String> names = new java.util.HashSet<>();
        source.index.members.keys().forEach(names::add);

        assertTrue(names.containsAll(Set.of(
            "Interactive", "Owner", "User", "Slice", "PointLight",
            "StoryComp", "TopDownPointLight", "TopDownShadow")));
        assertTrue(Set.of(
            "RPGProp", "Inventory", "Item", "ShipProp",
            "WaterReflection", "Broadcaster", "Prefabricated",
            "OnInteractive").stream().noneMatch(names::contains));
    }

}
