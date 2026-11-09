package org.worldloom.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.TiledMapTileSet;
import com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile;
import com.badlogic.gdx.utils.ObjectMap;
import org.worldloom.component.TileAnimation;
import org.worldloom.component.TileAnimations;
import java.util.*;

/** 单个图集只解析一次动作与帧形状描述；不持有实体、World或原生Shape。 */
public final class TileAnimationIndex {
    private final Map<String, Map<String, TileAnimation>> animations = new HashMap<>();
    private final Map<String, List<FrameShape>> shapes = new HashMap<>();
    private final Map<Integer, String> frameGroups = new HashMap<>();

    /** 帧形状保留只读Tiled源对象，每个实体仍独立创建自己的物理形状和监听器。 */
    public record FrameShape(MapObject object, String animation, int frame) { }

    /** 建立分组、动作及帧碰撞目录；同名动作保留原有的首个定义规则。 */
    public TileAnimationIndex(TiledMapTileSet tiles) {
        for (TiledMapTile tile : tiles) {
            if (!(tile instanceof AnimatedTiledMapTile animated)
                || !tile.getProperties().containsKey("TileAnimation")) continue;
            var props = tile.getProperties().get("TileAnimation", com.badlogic.gdx.maps.MapProperties.class);
            String group = TileAnimations.groupOf(tile.getProperties());
            String name = props.get("name", String.class);
            TileAnimation template = new TileAnimation();
            template.initialize(animated, Animation.PlayMode.valueOf(props.get("playMode", String.class)),
                props.get("offsetX", float.class), props.get("offsetY", float.class));
            template.name = name; template.group = group;
            if (animations.computeIfAbsent(group, ignored -> new LinkedHashMap<>()).putIfAbsent(name, template) != null)
                Gdx.app.log("TileAnimationIndex", "Duplicate animation name: " + name);
            var frames = animated.getFrameTiles();
            List<FrameShape> groupShapes = shapes.computeIfAbsent(group, ignored -> new ArrayList<>());
            for (int i = 0; i < frames.length; i++) {
                frameGroups.putIfAbsent(frames[i].getId(), group);
                for (MapObject object : frames[i].getObjects())
                    if (object.getProperties().containsKey("FixDef")) groupShapes.add(new FrameShape(object, name, i));
            }
        }
        shapes.replaceAll((group, entries) -> List.copyOf(entries));
    }

    /** 当前播放时间、随机状态、可修改时长数组均按实例隔离，共享的仅是纹理及已解析定义。 */
    public ObjectMap<String, TileAnimation> createAnimations(String group) {
        ObjectMap<String, TileAnimation> result = new ObjectMap<>();
        for (var entry : animations.getOrDefault(group, Map.of()).entrySet())
            result.put(entry.getKey(), entry.getValue().copyPlayback());
        return result;
    }

    /** 返回不可修改的帧形状目录，调用方不得更改其中Tiled源对象。 */
    public List<FrameShape> frameShapes(String group) { return shapes.getOrDefault(group, List.of()); }

    /** 图块自带分组优先，否则按首个引用它的动画确定归属，与旧扫描顺序一致。 */
    public String groupOf(TiledMapTile tile) {
        if (tile == null) return "";
        return tile.getProperties().containsKey("TileAnimation") ? TileAnimations.groupOf(tile.getProperties())
            : frameGroups.getOrDefault(tile.getId(), "");
    }
}
