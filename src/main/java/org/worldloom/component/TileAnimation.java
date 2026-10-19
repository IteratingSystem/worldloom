package org.worldloom.component;

import com.artemis.World;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.math.MathUtils;
import org.worldloom.component.parent.SerializeComponent;
import org.worldloom.serialize.SerializeParam;
import org.worldloom.serialize.data.EntityDatum;


import java.util.Arrays;

/**
 * @Auther WenLong
 * @Date 2025/3/7 10:44
 * @Description 瓦片动画组件,大部分方法来自于libgdx的Animation是一样的,可以使用共同的文档
 **/
public class TileAnimation extends SerializeComponent {
    @SerializeParam
    public String name;
    @SerializeParam
    public Animation.PlayMode playMode;
    @SerializeParam
    public float offsetX;
    @SerializeParam
    public float offsetY;
    /** 角色分组：同一 tileset 放多个角色时，用它区分动画归属；空表示不分组 */
    @SerializeParam
    public String group = "";

    protected TextureRegion[] keyframes;
    //状态运行时间
    @SerializeParam
    public float stateTime;


    public boolean isPause;


    //每一帧的间隔
    private float[] frameDurations;
    //随机播放模式时,每一帧持续的时间
    private float randomDuration;
    //动画的总持续事件
    private float totalDuration;
    //上一帧索引
    private int lastFrameNumber;
    //上一帧的时间戳
    private float lastStateTime;


    @Override
    public void reload(World world, EntityDatum entityDatum) {
        super.reload(world, entityDatum);
        isPause = false;

        if (!(tiledMapTile instanceof AnimatedTiledMapTile animatedTile)) {
            return;
        }

        initialize(animatedTile,playMode,offsetX,offsetY);
    }

    /**
     * 用于在非组件的模式下初始化
     * 传入一个AnimatedTiledMapTile以及playModeName
     *
     * @param animatedTile
     * @param playMode
     */
    public void initialize(AnimatedTiledMapTile animatedTile,Animation.PlayMode playMode,float offsetX,float offsetY) {
        stateTime = 0;
        int[] animationIntervals = animatedTile.getAnimationIntervals();
        frameDurations = new float[animationIntervals.length];
        for (int i = 0; i < animationIntervals.length; i++) {
            frameDurations[i] = animationIntervals[i]/1000f;
        }

        for (float duration : frameDurations) {
            totalDuration += duration;
        }
        randomDuration = totalDuration/frameDurations.length;

        StaticTiledMapTile[] frameTiles = animatedTile.getFrameTiles();
        keyframes = new TextureRegion[frameTiles.length];
        for (int i = 0; i < frameTiles.length; i++) {
            keyframes[i] = frameTiles[i].getTextureRegion();
        }
        this.playMode = playMode;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }


    /**
     * 判断送否是倒数第n帧之一
     * 一般用于控制动画切换时机
     * @param lastCount
     * @return
     */
    public boolean isLast(int lastCount){
        if (keyframes == null){
            return true;
        }
        int frameNumber = this.getKeyframeIndex(stateTime);
        int length = keyframes.length;
        return length - frameNumber - 1 <= lastCount;
    }

    /**
     * 获取特定时刻时的帧
     *
     * @return keyFrame
     */
    public TextureRegion getKeyFrame() {
        if (keyframes == null){
            return null;
        }
        int frameNumber = this.getKeyframeIndex(stateTime);
        return keyframes[frameNumber];
    }

    /**
     * 获取特定时刻时的帧
     *
     * @param stateTime
     * @param looping
     * @return keyFrame
     */
    private TextureRegion getKeyFrame(float stateTime, boolean looping) {
        Animation.PlayMode oldPlayMode = this.playMode;
        if (looping && (this.playMode == Animation.PlayMode.NORMAL || this.playMode == Animation.PlayMode.REVERSED)) {
            if (this.playMode == Animation.PlayMode.NORMAL) {
                this.playMode = Animation.PlayMode.LOOP;
            } else {
                this.playMode = Animation.PlayMode.LOOP_REVERSED;
            }
        } else if (!looping && this.playMode != Animation.PlayMode.NORMAL && this.playMode != Animation.PlayMode.REVERSED) {
            if (this.playMode == Animation.PlayMode.LOOP_REVERSED) {
                this.playMode = Animation.PlayMode.REVERSED;
            } else {
                this.playMode = Animation.PlayMode.LOOP;
            }
        }

        TextureRegion frame = this.getKeyFrame(stateTime);
        this.playMode = oldPlayMode;
        return frame;
    }


    /**
     * 获取特定时刻时的帧
     *
     * @param stateTime
     * @return keyFrame
     */
    private TextureRegion getKeyFrame(float stateTime) {
        if (keyframes == null){
            return null;
        }
        int frameNumber = this.getKeyframeIndex(stateTime);
        return this.keyframes[frameNumber];
    }

