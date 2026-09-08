package org.worldloom.navigation;

import com.badlogic.gdx.ai.pfa.Heuristic;

/** 与八方向网格移动代价一致的八边形距离。 */
public final class NavigationHeuristic implements Heuristic<NavigationNode> {
    private static final float DIAGONAL_COST = 1.41421356f;
    private final float minimumTerrainCost;

    public NavigationHeuristic() {
        this(1f);
    }

    public NavigationHeuristic(float minimumTerrainCost) {
        if (minimumTerrainCost <= 0f) {
            throw new IllegalArgumentException("minimum terrain cost must be positive");
        }
        this.minimumTerrainCost = minimumTerrainCost;
    }

    @Override
    public float estimate(NavigationNode node, NavigationNode endNode) {
        int dx = Math.abs(node.column - endNode.column);
        int dy = Math.abs(node.row - endNode.row);
        int diagonal = Math.min(dx, dy);
        return (diagonal * DIAGONAL_COST + Math.max(dx, dy) - diagonal)
            * minimumTerrainCost;
    }
}
