package org.worldloom.system;

import com.artemis.annotations.All;
import com.artemis.annotations.Exclude;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import net.mostlyoriginal.api.plugin.extendedcomponentmapper.M;
import org.worldloom.component.Inert;
import org.worldloom.component.Render;
import org.worldloom.component.TileAnimation;
import org.worldloom.component.TileAnimations;

/**
 * @Auther WenLong
 * @Date 2025/3/11 16:11
 * @Description 瓦片动画系统
 **/
@All({TileAnimation.class, Render.class})
@Exclude(Inert.class)
public class TileAnimSystem extends IteratingSystem {
    private final static String TAG = TileAnimSystem.class.getSimpleName();
    private M<TileAnimations> mTileAnimations;
    private M<TileAnimation> mTileAnimation;
    private M<Render> mRender;
    /** 暂停时不采样；播放时推进一次时间并只读取一次目标帧。 */
    @Override
    protected void process(int entityId) {
        Render render = mRender.get(entityId);
        TileAnimation tileAnimation;
        if (mTileAnimations.has(entityId)) {
            TileAnimations tileAnimations = mTileAnimations.get(entityId);
            tileAnimation = tileAnimations.getTileAnimation();
            if (tileAnimation == null){
                Gdx.app.error(TAG,"The current animation does not exist in tileAnimations: "+tileAnimations.current+",Please confirm the animation name: "+tileAnimations.current);
                return;
            }
        }else {
            tileAnimation = mTileAnimation.get(entityId);
        }
        if (tileAnimation.isPause || tileAnimation.getkeyframes() == null) return;
        tileAnimation.stateTime += world.getDelta();
        var frame = tileAnimation.getKeyFrame();
        if (frame == null) return;
        render.keyframe = frame;
        render.offsetX = tileAnimation.offsetX;
        render.offsetY = tileAnimation.offsetY;
    }
}
