package org.worldloom.navigation;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.ChainShape;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.EdgeShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;

/** 将Box2D静态Fixture转换为导航网格障碍。 */
final class Box2dNavigationRasterizer {
    private static final float EPSILON = 0.0001f;

    private Box2dNavigationRasterizer() {
    }

    static boolean[] rasterize(World world, int width, int height,
                               float tileWidth, float tileHeight,
                               float worldScale) {
        boolean[] blocked = new boolean[width * height];
        Array<Body> bodies = new Array<>();
        world.getBodies(bodies);
        for (Body body : bodies) {
            if (body.getType() != BodyDef.BodyType.StaticBody || !body.isActive()) {
                continue;
            }
            for (Fixture fixture : body.getFixtureList()) {
                if (fixture.isSensor()) {
                    continue;
                }
                rasterizeFixture(fixture, blocked, width, height,
                    tileWidth, tileHeight, worldScale);
            }
        }
        return blocked;
    }

    private static void rasterizeFixture(Fixture fixture, boolean[] blocked,
                                         int width, int height,
                                         float tileWidth, float tileHeight,
                                         float worldScale) {
        Shape shape = fixture.getShape();
        Bounds bounds = getBounds(shape, fixture.getBody(), worldScale);
        int minColumn = clamp((int)Math.floor(bounds.minX / tileWidth), 0, width - 1);
        int maxColumn = clamp((int)Math.floor(bounds.maxX / tileWidth), 0, width - 1);
        int minRow = clamp((int)Math.floor(bounds.minY / tileHeight), 0, height - 1);
        int maxRow = clamp((int)Math.floor(bounds.maxY / tileHeight), 0, height - 1);
        float inset = shape.getType() == Shape.Type.Edge
            || shape.getType() == Shape.Type.Chain ? 0f : EPSILON;

        for (int row = minRow; row <= maxRow; row++) {
            for (int column = minColumn; column <= maxColumn; column++) {
                float minX = column * tileWidth + inset;
                float minY = row * tileHeight + inset;
                float maxX = (column + 1) * tileWidth - inset;
                float maxY = (row + 1) * tileHeight - inset;
                if (overlaps(shape, fixture.getBody(), minX, minY, maxX, maxY,
                    worldScale)) {
                    blocked[row * width + column] = true;
                }
            }
        }
    }

