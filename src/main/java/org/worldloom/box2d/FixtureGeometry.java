package org.worldloom.box2d;

import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.ChainShape;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.EdgeShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;

/** 夹具与矩形的精确相交查询，所有坐标均为Box2D世界单位；不负责传感器或业务过滤。 */
public final class FixtureGeometry {
    /** 工具类不持有物理World或夹具引用。 */
    private FixtureGeometry() { }

    /** 沿用LibGDX形状边界语义：圆相切不算，多边形及线段接触边界算相交；业务容差由调用方施加。 */
    public static boolean overlaps(Fixture fixture, Rectangle rect) {
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
            return Intersector.overlapConvexPolygons(vertices,
                new float[] {rect.x, rect.y, rect.x + rect.width, rect.y,
                    rect.x + rect.width, rect.y + rect.height, rect.x, rect.y + rect.height}, null);
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
