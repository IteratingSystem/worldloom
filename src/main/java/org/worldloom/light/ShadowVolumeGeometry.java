package org.worldloom.light;

import com.badlogic.gdx.utils.FloatArray;

/** 按像素轮廓挤出的闭合阴影体，坐标及纹理坐标均归一化；不依赖GL或实体。 */
public final class ShadowVolumeGeometry {
    public static final int FLOATS_PER_VERTEX = 5;
    private final float[] vertices;

    /** 固定构建结果，不保留调用者的像素掩码。 */
    private ShadowVolumeGeometry(float[] vertices) { this.vertices = vertices; }

    /** 返回只读顶点数据，依次为局部XY、纵深比例和纹理UV；调用方不得改写。 */
    public float[] vertices() { return vertices; }

    /** 返回三角形列表的顶点数，完全透明的帧为零。 */
    public int vertexCount() { return vertices.length / FLOATS_PER_VERTEX; }

    /** 从Y轴向上的不透明掩码构建前后面与轮廓侧面，合并同一直线上的相邻边。 */
    public static ShadowVolumeGeometry build(int width, int height, boolean[] opaque) {
        if (width <= 0 || height <= 0 || opaque == null || opaque.length != Math.multiplyExact(width, height))
            throw new IllegalArgumentException("阴影轮廓尺寸与像素掩码不匹配");
        boolean visible = false;
        for (boolean pixel : opaque) if (pixel) { visible = true; break; }
        if (!visible) return new ShadowVolumeGeometry(new float[0]);
        FloatArray out = new FloatArray();
        cap(out, 0f);
        cap(out, 1f);
        for (int y = 0; y <= height; y++) {
            int x = 0;
            while (x < width) {
                int side = horizontalSide(opaque, width, height, x, y);
                if (side == 0) { x++; continue; }
                int start = x++;
                while (x < width && horizontalSide(opaque, width, height, x, y) == side) x++;
                float v = (y + side * 0.5f) / height;
                wall(out, start / (float)width, y / (float)height, x / (float)width, y / (float)height,
                    (start + 0.5f) / width, v, (x - 0.5f) / width, v);
            }
        }
        for (int x = 0; x <= width; x++) {
            int y = 0;
            while (y < height) {
                int side = verticalSide(opaque, width, height, x, y);
                if (side == 0) { y++; continue; }
                int start = y++;
                while (y < height && verticalSide(opaque, width, height, x, y) == side) y++;
                float u = (x + side * 0.5f) / width;
                wall(out, x / (float)width, start / (float)height, x / (float)width, y / (float)height,
                    u, (start + 0.5f) / height, u, (y - 0.5f) / height);
            }
        }
        return new ShadowVolumeGeometry(out.toArray());
    }

    /** 水平边两侧只有一侧不透明时才形成外轮廓或孔洞边界，返回该侧方向。 */
    private static int horizontalSide(boolean[] mask, int width, int height, int x, int y) {
        boolean above = y < height && mask[y * width + x];
        boolean below = y > 0 && mask[(y - 1) * width + x];
        return above == below ? 0 : above ? 1 : -1;
    }

    /** 垂直边两侧只有一侧不透明时才生成侧面，不生成像素之间的内部面。 */
    private static int verticalSide(boolean[] mask, int width, int height, int x, int y) {
        boolean right = x < width && mask[y * width + x];
        boolean left = x > 0 && mask[y * width + x - 1];
        return right == left ? 0 : right ? 1 : -1;
    }

    /** 前后面保留完整纹理采样，透明区域由原有片元阈值裁掉。 */
    private static void cap(FloatArray out, float depth) {
        vertex(out, 0, 0, depth, 0, 0); vertex(out, 1, 0, depth, 1, 0); vertex(out, 1, 1, depth, 1, 1);
        vertex(out, 0, 0, depth, 0, 0); vertex(out, 1, 1, depth, 1, 1); vertex(out, 0, 1, depth, 0, 1);
    }

    /** 沿轮廓线生成连续纵深面；UV缩进到不透明像素中心，避免采到相邻透明格。 */
    private static void wall(FloatArray out, float ax, float ay, float bx, float by,
                             float au, float av, float bu, float bv) {
        vertex(out, ax, ay, 0, au, av); vertex(out, bx, by, 0, bu, bv); vertex(out, bx, by, 1, bu, bv);
        vertex(out, ax, ay, 0, au, av); vertex(out, bx, by, 1, bu, bv); vertex(out, ax, ay, 1, au, av);
    }

    /** 写入与光空间投影Shader一致的位置及纹理布局。 */
    private static void vertex(FloatArray out, float x, float y, float depth, float u, float v) {
        out.add(x, y, depth, u);
        out.add(v);
    }
}
