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
                if (org.worldloom.box2d.FixtureGeometry.intersects(fixture,
                    new com.badlogic.gdx.math.Rectangle(minX * worldScale, minY * worldScale,
                        (maxX - minX) * worldScale, (maxY - minY) * worldScale))) {
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

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static Vector2 toWorld(Body body, Vector2 local, float worldScale) {
        return body.getWorldPoint(local).cpy().scl(1f / worldScale);
    }

    private record Bounds(float minX, float minY, float maxX, float maxY) {
    }
}