    /**
     * 获取传入时间的帧的索引
     *
     * @return
     */
    public int getKeyframeIndex() {
        return getKeyframeIndex(stateTime);
    }
    /**
     * 获取传入时间的帧的索引
     *
     * @param stateTime
     * @return
     */
    public int getKeyframeIndex(float stateTime) {
        if (this.keyframes == null || this.keyframes.length == 1) {
            return 0;
        }

        int frameNumber = 0;
        //动画循环n遍,最终的余数
        float remainder = stateTime % totalDuration;
        //往返动画循环n遍,最终的余数
        float pingRemainder = stateTime % (2*totalDuration);
        float adder;

        switch (this.playMode) {
            //播放一次后停止
            case NORMAL:
                if (stateTime >= totalDuration){
                    frameNumber = keyframes.length - 1;
                    break;
                }
                frameNumber = findIndex(stateTime, frameDurations);
                break;

            //循环播放
            case LOOP:
                frameNumber = findIndex(remainder, frameDurations);
                break;
            //往返播放
            case LOOP_PINGPONG:
                if (pingRemainder < totalDuration) {
                    // 正向播放
                    frameNumber = findIndex(pingRemainder, frameDurations);
                    break;
                } else {
                    // 反向播放
                    pingRemainder -= totalDuration;
                    frameNumber =  frameDurations.length - 1 - findIndex(pingRemainder, frameDurations);
                    break;
                }
            //循环播放,随机上一张或者下一张
            case LOOP_RANDOM:
                int lastFrameNumber = (int)(this.lastStateTime / this.randomDuration);
                if (lastFrameNumber != frameNumber) {
                    frameNumber = MathUtils.random(this.keyframes.length - 1);
                    break;
                } else {
                    frameNumber = this.lastFrameNumber;
                    break;
                }
            //反向播放一次后停止
            case REVERSED:
                if (stateTime < totalDuration){
                    adder = 0;
                    for (int i = frameDurations.length-1; i >= 0; i--) {
                        adder += frameDurations[i];
                        if (stateTime <= adder){
                            frameNumber = i;
                            break;
                        }
                    }
                }
                break;
            //反向循环播放
            case LOOP_REVERSED:
                if (stateTime < totalDuration){
                    adder = 0;
                    for (int i = frameDurations.length-1; i >= 0; i--) {
                        adder += frameDurations[i];
                        if (remainder <= adder){
                            frameNumber = i;
                            break;
                        }
                    }
                }
        }

        this.lastFrameNumber = frameNumber;
        this.lastStateTime = stateTime;
        return frameNumber;
    }


    public TextureRegion[] getkeyframes() {
        return this.keyframes;
    }



    /**
     * 设置帧间隔持续时间以及帧列表,所有间隔一致
     *
     * @param frameDuration
     * @param keyframes
     */
    protected void setkeyframes(float frameDuration,TextureRegion... keyframes) {
        //更新帧列表
        this.keyframes = keyframes;
        //更新每帧持续时间
        frameDurations = new float[keyframes.length];
        Arrays.fill(frameDurations, frameDuration);
        //更新动画总持续时间
        updateAnimationDuration();
    }

    /**
     * 设置帧间隔持续时间以及帧列表
     * 传入描述间隔时间的列表和帧列表
     *
     * @param frameDurations
     * @param keyframes
     */
    protected void setkeyframes(float[] frameDurations,TextureRegion... keyframes) {
        //更新帧列表
        this.keyframes = keyframes;
        //更新每帧持续时间
        this.frameDurations = frameDurations;
        //更新动画总持续时间
        updateAnimationDuration();
    }


    public Animation.PlayMode getPlayMode() {
        return this.playMode;
    }


    public void setPlayMode(Animation.PlayMode playMode) {
        this.playMode = playMode;
    }


    /**
     * 返回是否完成一便的播放
     *
     * @return
     */
    public boolean isAnimationFinished() {
        return stateTime >= totalDuration;
    }



    /**
     * 设置所有帧持续的时间
     * 传入一帧的时间,设置到所有帧上
     *
     * @param frameDuration
     */
    public void setFrameDuration(float frameDuration) {
        for (int i = 0; i < frameDurations.length; i++) {
            frameDurations[i] = frameDuration;
        }
        totalDuration = frameDurations.length * frameDuration;
    }

    /**
     * 设置传入帧的间隔持续时间
     * 传入帧索引以及此帧要设置的时间
     *
     * @param index
     * @param frameDuration
     */
    public void setFrameDuration(int index,float frameDuration) {
        //设置传入帧的间隔持续时间
        frameDurations[index] = frameDuration;
        //更新动画持续时间
        updateAnimationDuration();
    }

    /**
     * 获取传入索引帧的间隔持续时间
     *
     * @param index
     * @return
     */
    public float getFrameDuration(int index) {
        return frameDurations[index];
    }

    /**
     * 获取整个动画帧间隔列表
     *
     * @return
     */
    public float[] getFrameDurations() {
        return frameDurations;
    }


    public float getAnimationDuration() {
        return this.totalDuration;
    }

    private void updateAnimationDuration(){
        totalDuration = 0;
        for (float duration : frameDurations) {
            totalDuration += duration;
        }
    }
    private int findIndex(float remainingTime, float[] frameDurations) {
        int index = 0;
        float cumulativeTime = 0.0f;
        for (int i = 0; i < frameDurations.length; i++) {
            cumulativeTime += frameDurations[i];
            if (remainingTime < cumulativeTime) {
                index = i;
                break;
            }
        }
        return index;
    }
}
