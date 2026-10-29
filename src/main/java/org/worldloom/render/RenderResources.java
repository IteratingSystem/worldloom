package org.worldloom.render;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.math.MathUtils;

/** 常用绘制资源的创建入口；每次返回独立资源，由调用方在GL线程释放。 */
public final class RenderResources {
    /** 工具类不保存共享GL资源。 */
    private RenderResources() { }

    /** 创建位置与UV各两维的全屏四边形，使用GL_TRIANGLE_FAN绘制。 */
    public static Mesh createScreenQuad() {
        Mesh mesh = new Mesh(true, 4, 0,
            new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"),
            new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord"));
        mesh.setVertices(new float[] {-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f,
            1f, 1f, 1f, 1f, -1f, 1f, 0f, 1f});
        return mesh;
    }

    /** 创建白色径向软遮罩；solidRadius为不透明半径占比，外围线性衰减并使用线性过滤。 */
    public static Texture createSoftMask(int size, float solidRadius) {
        if (size < 2 || !Float.isFinite(solidRadius) || solidRadius < 0f || solidRadius >= 1f)
            throw new IllegalArgumentException("遮罩尺寸至少为2，不透明半径必须在[0,1)内");
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        try {
            float center = (size - 1) * 0.5f;
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
                float dx = (x - center) / center, dy = (y - center) / center;
                float distance = (float)Math.sqrt(dx * dx + dy * dy);
                float alpha = 1f - MathUtils.clamp((distance - solidRadius) / (1f - solidRadius), 0f, 1f);
                pixmap.setColor(1f, 1f, 1f, alpha);
                pixmap.drawPixel(x, y);
            }
            Texture texture = new Texture(pixmap);
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            return texture;
        } finally { pixmap.dispose(); }
    }
}
