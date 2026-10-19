package org.worldloom.component;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.tiled.TiledMapTileSet;
import com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.utils.Array;
import org.junit.jupiter.api.Test;

/** 验证多角色共用 tileset 时，实体放置瓦片能解析出所属分组。 */
class TileAnimationsGroupTest {
    /**
     * 创建一个静态帧瓦片。
     *
     * @param id 瓦片 id
     * @return 无纹理的静态瓦片
     */
    private static StaticTiledMapTile frame(int id) {
        StaticTiledMapTile tile = new StaticTiledMapTile((com.badlogic.gdx.graphics.g2d.TextureRegion) null);
        tile.setId(id);
        return tile;
    }

    /**
     * 创建带 TileAnimation 属性的动画瓦片，并加入 tileset。
     *
     * @param set   目标 tileset
     * @param id    动画瓦片 id
     * @param group 分组，null 表示不写 group 成员
     * @param frameId 唯一一帧的瓦片 id
     * @return 创建的动画瓦片
     */
    private static AnimatedTiledMapTile animated(TiledMapTileSet set, int id, String group, int frameId) {
        Array<StaticTiledMapTile> frames = new Array<>();
        frames.add(frame(frameId));
        AnimatedTiledMapTile tile = new AnimatedTiledMapTile(0.1f, frames);
        tile.setId(id);
        MapProperties anim = new MapProperties();
        anim.put("name", "idle");
        if (group != null) {
            anim.put("group", group);
        }
        tile.getProperties().put("TileAnimation", anim);
        set.putTile(id, tile);
        return tile;
    }

    /**
     * 分组按自身属性或帧引用解析；未写 group 的动画归空分组。
     */
    @Test
    void groupResolvedFromOwnPropertyOrFrameReference() {
        TiledMapTileSet set = new TiledMapTileSet();
        AnimatedTiledMapTile a = animated(set, 10, "a", 100);
        AnimatedTiledMapTile b = animated(set, 20, "b", 200);
        AnimatedTiledMapTile none = animated(set, 30, null, 300);

        assertEquals("a", TileAnimations.groupOf(set, a));
        assertEquals("b", TileAnimations.groupOf(set, b));
        assertEquals("", TileAnimations.groupOf(set, none));
        assertEquals("b", TileAnimations.groupOf(set, frame(200)));
        assertEquals("", TileAnimations.groupOf(set, frame(999)));
        assertEquals("", TileAnimations.groupOf(set, null));
        assertEquals("a", TileAnimations.groupOf(a.getProperties()));
    }
}
