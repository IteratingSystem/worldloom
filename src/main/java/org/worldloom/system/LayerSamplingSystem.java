package org.worldloom.system;

import com.artemis.annotations.All;
import com.artemis.annotations.Exclude;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.utils.Array;
import net.mostlyoriginal.api.plugin.extendedcomponentmapper.M;
import org.worldloom.component.*;
import org.worldloom.component.Inert;
import org.worldloom.utils.SamplingUtils;
import org.worldloom.render.MapVisibility;

@All({LayerSampling.class, Render.class, Pos.class})
@Exclude(Inert.class)
public class LayerSamplingSystem extends IteratingSystem {
    private TiledMapSystem tiledMapSystem;

    private M<Render> mRender;
    private M<LayerSampling> mSampling;
    private M<TileAnimation> mTileAnimation;


    @Override
    protected void initialize() {
        super.initialize();
    }

    /** 图层采样属于观察图表现资源，进入该图时才创建，避免串图和后台显存开销。 */
    @Override
    protected void process(int entityId) {
        if (!MapVisibility.inViewedMap(world, entityId)) {
            return;
        }
        LayerSampling sampling = mSampling.get(entityId);

        //采样完成
        if (sampling.isSampled()){
            //如果如果没有动画瓦片或者已经有动画组件则直接返回,否则生成动画组件
            if (sampling.flagAnimTile == null || sampling.isCreateAnim){
                return;
            }
            Array<StaticTiledMapTile> staticTiledMapTiles = new Array<>();
            for (TextureRegion region : sampling.regions) {
                staticTiledMapTiles.add(new StaticTiledMapTile(region));
            }
            AnimatedTiledMapTile animatedTiledMapTile = new AnimatedTiledMapTile(sampling.flagAnimTile.getAnimationIntervals()[0]/1000f,staticTiledMapTiles);

            mTileAnimation.remove(entityId);
            TileAnimation tileAnimation = mTileAnimation.create(entityId);
            tileAnimation.initialize(animatedTiledMapTile, Animation.PlayMode.LOOP,0,0);
            sampling.isCreateAnim = true;
            return;
        }

        //静态图层采样
        if (sampling.flagAnimTile == null){
            TextureRegion textureRegion = SamplingUtils.getInstance().samplingLayer(tiledMapSystem.getTiledMap(),sampling.layerName);
            sampling.regions[0] = textureRegion;
            Render render = mRender.get(entityId);
            render.keyframe = textureRegion;
            return;
        }

        //动画图层采样
        int currentFrameIndex = sampling.flagAnimTile.getCurrentFrameIndex();
        if (sampling.regions[currentFrameIndex] != null) {
            return;
        }

        TextureRegion textureRegion = SamplingUtils.getInstance().samplingLayer(tiledMapSystem.getTiledMap(),sampling.layerName);
        sampling.regions[currentFrameIndex] = textureRegion;
        Render render = mRender.get(entityId);
        render.keyframe = textureRegion;
    }

    /** World.dispose不会逐个销毁组件，因此显式释放仍然存活的采样结果。 */
    @Override protected void dispose() {
        var ids = world.getAspectSubscriptionManager().get(com.artemis.Aspect.all(LayerSampling.class)).getEntities();
        for (int i = 0; i < ids.size(); i++) mSampling.get(ids.get(i)).dispose();
    }
}