    private static Bounds getBounds(Shape shape, Body body, float worldScale) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        Array<Vector2> vertices = getWorldVertices(shape, body, worldScale);
        for (Vector2 vertex : vertices) {
            minX = Math.min(minX, vertex.x);
            minY = Math.min(minY, vertex.y);
            maxX = Math.max(maxX, vertex.x);
            maxY = Math.max(maxY, vertex.y);
        }
        if (shape.getType() == Shape.Type.Circle) {
            float radius = shape.getRadius() / worldScale;
            minX -= radius;
            minY -= radius;
            maxX += radius;
            maxY += radius;
        }
        return new Bounds(minX, minY, maxX, maxY);
    }

    private static Array<Vector2> getWorldVertices(Shape shape, Body body,
                                                    float worldScale) {
        Array<Vector2> vertices = new Array<>();
        Vector2 local = new Vector2();
        switch (shape.getType()) {
            case Polygon -> {
                PolygonShape polygon = (PolygonShape)shape;
                for (int i = 0; i < polygon.getVertexCount(); i++) {
                    polygon.getVertex(i, local);
                    vertices.add(toWorld(body, local, worldScale));
                }
            }
            case Circle -> vertices.add(toWorld(body,
                ((CircleShape)shape).getPosition(), worldScale));
            case Edge -> {
                EdgeShape edge = (EdgeShape)shape;
                edge.getVertex1(local);
                vertices.add(toWorld(body, local, worldScale));
                edge.getVertex2(local);
                vertices.add(toWorld(body, local, worldScale));
            }
            case Chain -> {
                ChainShape chain = (ChainShape)shape;
                for (int i = 0; i < chain.getVertexCount(); i++) {
                    chain.getVertex(i, local);
                    vertices.add(toWorld(body, local, worldScale));
                }
            }
        }
        return vertices;
    }

    private static boolean overlaps(Shape shape, Body body,
                                    float minX, float minY,
                                    float maxX, float maxY,
                                    float worldScale) {
        return switch (shape.getType()) {
            case Polygon -> polygonOverlaps((PolygonShape)shape, body,
                minX, minY, maxX, maxY, worldScale);
            case Circle -> circleOverlaps((CircleShape)shape, body,
                minX, minY, maxX, maxY, worldScale);
            case Edge -> edgeOverlaps((EdgeShape)shape, body,
                minX, minY, maxX, maxY, worldScale);
            case Chain -> chainOverlaps((ChainShape)shape, body,
                minX, minY, maxX, maxY, worldScale);
        };
    }

    private static boolean polygonOverlaps(PolygonShape shape, Body body,
                                           float minX, float minY,
                                           float maxX, float maxY,
                                           float worldScale) {
        int count = shape.getVertexCount();
        Vector2[] vertices = new Vector2[count];
        Vector2 local = new Vector2();
        for (int i = 0; i < count; i++) {
            shape.getVertex(i, local);
            vertices[i] = toWorld(body, local, worldScale);
            if (insideRectangle(vertices[i], minX, minY, maxX, maxY)) {
                return true;
            }
        }
        if (pointInPolygon(minX, minY, vertices)
            || pointInPolygon(maxX, minY, vertices)
            || pointInPolygon(maxX, maxY, vertices)
            || pointInPolygon(minX, maxY, vertices)) {
            return true;
        }
        for (int i = 0; i < count; i++) {
            if (segmentIntersectsRectangle(vertices[i], vertices[(i + 1) % count],
                minX, minY, maxX, maxY)) {
                return true;
            }
        }
        return false;
    }

    private static boolean circleOverlaps(CircleShape shape, Body body,
                                          float minX, float minY,
                                          float maxX, float maxY,
                                          float worldScale) {
        Vector2 center = toWorld(body, shape.getPosition(), worldScale);
        float nearestX = Math.max(minX, Math.min(center.x, maxX));
        float nearestY = Math.max(minY, Math.min(center.y, maxY));
        float dx = center.x - nearestX;
        float dy = center.y - nearestY;
        float radius = shape.getRadius() / worldScale;
        return dx * dx + dy * dy < radius * radius;
    }

    private static boolean edgeOverlaps(EdgeShape shape, Body body,
                                        float minX, float minY,
                                        float maxX, float maxY,
                                        float worldScale) {
        Vector2 localA = new Vector2();
        Vector2 localB = new Vector2();
        shape.getVertex1(localA);
        shape.getVertex2(localB);
        Vector2 a = toWorld(body, localA, worldScale);
        Vector2 b = toWorld(body, localB, worldScale);
        return segmentIntersectsRectangle(a, b, minX, minY, maxX, maxY);
    }

    private static boolean chainOverlaps(ChainShape shape, Body body,
                                         float minX, float minY,
                                         float maxX, float maxY,
                                         float worldScale) {
        Vector2 previousLocal = new Vector2();
        Vector2 currentLocal = new Vector2();
        shape.getVertex(0, previousLocal);
        Vector2 previous = toWorld(body, previousLocal, worldScale);
        for (int i = 1; i < shape.getVertexCount(); i++) {
            shape.getVertex(i, currentLocal);
            Vector2 current = toWorld(body, currentLocal, worldScale);
            if (segmentIntersectsRectangle(previous, current,
                minX, minY, maxX, maxY)) {
                return true;
            }
            previous = current;
        }
        return false;
    }

    private static boolean insideRectangle(Vector2 point, float minX, float minY,
                                           float maxX, float maxY) {
        return point.x > minX && point.x < maxX
            && point.y > minY && point.y < maxY;
    }

    private static boolean pointInPolygon(float x, float y, Vector2[] vertices) {
        boolean inside = false;
        for (int i = 0, previous = vertices.length - 1;
             i < vertices.length; previous = i++) {
            Vector2 a = vertices[i];
            Vector2 b = vertices[previous];
            if ((a.y > y) != (b.y > y)
                && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static boolean segmentIntersectsRectangle(Vector2 a, Vector2 b,
                                                       float minX, float minY,
                                                       float maxX, float maxY) {
        if (insideRectangle(a, minX, minY, maxX, maxY)
            || insideRectangle(b, minX, minY, maxX, maxY)) {
            return true;
        }
        return segmentsIntersect(a.x, a.y, b.x, b.y, minX, minY, maxX, minY)
            || segmentsIntersect(a.x, a.y, b.x, b.y, maxX, minY, maxX, maxY)
            || segmentsIntersect(a.x, a.y, b.x, b.y, maxX, maxY, minX, maxY)
            || segmentsIntersect(a.x, a.y, b.x, b.y, minX, maxY, minX, minY);
    }

    private static boolean segmentsIntersect(float ax, float ay, float bx, float by,
                                             float cx, float cy, float dx, float dy) {
        if (Math.max(ax, bx) < Math.min(cx, dx)
            || Math.max(cx, dx) < Math.min(ax, bx)
            || Math.max(ay, by) < Math.min(cy, dy)
            || Math.max(cy, dy) < Math.min(ay, by)) {
            return false;
        }
        float abC = cross(ax, ay, bx, by, cx, cy);
        float abD = cross(ax, ay, bx, by, dx, dy);
        float cdA = cross(cx, cy, dx, dy, ax, ay);
        float cdB = cross(cx, cy, dx, dy, bx, by);
        return abC * abD <= 0f && cdA * cdB <= 0f;
    }

    private static float cross(float ax, float ay, float bx, float by,
                               float px, float py) {
        return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static Vector2 toWorld(Body body, Vector2 local, float worldScale) {
        return body.getWorldPoint(local).cpy().scl(1f / worldScale);
    }

    private record Bounds(float minX, float minY, float maxX, float maxY) {
    }
}
