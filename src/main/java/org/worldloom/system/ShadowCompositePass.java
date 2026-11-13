package org.worldloom.system;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import org.worldloom.light.TopDownShadowConfig;

/** 最终日光/点光合成阶段；借用系统资源，不分配或释放FBO、网格及Shader。 */
final class ShadowCompositePass {
    private final PixelPerfectRenderSystem pixelPerfectRenderSystem;
    private final TopDownShadowConfig config;
    private final Mesh screenQuad;
    private final ShaderProgram sunCompositeShader, pointCompositeShader;

    /** 借用已成功构建的渲染资源，所有权仍在TopDownShadowSystem。 */
    ShadowCompositePass(PixelPerfectRenderSystem target, TopDownShadowConfig config, Mesh quad,
                        ShaderProgram sunShader, ShaderProgram pointShader) {
        pixelPerfectRenderSystem = target;
        this.config = config;
        screenQuad = quad;
        sunCompositeShader = sunShader;
        pointCompositeShader = pointShader;
    }

    /** 以普通透明混合合成日光遮罩，继续使用既有世界渲染目标。 */
    void sun(ShadowBuffers buffers, ShadowView view, float time) {
        pixelPerfectRenderSystem.resumeWorldTarget();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        bindCompositeMasks(buffers, view, time, sunCompositeShader);
        sunCompositeShader.setUniformf(
            "u_shadowOpacity", config.getSunShadowOpacity());
        screenQuad.render(sunCompositeShader, GL20.GL_TRIANGLE_FAN);
        finishComposite();
    }

    /** 环境光之后以加法混合单个点光源，不改变遮挡判定。 */
    void point(ShadowBuffers buffers, ShadowView view, float time, Texture lightMap) {
        pixelPerfectRenderSystem.resumeWorldTarget();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
        buffers.receiverShadowMask.getColorBufferTexture().bind(3);
        buffers.entityMask.getColorBufferTexture().bind(2);
        buffers.groundShadowMask.getColorBufferTexture().bind(1);
        lightMap.bind(0);
        pointCompositeShader.bind();
        pointCompositeShader.setUniformi("u_lightMap", 0);
        pointCompositeShader.setUniformi("u_groundShadow", 1);
        pointCompositeShader.setUniformi("u_entityMask", 2);
        pointCompositeShader.setUniformi("u_receiverShadow", 3);
        view.applyUniforms(pointCompositeShader, time);
        screenQuad.render(pointCompositeShader, GL20.GL_TRIANGLE_FAN);
        finishComposite();
    }

    /** 绑定三张日光遮罩并写入统一视口参数。 */
    private void bindCompositeMasks(ShadowBuffers buffers, ShadowView view, float time, ShaderProgram shader) {
        buffers.receiverShadowMask.getColorBufferTexture().bind(2);
        buffers.entityMask.getColorBufferTexture().bind(1);
        buffers.groundShadowMask.getColorBufferTexture().bind(0);
        shader.bind();
        shader.setUniformi("u_groundShadow", 0);
        shader.setUniformi("u_entityMask", 1);
        shader.setUniformi("u_receiverShadow", 2);
        view.applyUniforms(shader, time);
    }

    /** 结束合成，恢复默认活动纹理单元并关闭混合。 */
    private void finishComposite() {
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }
}
