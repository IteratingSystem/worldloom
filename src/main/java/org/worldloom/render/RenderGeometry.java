package org.worldloom.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import org.worldloom.component.Pos;
import org.worldloom.component.Render;

/** 共用实际绘制参数计算包围盒，避免裁剪边界与精灵的旋转、堆叠错位。 */
public final class RenderGeometry {
    /** 工具类无需创建实例。 */
    private RenderGeometry() { }

    /** 使用引擎既有原点与缩放约定绘制一层精灵。 */
    public static void draw(Batch batch, Render render, Pos pos, TextureRegion region,
                            float height, int sheet, float worldScale) {
        batch.draw(region.getTexture(), worldScale * (pos.x + render.offsetX),
            worldScale * (pos.y + render.offsetY + height + sheet * render.sheetOffset),
            render.originX, render.originY,
            render.keyframe.getRegionWidth(), render.keyframe.getRegionHeight(),
            worldScale * render.scaleWidth, worldScale * render.scaleHeight,
            render.rotation, region.getRegionX(), region.getRegionY(),
            render.keyframe.getRegionWidth(), render.keyframe.getRegionHeight(),
            render.flipX, render.flipY);
    }

    /** 把全部堆叠层的实际绘制矩形旋转为保守世界包围盒，复用调用方结果。 */
    public static void bounds(Render render, Pos pos, float height, float worldScale, Rectangle out) {
        float originX = render.originX;
        float originY = render.originY;
        float left = -originX * worldScale * render.scaleWidth;
        float right = (render.keyframe.getRegionWidth() - originX) * worldScale * render.scaleWidth;
        float bottom = -originY * worldScale * render.scaleHeight;
        float top = (render.keyframe.getRegionHeight() - originY) * worldScale * render.scaleHeight;
        float cos = MathUtils.cosDeg(render.rotation);
        float sin = MathUtils.sinDeg(render.rotation);
        float x1 = cos * left - sin * bottom;
        float y1 = sin * left + cos * bottom;
        float x2 = cos * left - sin * top;
        float y2 = sin * left + cos * top;
        float x3 = cos * right - sin * top;
        float y3 = sin * right + cos * top;
        float x4 = cos * right - sin * bottom;
        float y4 = sin * right + cos * bottom;
        float minX = Math.min(Math.min(x1, x2), Math.min(x3, x4));
        float maxX = Math.max(Math.max(x1, x2), Math.max(x3, x4));
        float minY = Math.min(Math.min(y1, y2), Math.min(y3, y4));
        float maxY = Math.max(Math.max(y1, y2), Math.max(y3, y4));
        int sheets = render.textureSheets == null ? 1 : Math.max(1, render.textureSheets.size);
        float sheetOffset = (sheets - 1) * render.sheetOffset * worldScale;
        out.set(worldScale * (pos.x + render.offsetX) + originX + minX,
            worldScale * (pos.y + render.offsetY + height) + originY + minY + Math.min(0f, sheetOffset),
            maxX - minX, maxY - minY + Math.abs(sheetOffset));
    }
}
