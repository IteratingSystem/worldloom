package org.worldloom.system;

import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.artemis.utils.Bag;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import net.mostlyoriginal.api.plugin.extendedcomponentmapper.M;
import org.worldloom.box2d.KeyframeShapeData;
import org.worldloom.box2d.setup.FixtureSetup;
import org.worldloom.component.B2dBody;
import org.worldloom.component.Render;
import org.worldloom.component.TileAnimations;
import org.worldloom.component.TileAnimation;
import org.worldloom.utils.ShapeUtils;

/**
 * @Auther WenLong
 * @Date 2025/4/7 14:22
 * @Description 动画帧中响应的形状
 **/
@All({B2dBody.class, TileAnimations.class, Render.class})
//@Exclude(Inert.class)
public class KeyframeShapeSystem extends IteratingSystem {
    private M<B2dBody> mB2dBody;
    private M<TileAnimations> mTileAnimations;
    private M<Render> mRender;
    private final IntMap<FrameState> frames = new IntMap<>();

    /** 缓存每个实体实际安装的帧形状；仅当前世界持有，不序列化到组件。 */
    private static final class FrameState {
        B2dBody component;
        Body body;
        Bag<FixtureSetup> setups;
        TileAnimation animation;
        String name;
        int index, width;
        boolean flip;
        float time;
        final Array<Fixture> fixtures = new Array<>();

        /** 检查缓存夹具仍属于当前身体，兼容业务主动重建碰撞体。 */
        boolean installed() {
            for (int i = 0; i < fixtures.size; i++)
                if (!body.getFixtureList().contains(fixtures.get(i), true)) return false;
            return true;
        }
    }


    /**
     * 仅在动作、帧号、翻转、身体或形状集合变化时更新夹具；普通游戏帧保留现有接触。
     * 动作重新开始及外部夹具重建同样使缓存失效，不依赖反复制造beginContact触发伤害。
     * @param entityId 当前动画实体
     */
    @Override
    protected void process(int entityId) {
        B2dBody b2dBody = mB2dBody.get(entityId);
        Body body = b2dBody.body;
        Bag<FixtureSetup> keyframeFixSetups = b2dBody.keyframeFixSetups;

        TileAnimations tileAnimations = mTileAnimations.get(entityId);
        String aniName = tileAnimations.current;
        TileAnimation animation = tileAnimations.getTileAnimation();

        Render render = mRender.get(entityId);
        if (body == null || keyframeFixSetups == null || animation == null || render.keyframe == null) return;
        int keyframeIndex = animation.getKeyframeIndex();
        int regionWidth = render.keyframe.getRegionWidth();
        FrameState state = frames.get(entityId);
        if (state == null) {
            state = new FrameState();
            frames.put(entityId, state);
        }
        boolean unchanged = state.component == b2dBody && state.body == body
            && state.setups == keyframeFixSetups && state.animation == animation
            && java.util.Objects.equals(state.name, aniName) && state.index == keyframeIndex
            && state.width == regionWidth && state.flip == b2dBody.needFlipX
            && animation.stateTime >= state.time && state.installed();
        state.time = animation.stateTime;
        if (unchanged) return;

        Array<Fixture> fixtureList = body.getFixtureList();
        // 反向删除，防止destroyFixture修改原数组后跳过相邻夹具。
        for (int i = fixtureList.size - 1; i >= 0; i--) {
            Fixture fixture = fixtureList.get(i);
            if (fixture.getUserData() instanceof KeyframeShapeData) body.destroyFixture(fixture);
        }
        state.fixtures.clear();

        //创建响应的帧形状
        for (FixtureSetup keyframeFixSetup : keyframeFixSetups) {
            KeyframeShapeData fixtureData = (KeyframeShapeData)keyframeFixSetup.fixtureData;
            if (java.util.Objects.equals(aniName, fixtureData.aniName) && keyframeIndex == fixtureData.keyframeIndex) {
                FixtureDef keyframeFixDef = b2dBody.getKeyframeFixDef(fixtureData);

                //是否需要翻转与当前的翻转情况
                if (b2dBody.needFlipX != fixtureData.isFlipX){
                    Shape shape = keyframeFixDef.shape;
                    ShapeUtils.flipX(shape,regionWidth);
                    fixtureData.isFlipX = b2dBody.needFlipX;
                }
                Fixture fixture = body.createFixture(keyframeFixDef);
                fixture.setUserData(fixtureData);
                state.fixtures.add(fixture);
            }
        }
        state.component = b2dBody;
        state.body = body;
        state.setups = keyframeFixSetups;
        state.animation = animation;
        state.name = aniName;
        state.index = keyframeIndex;
        state.width = regionWidth;
        state.flip = b2dBody.needFlipX;
    }

    /** 实体删除或移除相关组件后清理缓存，防止复用实体编号继承旧身体。 */
    @Override protected void removed(int entityId) { frames.remove(entityId); }

    /** 世界退出时丢弃缓存引用，夹具仍归Box2D身体管理。 */
    @Override protected void dispose() { frames.clear(); }
}
