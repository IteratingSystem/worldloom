package org.worldloom.system;

import com.artemis.BaseSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import org.worldloom.camera.PixelPerfectCameraConfig;

/**
 * 在世界渲染开始前建立扩边缓冲并对齐摄像机。
 *
 * <p>游戏系统使用临时FrameBuffer后必须调用{@link #resumeWorldTarget()}，
 * 以便后续内容继续写入世界缓冲。</p>
 */
public final class PixelPerfectRenderSystem extends BaseSystem {
    private static final String TAG =
        PixelPerfectRenderSystem.class.getSimpleName();

    private final PixelPerfectCameraConfig config;
    private CameraSystem cameraSystem;
    private FrameBuffer worldBuffer;
    private TextureRegion worldRegion;
    private int bufferWidth;
    private int bufferHeight;
    private boolean rendering;
    private float logicalX;
    private float logicalY;
    private float viewportWidth;
    private float viewportHeight;
    private float worldUnitsPerPixelX;
    private float worldUnitsPerPixelY;
    private float subpixelX;
    private float subpixelY;

    public PixelPerfectRenderSystem(PixelPerfectCameraConfig config) {
        this.config = config;
    }

    @Override
    protected void initialize() {
        if (config.isEnabled()) {
            ensureBuffer();
            Gdx.app.log(TAG, "Smooth pixel camera initialized");
        }
    }

    @Override
    protected void processSystem() {
        if (!config.isEnabled()) {
            return;
        }
        ensureBuffer();
        beginCameraRender();
        worldBuffer.begin();
        rendering = true;
        // 沿用页面在本帧设置的清屏颜色，避免改变既有背景表现。
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }

    private void beginCameraRender() {
        int padding = config.getOverscanPixels();
        logicalX = cameraSystem.camera.position.x;
        logicalY = cameraSystem.camera.position.y;
        viewportWidth = cameraSystem.camera.viewportWidth;
        viewportHeight = cameraSystem.camera.viewportHeight;
        worldUnitsPerPixelX = viewportWidth
            * cameraSystem.camera.zoom / Math.max(1, bufferWidth - 2 * padding);
        worldUnitsPerPixelY = viewportHeight
            * cameraSystem.camera.zoom / Math.max(1, bufferHeight - 2 * padding);

        float snappedX = snapDown(logicalX, worldUnitsPerPixelX);
        float snappedY = snapDown(logicalY, worldUnitsPerPixelY);
        subpixelX = logicalX - snappedX;
        subpixelY = logicalY - snappedY;
        cameraSystem.camera.position.set(
            snappedX, snappedY, cameraSystem.camera.position.z);
        cameraSystem.camera.viewportWidth = viewportWidth
            + 2f * padding * worldUnitsPerPixelX
            / cameraSystem.camera.zoom;
        cameraSystem.camera.viewportHeight = viewportHeight
            + 2f * padding * worldUnitsPerPixelY
            / cameraSystem.camera.zoom;
        cameraSystem.camera.update();
    }

    private float snapDown(float value, float step) {
        if (step <= 0f) {
            return value;
        }
        return (float) Math.floor(value / step) * step;
    }

    /** 临时FrameBuffer结束后重新绑定当前世界缓冲。 */
    public void resumeWorldTarget() {
        if (!rendering || worldBuffer == null) {
            return;
        }
        worldBuffer.bind();
        Gdx.gl.glViewport(0, 0, bufferWidth, bufferHeight);
    }

    void finishWorldTarget() {
        if (!rendering) {
            return;
        }
        worldBuffer.end();
        rendering = false;
        cameraSystem.camera.position.set(
            logicalX, logicalY, cameraSystem.camera.position.z);
        cameraSystem.camera.viewportWidth = viewportWidth;
        cameraSystem.camera.viewportHeight = viewportHeight;
        cameraSystem.camera.update();
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    public int getRenderWidth() {
        return config.isEnabled()
            ? Math.max(1, Gdx.graphics.getBackBufferWidth())
                + 2 * config.getOverscanPixels()
            : Math.max(1, Gdx.graphics.getBackBufferWidth());
    }

    public int getRenderHeight() {
        return config.isEnabled()
            ? Math.max(1, Gdx.graphics.getBackBufferHeight())
                + 2 * config.getOverscanPixels()
            : Math.max(1, Gdx.graphics.getBackBufferHeight());
    }

    TextureRegion getWorldRegion() {
        return worldRegion;
    }

    int getPadding() {
        return config.getOverscanPixels();
    }

    float getScreenOffsetX() {
        return worldUnitsPerPixelX <= 0f
            ? 0f : subpixelX / worldUnitsPerPixelX;
    }

    float getScreenOffsetY() {
        return worldUnitsPerPixelY <= 0f
            ? 0f : subpixelY / worldUnitsPerPixelY;
    }

    /** 新缓冲完整创建后才替换旧目标，失败时不留下尺寸已更新但纹理为空的状态。 */
    private void ensureBuffer() {
        int width = getRenderWidth();
        int height = getRenderHeight();
        if (worldBuffer != null
            && width == bufferWidth && height == bufferHeight) {
            return;
        }
        FrameBuffer next = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        TextureRegion nextRegion;
        try {
            Texture texture = next.getColorBufferTexture();
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            texture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
            nextRegion = new TextureRegion(texture);
            nextRegion.flip(false, true);
        } catch (RuntimeException | Error failure) {
            next.dispose();
            throw failure;
        }
        FrameBuffer previous = worldBuffer;
        worldBuffer = next; worldRegion = nextRegion;
        bufferWidth = width; bufferHeight = height;
        if (previous != null) previous.dispose();
        Gdx.app.log(TAG, "World buffer resized: "
            + bufferWidth + "x" + bufferHeight);
    }

    private void disposeBuffer() {
        if (worldBuffer != null) {
            worldBuffer.dispose();
            worldBuffer = null;
            worldRegion = null;
        }
    }

    @Override
    protected void dispose() {
        disposeBuffer();
    }
}
