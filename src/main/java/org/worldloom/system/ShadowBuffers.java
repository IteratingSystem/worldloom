package org.worldloom.system;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/** 单个尺寸下的完整阴影缓冲集合；成功创建后才交给渲染系统替换旧集合。 */
final class ShadowBuffers implements Disposable {
    final FrameBuffer heightMapSource, heightMap, depthDownsampleBuffer, entityMask;
    final FrameBuffer groundShadowSource, groundShadowMask, receiverShadowMask, sunShadowMap;
    private final Array<FrameBuffer> owned = new Array<>();

    /** 部分分配失败时释放已完成的资源，调用方原有集合保持可用。 */
    ShadowBuffers(int width, int height) {
        try {
            int depth = Math.max(1, (height + 1) / 2);
            heightMapSource = create(width, height, false, TextureFilter.Nearest);
            heightMap = create(width, depth, false, TextureFilter.Linear);
            depthDownsampleBuffer = create(width, depth, false, TextureFilter.Nearest);
            entityMask = create(width, height, false, TextureFilter.Nearest);
            groundShadowSource = create(width, height, false, TextureFilter.Nearest);
            groundShadowMask = create(width, depth, false, TextureFilter.Linear);
            receiverShadowMask = create(width, height, false, TextureFilter.Linear);
            sunShadowMap = create(width, height, true, TextureFilter.Nearest);
        } catch (RuntimeException | Error failure) {
            try { dispose(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    /** 创建后立即登记所有权，再设置采样，覆盖后续设置失败的清理路径。 */
    private FrameBuffer create(int width, int height, boolean depth, TextureFilter filter) {
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, depth);
        owned.add(buffer);
        buffer.getColorBufferTexture().setFilter(filter, filter);
        return buffer;
    }

    /** 幂等逆序释放全部已登记缓冲。 */
    @Override public void dispose() {
        RuntimeException failure = null;
        while (owned.notEmpty()) {
            try { owned.pop().dispose(); }
            catch (RuntimeException error) {
                if (failure == null) failure = error; else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }
}
