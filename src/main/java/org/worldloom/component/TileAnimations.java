package org.worldloom.component;

import com.artemis.World;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.TiledMapTileSet;
import com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.utils.ObjectMap;
import org.worldloom.component.parent.SerializeComponent;
import org.worldloom.manager.map.MapManager;
import org.worldloom.serialize.SerializeParam;
import org.worldloom.serialize.data.EntityDatum;

import java.util.Iterator;

/**
 * @Auther WenLong
 * @Date 2025/3/11 15:44
 * @Description 多动画组件(瓦片动画)
 **/
public class TileAnimations extends SerializeComponent {
    private final static String TAG = TileAnimations.class.getSimpleName();
    public ObjectMap<String,TileAnimation> table;

    @SerializeParam
    public String current;
    @Override
    public void reload(World world, EntityDatum entityDatum) {
        super.reload(world, entityDatum);
        table = new ObjectMap<>();

        MapManager mapManager = MapManager.getInstance();
        TiledMapTileSet tileSet = mapManager.getTileSet(tiledMapTile);
        String group = groupOf(tileSet, tiledMapTile);
        Iterator<TiledMapTile> tileSetItr = tileSet.iterator();
        while (tileSetItr.hasNext()){
            TiledMapTile tile = tileSetItr.next();
            if (!(tile instanceof AnimatedTiledMapTile animatedTile)){
                continue;
            }
            MapProperties props = animatedTile.getProperties();
            if (!props.containsKey("TileAnimation")) {
                continue;
            }
            if (!group.equals(groupOf(props))) {
                continue;
            }
            MapProperties animProp = props.get("TileAnimation",MapProperties.class);
            String name = animProp.get("name", String.class);
            String playMode = animProp.get("playMode", String.class);
            float offsetX = animProp.get("offsetX", float.class);
            float offsetY = animProp.get("offsetY", float.class);
            TileAnimation tileAnimation = new TileAnimation();
            tileAnimation.initialize(animatedTile, Animation.PlayMode.valueOf(playMode),offsetX,offsetY);
            if (table.containsKey(name)){
                Gdx.app.log(TAG,"Repetitive animation naming:"+name);
                continue;
            }
            table.put(name,tileAnimation);
        }
    }

    /**
     * 读取瓦片动画属性里的角色分组，没有写分组时返回空字符串。
     * 同一个 tileset 里放多个角色时，每个角色的动画瓦片写同一个 group，实体只会收集与自己同组的动画和碰撞形状。
     *
     * @param props 动画瓦片的属性
     * @return 分组名，不分组为 ""
     */
    public static String groupOf(MapProperties props) {
        if (props == null || !props.containsKey("TileAnimation")) {
            return "";
        }
        MapProperties animProp = props.get("TileAnimation", MapProperties.class);
        return animProp.get("group", "", String.class);
    }

    /**
     * 解析实体所放置瓦片所属的角色分组：瓦片自带 TileAnimation 属性就取它的分组，
     * 否则在 tileset 里找把它当作动画帧的动画瓦片，取那一个的分组。
     *
     * @param tileSet 瓦片所在 tileset
     * @param tile    实体放置的瓦片，可为 null
     * @return 分组名，不分组为 ""
     */
    public static String groupOf(TiledMapTileSet tileSet, TiledMapTile tile) {
        if (tile == null) {
            return "";
        }
        if (tile.getProperties().containsKey("TileAnimation")) {
            return groupOf(tile.getProperties());
        }
        for (TiledMapTile other : tileSet) {
            if (!(other instanceof AnimatedTiledMapTile animated) || !animated.getProperties().containsKey("TileAnimation")) {
                continue;
            }
            for (StaticTiledMapTile frame : animated.getFrameTiles()) {
                if (frame.getId() == tile.getId()) {
                    return groupOf(animated.getProperties());
                }
            }
        }
        return "";
    }

    public TileAnimation getTileAnimation(){
        return getTileAnimation(current);
    }
    private TileAnimation getTileAnimation(String animationName){
        if (!hasAnimation(animationName)){
            Gdx.app.error(TAG,"Failed to getTileAnimation,There is no animation named: "+animationName);
            return null;
        }
        return table.get(animationName);
    }
    public TextureRegion getKeyFrame() {
        TileAnimation tileAnimation = getTileAnimation();
        return tileAnimation.getKeyFrame();
    }

    /**
     * 直接改变动画,在改变动画的过程中stateTime会清零
     * @param animationName
     */
    public void changeAnimation(String animationName){
        //判断是否存在这个动画
        if (!hasAnimation(animationName)){
            Gdx.app.error(TAG,"Failed to changeAnimation,There is no animation named: "+animationName);
            return;
        }
        current = animationName;
        TileAnimation tileAnimation = getTileAnimation();
        tileAnimation.stateTime = 0;
    }
    public boolean isAnimationFinished(){
        TileAnimation tileAnimation = getTileAnimation();
        return tileAnimation.isAnimationFinished();
    }
    public boolean isLast(int lastCount){
        TileAnimation tileAnimation = getTileAnimation();
        return tileAnimation.isLast(lastCount);
    }
    public boolean hasAnimation(String animationName){
        if (table.containsKey(animationName)){
            return true;
        }
        return false;
    }
}
