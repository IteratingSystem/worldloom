package org.worldloom.light;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.LongMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;

/** 阴影使用的区域透明度缓存；仅主线程使用，不拥有纹理和Pixmap。 */
public final class OpaqueRegionCache {
    private static final String TAG = OpaqueRegionCache.class.getSimpleName();
    private static final float OPAQUE_ALPHA_THRESHOLD = 0.01f;
    private final ObjectMap<Texture, LongMap<Bounds>> opaqueBoundsCache = new ObjectMap<>();
    private final ObjectSet<Texture> unreadableOpaqueTextures = new ObjectSet<>();

    /** 创建仅保存区域边界的缓存，不分配GL资源。 */
    public OpaqueRegionCache() { }

    /** 世界释放或纹理内容重载后清空引用，共享纹理由资源所有者释放。 */
    public void clear() { opaqueBoundsCache.clear(); unreadableOpaqueTextures.clear(); }

    /** 首次扫描区域透明度并缓存结果；无法读取时回退整块区域且每张纹理只记录一次。 */
    public Bounds get(TextureRegion region) {
        Texture texture = region.getTexture();
        long key = getRegionKey(region);
        LongMap<Bounds> textureBounds = opaqueBoundsCache.get(texture);
        if (textureBounds != null) {
            Bounds cached = textureBounds.get(key);
            if (cached != null) {
                return cached;
            }
        } else {
            textureBounds = new LongMap<>();
            opaqueBoundsCache.put(texture, textureBounds);
        }

        Bounds bounds = readBounds(region);
        textureBounds.put(key, bounds);
        return bounds;
    }

    /** 扫描可读像素的非透明范围，按TextureData所有权释放临时Pixmap。 */
    private Bounds readBounds(TextureRegion region) {
        Texture texture = region.getTexture();
        if (unreadableOpaqueTextures.contains(texture)) {
            return Bounds.full(region);
        }
        TextureData data = texture.getTextureData();
        if (data == null || data.getType() != TextureData.TextureDataType.Pixmap) {
            logUnreadableTexture(texture, "Texture data is not pixmap-backed");
            return Bounds.full(region);
        }

        Pixmap pixmap = null;
        try {
            if (!data.isPrepared()) {
                data.prepare();
            }
            pixmap = data.consumePixmap();
            int minimumX = region.getRegionWidth();
            int minimumY = region.getRegionHeight();
            int maximumX = -1;
            int maximumY = -1;
            int startX = region.getRegionX();
            int startY = region.getRegionY();
            for (int y = 0; y < region.getRegionHeight(); y++) {
                for (int x = 0; x < region.getRegionWidth(); x++) {
                    int alpha = pixmap.getPixel(startX + x, startY + y) & 0xff;
                    if (alpha / 255f < OPAQUE_ALPHA_THRESHOLD) {
                        continue;
                    }
                    minimumX = Math.min(minimumX, x);
                    minimumY = Math.min(minimumY, y);
                    maximumX = Math.max(maximumX, x);
                    maximumY = Math.max(maximumY, y);
                }
            }
            if (maximumX < minimumX || maximumY < minimumY) {
                return Bounds.full(region);
            }
            return new Bounds(
                minimumX, maximumX + 1f,
                region.getRegionHeight() - maximumY - 1f,
                region.getRegionHeight() - minimumY);
        } catch (RuntimeException exception) {
            logUnreadableTexture(texture,
                "Unable to read texture alpha: " + exception.getMessage());
            return Bounds.full(region);
        } finally {
            if (pixmap != null && data.disposePixmap()) {
                pixmap.dispose();
            }
        }
    }

    /** 同一张不可读取的纹理只记录一次回退诊断。 */
    private void logUnreadableTexture(Texture texture, String message) {
        if (unreadableOpaqueTextures.add(texture)) {
            Gdx.app.log(TAG, message + "; using the full texture region");
        }
    }

    /** 以图集区域坐标和尺寸编码缓存键，不依赖TextureRegion实例身份。 */
    private long getRegionKey(TextureRegion region) {
        return ((long) region.getRegionX() & 0xffffL) << 48
            | ((long) region.getRegionY() & 0xffffL) << 32
            | ((long) region.getRegionWidth() & 0xffffL) << 16
            | ((long) region.getRegionHeight() & 0xffffL);
    }

    /** 区域局部像素坐标，Y轴向上；右侧和顶部是排他边界。 */
    public record Bounds(float left, float right, float bottom, float top) {
        /** 全透明或不可读取时沿用完整区域，保持原有阴影回退行为。 */
        private static Bounds full(TextureRegion region) {
            return new Bounds(0f, region.getRegionWidth(), 0f, region.getRegionHeight());
        }
    }
}
