package org.worldloom.box2d;

import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;

/** 夹具与矩形的精确相交查询，所有坐标均为Box2D世界单位；不负责传感器或业务过滤。 */
public final class FixtureGeometry {
    /** 工具类不持有物理World或夹具引用。 */
    private FixtureGeometry() { }

    /** 面形状要求实际重叠，圆相切不算重叠；线形状与矩形接触也算相交。 */
    public static boolean overlaps(Fixture fixture, Rectangle rectangle) {
        return intersects(fixture, rectangle, false);
    }

    /** 多边形边缘接触也算相交，供导航栅格等显式处理边界的调用方使用；圆仍要求重叠。 */
    public static boolean intersects(Fixture fixture, Rectangle rectangle) {
        return intersects(fixture, rectangle, true);
    }

    /** 共用世界顶点转换及线段查询，仅多边形按调用入口选择边界规则。 */
    private static boolean intersects(Fixture fixture, Rectangle rect, boolean polygonBoundary) {
        Shape shape = fixture.getShape();
        Body body = fixture.getBody();
        Vector2 a = new Vector2(), b = new Vector2();
        if (shape instanceof CircleShape circle) {
            a.set(body.getWorldPoint(circle.getPosition()));
            return Intersector.overlaps(new Circle(a.x, a.y, circle.getRadius()), rect);
        }
        if (shape instanceof PolygonShape polygon) {
            float[] vertices = new float[polygon.getVertexCount() * 2];
            for (int i = 0; i < polygon.getVertexCount(); i++) {
                polygon.getVertex(i, a); a.set(body.getWorldPoint(a));
                vertices[i * 2] = a.x; vertices[i * 2 + 1] = a.y;
            }
            if (!polygonBoundary) return Intersector.overlapConvexPolygons(vertices,
                new float[] {rect.x, rect.y, rect.x + rect.width, rect.y,
                    rect.x + rect.width, rect.y + rect.height, rect.x, rect.y + rect.height}, null);
            for (int i = 0; i < vertices.length; i += 2) {
                int next = (i + 2) % vertices.length;
                a.set(vertices[i], vertices[i + 1]); b.set(vertices[next], vertices[next + 1]);
                if (Intersector.intersectSegmentRectangle(a, b, rect)) return true;
            }
            return Intersector.isPointInPolygon(vertices, 0, vertices.length, rect.x, rect.y)
                || Intersector.isPointInPolygon(vertices, 0, vertices.length, rect.x + rect.width, rect.y)
                || Intersector.isPointInPolygon(vertices, 0, vertices.length, rect.x, rect.y + rect.height)
                || Intersector.isPointInPolygon(vertices, 0, vertices.length, rect.x + rect.width, rect.y + rect.height);
        }
        if (shape instanceof EdgeShape edge) {
            edge.getVertex1(a); edge.getVertex2(b);
            return segment(body, a, b, rect);
        }
        if (shape instanceof ChainShape chain) {
            for (int i = 1; i < chain.getVertexCount(); i++) {
                chain.getVertex(i - 1, a); chain.getVertex(i, b);
                if (segment(body, a, b, rect)) return true;
            }
            if (chain.isLooped() && chain.getVertexCount() > 1) {
                chain.getVertex(chain.getVertexCount() - 1, a); chain.getVertex(0, b);
                return segment(body, a, b, rect);
            }
        }
        return false;
    }

    /** 立即复制Body返回的临时坐标，避免第二次转换覆盖第一端点。 */
    private static boolean segment(Body body, Vector2 a, Vector2 b, Rectangle rect) {
        a.set(body.getWorldPoint(a)); b.set(body.getWorldPoint(b));
        return Intersector.intersectSegmentRectangle(a, b, rect);
    }
}
