package org.worldloom.light;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.ObjectMap;

import java.util.BitSet;

/** 阴影透明度与轮廓缓存；每张纹理只解码一次，不拥有共享纹理。仅主线程使用。 */
public final class OpaqueRegionCache {
    private static final String TAG = OpaqueRegionCache.class.getSimpleName();
    private static final float OPAQUE_ALPHA_THRESHOLD = 0.01f;
    private final ObjectMap<Texture, TexturePixels> textures = new ObjectMap<>();

    /** 创建纯CPU缓存，首次使用区域时才读取像素。 */
    public OpaqueRegionCache() { }

    /** 世界释放或纹理重载时清空CPU引用，共享纹理由原所有者释放。 */
    public void clear() { textures.clear(); }

    /** 返回Y轴向上的不透明边界；全透明或不可读区域保留整框回退。 */
    public Bounds get(TextureRegion region) { return regionData(region).bounds; }

    /** 首次请求时构建闭合轮廓体；不可读纹理返回null，透明帧返回零顶点几何。 */
    public ShadowVolumeGeometry getVolume(TextureRegion region) {
        TexturePixels pixels = texturePixels(region.getTexture());
        if (pixels.opaque == null) return null;
        RegionKey key = RegionKey.of(region);
        RegionData data = regionData(region);
        if (data.volume == null) {
            boolean[] mask = new boolean[Math.multiplyExact(key.width, key.height)];
            for (int y = 0; y < key.height; y++)
                for (int x = 0; x < key.width; x++)
                    mask[y * key.width + x] = pixels.at(key, x, y);
            data.volume = ShadowVolumeGeometry.build(key.width, key.height, mask);
        }
        return data.volume;
    }

    /** 按图集坐标及翻转缓存区域，避免依赖每帧创建的TextureRegion对象身份。 */
    private RegionData regionData(TextureRegion region) {
        TexturePixels pixels = texturePixels(region.getTexture());
        RegionKey key = RegionKey.of(region);
        RegionData cached = pixels.regions.get(key);
        if (cached != null) return cached;
        int left = key.width, bottom = key.height, right = -1, top = -1;
        if (pixels.opaque != null) {
            for (int y = 0; y < key.height; y++) {
                for (int x = 0; x < key.width; x++) {
                    if (!pixels.at(key, x, y)) continue;
                    left = Math.min(left, x); right = Math.max(right, x);
                    bottom = Math.min(bottom, y); top = Math.max(top, y);
                }
            }
        }
        Bounds bounds = right < left ? Bounds.full(region)
            : new Bounds(left, right + 1f, bottom, top + 1f);
        RegionData result = new RegionData(bounds);
        pixels.regions.put(key, result);
        return result;
    }

    /** 只解码整张纹理一次，立即按TextureData所有权释放临时Pixmap。 */
    private TexturePixels texturePixels(Texture texture) {
        TexturePixels cached = textures.get(texture);
        if (cached != null) return cached;
        TextureData data = texture.getTextureData();
        Pixmap pixmap = null;
        TexturePixels result;
        try {
            if (data == null || data.getType() != TextureData.TextureDataType.Pixmap)
                throw new IllegalStateException("纹理不支持Pixmap读取");
            if (!data.isPrepared()) data.prepare();
            pixmap = data.consumePixmap();
            BitSet opaque = new BitSet(Math.multiplyExact(pixmap.getWidth(), pixmap.getHeight()));
            for (int y = 0; y < pixmap.getHeight(); y++)
                for (int x = 0; x < pixmap.getWidth(); x++)
                    if ((pixmap.getPixel(x, y) & 0xff) / 255f >= OPAQUE_ALPHA_THRESHOLD)
                        opaque.set(y * pixmap.getWidth() + x);
            result = new TexturePixels(pixmap.getWidth(), pixmap.getHeight(), opaque);
        } catch (RuntimeException exception) {
            if (Gdx.app != null) Gdx.app.log(TAG, "无法读取阴影轮廓，沿用切片投影：" + exception.getMessage());
            result = new TexturePixels(texture.getWidth(), texture.getHeight(), null);
        } finally {
            if (pixmap != null && data.disposePixmap()) pixmap.dispose();
        }
        textures.put(texture, result);
        return result;
    }

    /** 单张图集的位掩码与各帧派生数据，不保存Pixmap或GL资源。 */
    private static final class TexturePixels {
        final int width, height;
        final BitSet opaque;
        final ObjectMap<RegionKey, RegionData> regions = new ObjectMap<>();

        /** 保存图集像素尺寸及压缩的透明度掩码。 */
        TexturePixels(int width, int height, BitSet opaque) {
            this.width = width; this.height = height; this.opaque = opaque;
        }

        /** 将局部向上坐标映射到图集向下坐标，同时兼容区域自身的UV翻转。 */
        boolean at(RegionKey key, int x, int y) {
            int sourceX = key.x + (key.flipX ? key.width - 1 - x : x);
            int sourceY = key.y + (key.flipY ? y : key.height - 1 - y);
            return sourceX >= 0 && sourceX < width && sourceY >= 0 && sourceY < height
                && opaque.get(sourceY * width + sourceX);
        }
    }

    /** 区域边界立即缓存，较大的轮廓顶点仅在太阳投影实际需要时生成。 */
    private static final class RegionData {
        final Bounds bounds;
        ShadowVolumeGeometry volume;
        /** 保存只读边界，轮廓几何延迟初始化。 */
        RegionData(Bounds bounds) { this.bounds = bounds; }
    }

    /** 缓存键包含UV翻转，避免翻转区域与原区域串用轮廓。 */
    private record RegionKey(int x, int y, int width, int height, boolean flipX, boolean flipY) {
        /** 以UV较小端定位图集像素，兼容TextureRegion翻转后的负向跨度。 */
        static RegionKey of(TextureRegion region) {
            return new RegionKey(Math.round(Math.min(region.getU(), region.getU2()) * region.getTexture().getWidth()),
                Math.round(Math.min(region.getV(), region.getV2()) * region.getTexture().getHeight()),
                region.getRegionWidth(), region.getRegionHeight(), region.isFlipX(), region.isFlipY());
        }
    }

    /** 区域局部像素坐标，Y轴向上；右侧和顶部是排他边界。 */
    public record Bounds(float left, float right, float bottom, float top) {
        /** 全透明或不可读取时沿用完整区域，保持原有边界回退行为。 */
        private static Bounds full(TextureRegion region) {
            return new Bounds(0f, region.getRegionWidth(), 0f, region.getRegionHeight());
        }
    }
}
