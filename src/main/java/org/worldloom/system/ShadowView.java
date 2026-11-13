package org.worldloom.system;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.BufferUtils;
import java.nio.IntBuffer;

/** 阴影阶段的视口与GL纹理状态；不拥有相机或纹理，成对捕获/恢复。 */
final class ShadowView {
    private final IntBuffer glStateBuffer = BufferUtils.newIntBuffer(1);
    private final int[] previousTextureBindings = new int[4];
    private float shadowCameraOriginalX, shadowCameraOriginalY;
    private float shadowUvOffsetX, shadowUvOffsetY;
    private boolean shadowCameraActive;
    private int bufferWidth, bufferHeight;

    /**
     * 将阴影视图对齐到阴影缓冲自身的纹素网格。
     * 主世界仍按屏幕像素移动，二者的差值在最终合成时补偿。
     */
    void beginCamera(com.badlogic.gdx.graphics.OrthographicCamera camera, int bufferWidth, int bufferHeight) {
        if (shadowCameraActive || bufferWidth <= 0 || bufferHeight <= 0) {
            return;
        }
        this.bufferWidth = bufferWidth;
        this.bufferHeight = bufferHeight;
        shadowCameraOriginalX = camera.position.x;
        shadowCameraOriginalY = camera.position.y;
        float viewWidth = camera.viewportWidth
            * camera.zoom;
        float viewHeight = camera.viewportHeight
            * camera.zoom;
        float texelX = viewWidth / bufferWidth;
        float texelY = viewHeight / bufferHeight;
        float shadowX = snapDown(shadowCameraOriginalX, texelX);
        float shadowY = snapDown(shadowCameraOriginalY, texelY);
        shadowUvOffsetX = (shadowCameraOriginalX - shadowX) / viewWidth;
        shadowUvOffsetY = (shadowCameraOriginalY - shadowY) / viewHeight;
        camera.position.set(
            shadowX, shadowY, camera.position.z);
        camera.update();
        shadowCameraActive = true;
    }

    /** 恢复像素相机本帧用于世界渲染的位置。 */
    void endCamera(com.badlogic.gdx.graphics.OrthographicCamera camera) {
        if (!shadowCameraActive) {
            return;
        }
        camera.position.set(
            shadowCameraOriginalX, shadowCameraOriginalY,
            camera.position.z);
        camera.update();
        shadowCameraActive = false;
    }

    /** 对齐到不大于当前位置的缓冲纹素，避免阴影相机游移。 */
    private float snapDown(float value, float step) {
        if (step <= 0f) {
            return value;
        }
        return (float) Math.floor(value / step) * step;
    }

    /**
     * 保存本系统会占用的纹理单元，避免污染海洋等其它多纹理Shader。
     */
    int captureTextureBindings() {
        int activeTexture = getGlInteger(GL20.GL_ACTIVE_TEXTURE);
        for (int unit = 0; unit < previousTextureBindings.length; unit++) {
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0 + unit);
            previousTextureBindings[unit] =
                getGlInteger(GL20.GL_TEXTURE_BINDING_2D);
        }
        Gdx.gl.glActiveTexture(activeTexture);
        return activeTexture;
    }

    /** 恢复本次阴影阶段占用的四个纹理单元。 */
    void restoreTextureBindings(int activeTexture) {
        for (int unit = 0; unit < previousTextureBindings.length; unit++) {
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0 + unit);
            Gdx.gl.glBindTexture(
                GL20.GL_TEXTURE_2D, previousTextureBindings[unit]);
        }
        Gdx.gl.glActiveTexture(activeTexture);
    }

    /** 复用缓冲读取单个GL状态，不分配临时数组。 */
    private int getGlInteger(int parameter) {
        glStateBuffer.clear();
        Gdx.gl.glGetIntegerv(parameter, glStateBuffer);
        return glStateBuffer.get(0);
    }

    /** 将阴影视口的纹素大小及相对主相机偏移写入合成Shader。 */
    void applyUniforms(ShaderProgram shader, float shadowTime) {
        shader.setUniformf("u_shadowTexel",
            1f / bufferWidth, 1f / bufferHeight);
        shader.setUniformf("u_shadowUvOffset",
            shadowUvOffsetX, shadowUvOffsetY);
        shader.setUniformf("u_time", shadowTime);
    }
}
