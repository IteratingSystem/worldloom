package org.worldloom.navigation;

import com.badlogic.gdx.ai.pfa.Connection;
import com.badlogic.gdx.ai.pfa.indexed.IndexedGraph;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;

/**
 * 从Box2D静态碰撞形状建立的八方向导航网格。
 *
 * <p>地图仅提供网格范围和瓦片尺寸，障碍数据以Box2D世界为唯一来源。</p>
 */
public final class NavigationGrid implements IndexedGraph<NavigationNode> {
    private static final float DIAGONAL_COST = 1.41421356f;

    private final int width;
    private final int height;
    private final float tileWidth;
    private final float tileHeight;
    private final NavigationNode[] nodes;
    private final boolean[] blocked;
    private final float[] terrainCosts;
    private final Array<Connection<NavigationNode>>[] connections;

    /** 创建有限正尺寸网格；净空在各工厂生成障碍膨胀前校验。 */
    @SuppressWarnings("unchecked")
    private NavigationGrid(int width, int height, float tileWidth, float tileHeight,
                           boolean[] blocked, float[] terrainCosts) {
        validateMetrics(tileWidth, tileHeight, 0f);
        this.width = width;
        this.height = height;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.blocked = blocked;
        this.terrainCosts = terrainCosts;
        nodes = new NavigationNode[width * height];
        connections = new Array[nodes.length];
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                int index = indexOf(column, row);
                nodes[index] = new NavigationNode(index, column, row,
                    (column + 0.5f) * tileWidth, (row + 0.5f) * tileHeight);
            }
        }
        buildConnections();
    }

    /** 使用布尔障碍数据建立网格，主要用于工具和测试。 */
    public static NavigationGrid fromBlockedCells(boolean[][] cells,
                                                   float tileWidth,
                                                   float tileHeight,
                                                   float clearance) {
        validateMetrics(tileWidth, tileHeight, clearance);
        if (cells == null || cells.length == 0 || cells[0] == null || cells[0].length == 0) {
            throw new IllegalArgumentException("blocked cells cannot be empty");
        }
        int height = cells.length;
        int width = cells[0].length;
        boolean[] source = new boolean[width * height];
        for (int row = 0; row < height; row++) {
            if (cells[row] == null || cells[row].length != width) {
                throw new IllegalArgumentException("blocked cell rows must have equal length");
            }
            for (int column = 0; column < width; column++) {
                source[row * width + column] = cells[row][column];
            }
        }
        float[] terrainCosts = new float[width * height];
        java.util.Arrays.fill(terrainCosts, 1f);
        return new NavigationGrid(width, height, tileWidth, tileHeight,
            expandObstacles(source, width, height, tileWidth, tileHeight, clearance),
            terrainCosts);
    }

    /** 使用布尔障碍和道路数据建立带权网格，主要用于工具和测试。 */
    public static NavigationGrid fromWeightedCells(boolean[][] blockedCells,
                                                    boolean[][] roadCells,
                                                    float tileWidth,
                                                    float tileHeight,
                                                    float roadCost,
                                                    float offRoadCost,
                                                    float clearance) {
        validateMetrics(tileWidth, tileHeight, clearance);
        if (blockedCells == null || blockedCells.length == 0 || blockedCells[0] == null
            || blockedCells[0].length == 0 || roadCells == null
            || roadCells.length != blockedCells.length) {
            throw new IllegalArgumentException("weighted cells cannot be empty");
        }
        validateCosts(roadCost, offRoadCost);
        int height = blockedCells.length;
        int width = blockedCells[0].length;
        boolean[] blocked = new boolean[width * height];
        float[] costs = new float[width * height];
        for (int row = 0; row < height; row++) {
            if (blockedCells[row] == null || roadCells[row] == null
                || blockedCells[row].length != width
                || roadCells[row].length != width) {
                throw new IllegalArgumentException("weighted cell rows must have equal length");
            }
            for (int column = 0; column < width; column++) {
                int index = row * width + column;
                blocked[index] = blockedCells[row][column];
                costs[index] = roadCells[row][column] ? roadCost : offRoadCost;
            }
        }
        return new NavigationGrid(width, height, tileWidth, tileHeight,
            expandObstacles(blocked, width, height, tileWidth, tileHeight, clearance),
            costs);
    }

    /** 使用当前Box2D世界中的静态碰撞形状建立网格。 */
    public static NavigationGrid fromBox2D(TiledMap map, World box2dWorld,
                                           float worldScale,
                                           TiledMapTileLayer roadLayer,
                                           float roadCost, float offRoadCost,
                                           float clearance) {
        if (map == null) {
            throw new IllegalArgumentException("map cannot be null");
        }
        if (box2dWorld == null) {
            throw new IllegalArgumentException("Box2D world cannot be null");
        }
        if (!Float.isFinite(worldScale) || worldScale <= 0f) {
            throw new IllegalArgumentException("world scale must be positive");
        }
        validateCosts(roadCost, offRoadCost);
        int width = map.getProperties().get("width", 0, Integer.class);
        int height = map.getProperties().get("height", 0, Integer.class);
        int tileWidth = map.getProperties().get("tilewidth", 0, Integer.class);
        int tileHeight = map.getProperties().get("tileheight", 0, Integer.class);
        validateMetrics(tileWidth, tileHeight, clearance);
        if (width <= 0 || height <= 0 || tileWidth <= 0 || tileHeight <= 0) {
            throw new IllegalArgumentException("map must define a finite tile grid");
        }

        boolean[] source = Box2dNavigationRasterizer.rasterize(box2dWorld,
            width, height, tileWidth, tileHeight, worldScale);
        float[] terrainCosts = createTerrainCosts(width, height, roadLayer,
            roadCost, offRoadCost);
        return new NavigationGrid(width, height, tileWidth, tileHeight,
            expandObstacles(source, width, height, tileWidth, tileHeight, clearance),
            terrainCosts);
    }

    /** 越界或非有限坐标没有可用节点，不能被整数转换误映射为原点。 */
    public NavigationNode getNodeAt(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) return null;
        int column = (int)Math.floor(x / tileWidth);
        int row = (int)Math.floor(y / tileHeight);
        if (!contains(column, row)) {
            return null;
        }
        NavigationNode node = nodes[indexOf(column, row)];
        return blocked[node.index] ? null : node;
    }

    public boolean isBlocked(int column, int row) {
        return !contains(column, row) || blocked[indexOf(column, row)];
    }

    public float getTileWidth() {
        return tileWidth;
    }

    public float getTileHeight() {
        return tileHeight;
    }

    @Override
    public int getIndex(NavigationNode node) {
        return node.index;
    }

    @Override
    public int getNodeCount() {
        return nodes.length;
    }

    @Override
    public Array<Connection<NavigationNode>> getConnections(NavigationNode fromNode) {
        return connections[fromNode.index];
    }

    private void buildConnections() {
        for (NavigationNode node : nodes) {
            Array<Connection<NavigationNode>> result = new Array<>(8);
            connections[node.index] = result;
            if (blocked[node.index]) {
                continue;
            }
            for (int rowOffset = -1; rowOffset <= 1; rowOffset++) {
                for (int columnOffset = -1; columnOffset <= 1; columnOffset++) {
                    if (columnOffset == 0 && rowOffset == 0) {
                        continue;
                    }
                    int column = node.column + columnOffset;
                    int row = node.row + rowOffset;
                    if (isBlocked(column, row)) {
                        continue;
                    }
                    boolean diagonal = columnOffset != 0 && rowOffset != 0;
                    if (diagonal && (isBlocked(node.column + columnOffset, node.row)
                        || isBlocked(node.column, node.row + rowOffset))) {
                        continue;
                    }
                    NavigationNode target = nodes[indexOf(column, row)];
                    float terrainCost = (terrainCosts[node.index]
                        + terrainCosts[target.index]) * 0.5f;
                    result.add(new NavigationConnection(node, target,
                        (diagonal ? DIAGONAL_COST : 1f) * terrainCost));
                }
            }
        }
    }

    private boolean contains(int column, int row) {
        return column >= 0 && row >= 0 && column < width && row < height;
    }

    private int indexOf(int column, int row) {
        return row * width + column;
    }

    private static boolean[] expandObstacles(boolean[] source, int width, int height,
                                             float tileWidth, float tileHeight,
                                             float clearance) {
        boolean[] result = source.clone();
        if (clearance <= 0f) {
            return result;
        }
        int rangeX = (int)Math.ceil(clearance / tileWidth + 0.5f);
        int rangeY = (int)Math.ceil(clearance / tileHeight + 0.5f);
        float clearanceSquared = clearance * clearance;
        for (int obstacleRow = 0; obstacleRow < height; obstacleRow++) {
            for (int obstacleColumn = 0; obstacleColumn < width; obstacleColumn++) {
                if (!source[obstacleRow * width + obstacleColumn]) {
                    continue;
                }
                for (int row = Math.max(0, obstacleRow - rangeY);
                     row <= Math.min(height - 1, obstacleRow + rangeY); row++) {
                    for (int column = Math.max(0, obstacleColumn - rangeX);
                         column <= Math.min(width - 1, obstacleColumn + rangeX); column++) {
                        float distanceX = Math.max(0f,
                            (Math.abs(column - obstacleColumn) - 0.5f) * tileWidth);
                        float distanceY = Math.max(0f,
                            (Math.abs(row - obstacleRow) - 0.5f) * tileHeight);
                        if (distanceX * distanceX + distanceY * distanceY
                            < clearanceSquared) {
                            result[row * width + column] = true;
                        }
                    }
                }
            }
        }
        return result;
    }

    private static float[] createTerrainCosts(int width, int height,
                                              TiledMapTileLayer roadLayer,
                                              float roadCost,
                                              float offRoadCost) {
        float[] costs = new float[width * height];
        if (roadLayer == null) {
            java.util.Arrays.fill(costs, 1f);
            return costs;
        }
        int layerWidth = Math.min(width, roadLayer.getWidth());
        int layerHeight = Math.min(height, roadLayer.getHeight());
        java.util.Arrays.fill(costs, offRoadCost);
        for (int row = 0; row < layerHeight; row++) {
            for (int column = 0; column < layerWidth; column++) {
                if (roadLayer.getCell(column, row) != null) {
                    costs[row * width + column] = roadCost;
                }
            }
        }
        return costs;
    }

    /** 网格尺寸必须为有限正数，净空允许零但不能为负数或非有限数。 */
    private static void validateMetrics(float tileWidth, float tileHeight, float clearance) {
        if (!Float.isFinite(tileWidth) || !Float.isFinite(tileHeight)
            || tileWidth <= 0f || tileHeight <= 0f || !Float.isFinite(clearance) || clearance < 0f)
            throw new IllegalArgumentException("Tile dimensions must be finite and positive; clearance must be finite and non-negative");
    }

    /** 道路权重参与A*比较，不接受NaN或无穷大。 */
    private static void validateCosts(float roadCost, float offRoadCost) {
        if (!Float.isFinite(roadCost) || !Float.isFinite(offRoadCost)
            || roadCost <= 0f || offRoadCost < roadCost) {
            throw new IllegalArgumentException(
                "navigation costs must satisfy 0 < road cost <= off-road cost");
        }
    }
}
