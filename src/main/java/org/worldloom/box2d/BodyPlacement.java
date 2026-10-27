package org.worldloom.box2d;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;

/** 跨图前在目标物理环境预检实体碰撞形状，不依赖渲染范围或导航网格精度。 */
public final class BodyPlacement {
    /** 几何检查工具无需实例化。 */
    private BodyPlacement() { }

    /** 用实体实心Fixture检查目标位置；感应器和掩码不允许的接触不阻止迁移。 */
    public static boolean canPlace(Body source, World destination, float x, float y) {
        for (Fixture fixture : source.getFixtureList()) {
            if (fixture.isSensor()) continue;
            Geometry moving = geometry(fixture, x - source.getPosition().x, y - source.getPosition().y);
            boolean[] blocked = {false};
            destination.QueryAABB(other -> {
                if (other.getBody() == source || !other.getBody().isActive() || other.isSensor()) return true;
                Filter a = fixture.getFilterData(), b = other.getFilterData();
                boolean collide = a.groupIndex != 0 && a.groupIndex == b.groupIndex
                    ? a.groupIndex > 0 : (a.maskBits & b.categoryBits) != 0 && (b.maskBits & a.categoryBits) != 0;
                if (collide && overlaps(moving, geometry(other, 0f, 0f))) { blocked[0] = true; return false; }
                return true;
            }, moving.minX, moving.minY, moving.maxX, moving.maxY);
            if (blocked[0]) return false;
        }
        return true;
    }

    /** 将Fixture几何转换到目标世界坐标；仅借用形状，不创建或释放原生Shape。 */
    private static Geometry geometry(Fixture fixture, float dx, float dy) {
        Shape shape = fixture.getShape();
        Geometry result = new Geometry();
        Vector2 point = new Vector2();
        if (shape instanceof CircleShape circle) {
            point.set(fixture.getBody().getWorldPoint(circle.getPosition())).add(dx, dy);
            result.vertices = new float[]{point.x, point.y};
            result.radius = circle.getRadius();
            result.circle = true;
        } else {
            int count = shape instanceof PolygonShape polygon ? polygon.getVertexCount()
                : shape instanceof ChainShape chain ? chain.getVertexCount() : 2;
            result.closed = shape instanceof PolygonShape;
            result.vertices = new float[count * 2];
            for (int i = 0; i < count; i++) {
                if (shape instanceof PolygonShape polygon) polygon.getVertex(i, point);
                else if (shape instanceof ChainShape chain) chain.getVertex(i, point);
                else if (shape instanceof EdgeShape edge) { if (i == 0) edge.getVertex1(point); else edge.getVertex2(point); }
                point.set(fixture.getBody().getWorldPoint(point)).add(dx, dy);
                result.vertices[i * 2] = point.x; result.vertices[i * 2 + 1] = point.y;
            }
        }
        for (int i = 0; i < result.vertices.length; i += 2) {
            result.minX = Math.min(result.minX, result.vertices[i] - result.radius);
            result.maxX = Math.max(result.maxX, result.vertices[i] + result.radius);
            result.minY = Math.min(result.minY, result.vertices[i + 1] - result.radius);
            result.maxY = Math.max(result.maxY, result.vertices[i + 1] + result.radius);
        }
        return result;
    }

    /** 对凸多边形、圆及边链执行交叠检测，避免把门口整块瓦片误当成障碍。 */
    private static boolean overlaps(Geometry a, Geometry b) {
        if (a.circle && b.circle) {
            float radius = a.radius + b.radius;
            return Vector2.dst2(a.vertices[0], a.vertices[1], b.vertices[0], b.vertices[1]) < radius * radius;
        }
        if (a.circle) return circleOverlaps(a, b);
        if (b.circle) return circleOverlaps(b, a);
        if (a.closed && b.closed) return Intersector.overlapConvexPolygons(a.vertices, b.vertices, null);
        if (a.closed && Intersector.isPointInPolygon(a.vertices, 0, a.vertices.length, b.vertices[0], b.vertices[1])) return true;
        if (b.closed && Intersector.isPointInPolygon(b.vertices, 0, b.vertices.length, a.vertices[0], a.vertices[1])) return true;
        for (int i = 0; i < a.edgeCount(); i++) {
            int ai = i * 2, aj = ((i + 1) * 2) % a.vertices.length;
            for (int j = 0; j < b.edgeCount(); j++) {
                int bi = j * 2, bj = ((j + 1) * 2) % b.vertices.length;
                if (Intersector.intersectSegments(a.vertices[ai], a.vertices[ai + 1], a.vertices[aj], a.vertices[aj + 1],
                    b.vertices[bi], b.vertices[bi + 1], b.vertices[bj], b.vertices[bj + 1], null)) return true;
            }
        }
        return false;
    }

    /** 检查圆心落入实心区域或圆边界与多边形、边链相交。 */
    private static boolean circleOverlaps(Geometry circle, Geometry other) {
        float x = circle.vertices[0], y = circle.vertices[1];
        if (other.closed && Intersector.isPointInPolygon(other.vertices, 0, other.vertices.length, x, y)) return true;
        for (int i = 0; i < other.edgeCount(); i++) {
            int a = i * 2, b = ((i + 1) * 2) % other.vertices.length;
            if (Intersector.distanceSegmentPoint(other.vertices[a], other.vertices[a + 1], other.vertices[b], other.vertices[b + 1], x, y) < circle.radius) return true;
        }
        return false;
    }

    /** 仅在迁移请求时创建的轻量几何快照，不持有Box2D原生资源。 */
    private static final class Geometry {
        float[] vertices;
        float radius;
        boolean circle, closed;
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        /** 返回需要参与交叠检测的边数，边链不补首尾连线。 */
        int edgeCount() { return vertices.length / 2 - (closed ? 0 : 1); }
    }
}
