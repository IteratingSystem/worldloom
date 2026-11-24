package org.worldloom.physics;

import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.EllipseMapObject;
import com.badlogic.gdx.maps.objects.PolygonMapObject;
import com.badlogic.gdx.maps.objects.PolylineMapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.Ellipse;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Polyline;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import dev.lyze.projectTrianglePlatforming.TiledTileCollisionToBox2dOptions;
import dev.lyze.projectTrianglePlatforming.utils.MapUtils;
import dev.lyze.projectTrianglePlatforming.utils.PolygonUtils;

import java.util.IdentityHashMap;

/**
 * 只解析含有碰撞对象的瓦片，保持第三方碰撞库的非合并形状语义。
 *
 * <p>地图仍以原始Tiled层为唯一数据源。解析器只在进入物理世界时建立一次
 * 稀疏坐标列表，后续按坐标读取原始Cell，不创建第二份地图层。</p>
 */
public final class SparseTiledTileCollisionToBox2d {
    private final TiledTileCollisionToBox2dOptions options;
    private final IdentityHashMap<TiledMapTile, Boolean> collisionTiles = new IdentityHashMap<>();

    /** 使用与第三方解析器相同的选项创建稀疏解析器。 */
    public SparseTiledTileCollisionToBox2d(TiledTileCollisionToBox2dOptions options) {
        if (options == null) throw new IllegalArgumentException("Collision options cannot be null");
        if (options.isCombineTileCollisions())
            throw new IllegalArgumentException("Sparse collision parser requires disabled tile merging");
        this.options = options;
    }

    /** 扫描图层一次并仅解析含碰撞对象的坐标。 */
    public void parseLayer(TiledMapTileLayer layer, World world) {
        Array<CellPosition> cells = new Array<>();
        for (int x = 0; x < layer.getWidth(); x++) {
            for (int y = 0; y < layer.getHeight(); y++) {
                TiledMapTileLayer.Cell cell = layer.getCell(x, y);
                if (cell != null && hasCollision(cell.getTile())) cells.add(new CellPosition(x, y));
            }
        }
        for (CellPosition cell : cells) parseCell(layer, cell.x(), cell.y(), world);
    }

    /** 预先缓存瓦片是否包含有效碰撞对象，避免对百万个空瓦片反复查询对象列表。 */
    private boolean hasCollision(TiledMapTile tile) {
        if (tile == null) return false;
        Boolean cached = collisionTiles.get(tile);
        if (cached != null) return cached;
        boolean result = false;
        for (MapObject object : tile.getObjects()) {
            if (!object.getProperties().get("ptpIgnore", false, Boolean.class)) {
                result = true;
                break;
            }
        }
        collisionTiles.put(tile, result);
        return result;
    }

    /** 按第三方解析器的对象类型和坐标规则建立单瓦片形状。 */
    private void parseCell(TiledMapTileLayer layer, int x, int y, World world) {
        TiledMapTileLayer.Cell cell = layer.getCell(x, y);
        if (cell == null || !hasCollision(cell.getTile())) return;
        int originX = x * layer.getTileWidth();
        int originY = y * layer.getTileHeight();
        for (MapObject object : cell.getTile().getObjects()) {
            if (object.getProperties().get("ptpIgnore", false, Boolean.class)) continue;
            if (object instanceof EllipseMapObject ellipse) {
                extractCircle(world, originX, originY, ellipse.getEllipse());
            } else if (object instanceof RectangleMapObject rectangle) {
                extractRectangle(world, originX, originY, rectangle.getRectangle());
            } else if (object instanceof PolylineMapObject polyline) {
                extractPolyline(world, originX, originY, polyline.getPolyline());
            } else if (object instanceof PolygonMapObject polygon) {
                extractPolygon(world, originX, originY, polygon.getPolygon());
            }
        }
    }

    /** 建立多边形碰撞，沿用原库的简化、凹多边形三角化和顶点限制。 */
    private void extractPolygon(World world, int originX, int originY, Polygon polygon) {
        float[] vertices = PolygonUtils.transformVertices(
            polygon.getTransformedVertices(), options.getScale(),
            originX * options.getScale(), originY * options.getScale());
        if (polygon.getVertices().length > 8 && !options.isTriangulateInsteadOfThrow())
            throw new IllegalArgumentException("Polygon vertices > 8");
        MapUtils.extractPolygon(world, vertices, options.getTriangulator());
    }

    /** 建立折线碰撞，保持原库的链形状语义。 */
    private void extractPolyline(World world, int originX, int originY, Polyline polyline) {
        float[] vertices = PolygonUtils.transformVertices(
            polyline.getTransformedVertices(), options.getScale(),
            originX * options.getScale(), originY * options.getScale());
        MapUtils.extractPolyline(world, vertices);
    }

    /** 建立矩形碰撞，保持原库的世界坐标和缩放规则。 */
    private void extractRectangle(World world, int originX, int originY, Rectangle rectangle) {
        float x = originX * options.getScale() + rectangle.x * options.getScale();
        float y = originY * options.getScale() + rectangle.y * options.getScale();
        MapUtils.extractRectangle(world, x, y,
            rectangle.width * options.getScale(), rectangle.height * options.getScale());
    }

    /** 建立圆形碰撞，非正圆沿用原库的拒绝策略。 */
    private void extractCircle(World world, int originX, int originY, Ellipse ellipse) {
        if (ellipse.width != ellipse.height) {
            if (options.isThrowOnInvalidObject())
                throw new IllegalArgumentException("Ellipse objects not supported by Box2D");
            return;
        }
        float x = originX * options.getScale() + (ellipse.x + ellipse.width / 2f) * options.getScale();
        float y = originY * options.getScale() + (ellipse.y + ellipse.height / 2f) * options.getScale();
        MapUtils.extractCircle(world, x, y, ellipse.width / 2f * options.getScale());
    }

    /** 单元格坐标只保存整数，避免为地图源对象创建临时矩形。 */
    private record CellPosition(int x, int y) { }
}
