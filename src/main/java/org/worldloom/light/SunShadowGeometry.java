package org.worldloom.light;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import org.worldloom.component.Pos;
import org.worldloom.component.Render;

/** 不依赖ECS或GL的太阳投影几何，数组随实例复用，只在主线程顺序计算和消费。 */
public final class SunShadowGeometry {
    private final float worldScale;
    private final float[] ribbonLocal = new float[8];
    private final float[] ribbonCandidates = new float[16];
    private final float[] ribbonHull = new float[32];

    /** 使用与精灵绘制一致的像素到世界缩放。 */
    public SunShadowGeometry(float worldScale) { this.worldScale = worldScale; }

    /** 返回当前凸包数组，只读且仅在下次buildRibbon前有效。 */
    public float[] vertices() { return ribbonHull; }

    /** 计算投影的世界纵向跨度，由调用方按目标缓冲分辨率换算纹素。 */
    public float verticalSpan(
        Render render, Pos pos, TextureRegion region, float extraY,
        float footY, float left, float right, float bottom, float top,
        Vector2 lightDirection, float shadowLengthScale) {
        projectBounds(render, pos, region, extraY, footY,
            left, right, bottom, top, 0f, lightDirection, shadowLengthScale);
        float minimumY = Float.POSITIVE_INFINITY;
        float maximumY = Float.NEGATIVE_INFINITY;
        for (int i = 1; i < 8; i += 2) {
            minimumY = Math.min(minimumY, ribbonCandidates[i]);
            maximumY = Math.max(maximumY, ribbonCandidates[i]);
        }
        return maximumY - minimumY;
    }

    /** 生成退化投影的凸阴影带，返回顶点数，结果保存在vertices中供当前绘制使用。 */
    public int buildRibbon(
        Render render, Pos pos, TextureRegion region, float extraY,
        float footY, float left, float right, float bottom, float top,
        float depth, Vector2 lightDirection, float shadowLengthScale) {
        projectBounds(render, pos, region, extraY, footY,
            left, right, bottom, top,
            Math.max(depth, 0.0001f) * 0.5f, lightDirection, shadowLengthScale);
        sortRibbonCandidates();
        return buildRibbonConvexHull();
    }

    /** 按精灵翻转、原点、缩放与旋转生成太阳方向上的四角投影。 */
    private void projectBounds(
        Render render, Pos pos, TextureRegion region, float extraY,
        float footY, float left, float right, float bottom, float top,
        float halfDepth, Vector2 lightDirection, float shadowLengthScale) {
        if (render.flipX) {
            float originalLeft = left;
            left = region.getRegionWidth() - right;
            right = region.getRegionWidth() - originalLeft;
        }
        if (render.flipY) {
            float originalBottom = bottom;
            bottom = region.getRegionHeight() - top;
            top = region.getRegionHeight() - originalBottom;
        }

        ribbonLocal[0] = left;
        ribbonLocal[1] = bottom;
        ribbonLocal[2] = right;
        ribbonLocal[3] = bottom;
        ribbonLocal[4] = right;
        ribbonLocal[5] = top;
        ribbonLocal[6] = left;
        ribbonLocal[7] = top;
        float drawX = worldScale * (pos.x + render.offsetX);
        float drawY = worldScale * (pos.y + render.offsetY + extraY);
        float scaleX = worldScale * render.scaleWidth;
        float scaleY = worldScale * render.scaleHeight;
        float cosine = MathUtils.cosDeg(render.rotation);
        float sine = MathUtils.sinDeg(render.rotation);
        for (int i = 0; i < 4; i++) {
            float relativeX = (ribbonLocal[i * 2] - render.originX) * scaleX;
            float relativeY = (ribbonLocal[i * 2 + 1] - render.originY) * scaleY;
            float spriteX = drawX + render.originX
                + relativeX * cosine - relativeY * sine;
            float spriteY = drawY + render.originY
                + relativeX * sine + relativeY * cosine;
            float pixelHeight = Math.max(0f, spriteY - footY);
            float projectedX = spriteX
                + lightDirection.x * pixelHeight * shadowLengthScale;
            float projectedY = footY
                + lightDirection.y * pixelHeight * shadowLengthScale;
            // 凸阴影带与普通投影使用相同锚点，向太阳移动半个纵深。
            projectedX -= lightDirection.x * halfDepth;
            projectedY -= lightDirection.y * halfDepth;
            if (halfDepth <= 0f) {
                ribbonCandidates[i * 2] = projectedX;
                ribbonCandidates[i * 2 + 1] = projectedY;
            } else {
                int lower = i * 4;
                ribbonCandidates[lower] = projectedX;
                ribbonCandidates[lower + 1] = projectedY - halfDepth;
                ribbonCandidates[lower + 2] = projectedX;
                ribbonCandidates[lower + 3] = projectedY + halfDepth;
            }
        }
    }

    /** 按横坐标和纵坐标稳定排序八个候选点。 */
    private void sortRibbonCandidates() {
        for (int i = 1; i < 8; i++) {
            float x = ribbonCandidates[i * 2];
            float y = ribbonCandidates[i * 2 + 1];
            int insertion = i;
            while (insertion > 0 && isPointAfter(
                ribbonCandidates[(insertion - 1) * 2],
                ribbonCandidates[(insertion - 1) * 2 + 1], x, y)) {
                ribbonCandidates[insertion * 2] =
                    ribbonCandidates[(insertion - 1) * 2];
                ribbonCandidates[insertion * 2 + 1] =
                    ribbonCandidates[(insertion - 1) * 2 + 1];
                insertion--;
            }
            ribbonCandidates[insertion * 2] = x;
            ribbonCandidates[insertion * 2 + 1] = y;
        }
    }

    /** 比较凸包候选点的字典序。 */
    private boolean isPointAfter(float firstX, float firstY,
                                 float secondX, float secondY) {
        return firstX > secondX || (firstX == secondX && firstY > secondY);
    }

    /** 以单调链生成凸包，剔除重复端点与共线中间点。 */
    private int buildRibbonConvexHull() {
        int hullSize = 0;
        for (int i = 0; i < 8; i++) {
            while (hullSize >= 2 && crossHullPoint(
                hullSize - 2, hullSize - 1,
                ribbonCandidates[i * 2], ribbonCandidates[i * 2 + 1]) <= 0f) {
                hullSize--;
            }
            setHullPoint(hullSize++, ribbonCandidates[i * 2],
                ribbonCandidates[i * 2 + 1]);
        }
        int lowerSize = hullSize;
        for (int i = 6; i >= 0; i--) {
            while (hullSize > lowerSize && crossHullPoint(
                hullSize - 2, hullSize - 1,
                ribbonCandidates[i * 2], ribbonCandidates[i * 2 + 1]) <= 0f) {
                hullSize--;
            }
            setHullPoint(hullSize++, ribbonCandidates[i * 2],
                ribbonCandidates[i * 2 + 1]);
        }
        return Math.max(0, hullSize - 1);
    }

    /** 计算已写入凸包两点与候选点的叉积。 */
    private float crossHullPoint(int first, int second, float x, float y) {
        float firstX = ribbonHull[first * 2];
        float firstY = ribbonHull[first * 2 + 1];
        float secondX = ribbonHull[second * 2];
        float secondY = ribbonHull[second * 2 + 1];
        return (secondX - firstX) * (y - firstY)
            - (secondY - firstY) * (x - firstX);
    }

    /** 写入复用顶点数组，避免逐帧分配几何对象。 */
    private void setHullPoint(int index, float x, float y) {
        ribbonHull[index * 2] = x;
        ribbonHull[index * 2 + 1] = y;
    }

}
