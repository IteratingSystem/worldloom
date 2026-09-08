package org.worldloom.navigation;

import com.badlogic.gdx.ai.pfa.DefaultGraphPath;
import com.badlogic.gdx.ai.pfa.indexed.IndexedAStarPathFinder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationGridTest {
    @Test
    void findsPathAroundObstacle() {
        boolean[][] blocked = {
            {false, false, false, false, false},
            {false, false, true, false, false},
            {false, false, true, false, false},
            {false, false, true, false, false},
            {false, false, false, false, false}
        };
        NavigationGrid grid = NavigationGrid.fromBlockedCells(
            blocked, 16f, 16f, 0f);
        DefaultGraphPath<NavigationNode> path = new DefaultGraphPath<>();

        boolean found = new IndexedAStarPathFinder<>(grid).searchNodePath(
            grid.getNodeAt(8f, 40f), grid.getNodeAt(72f, 40f),
            new NavigationHeuristic(), path);

        assertTrue(found);
        assertTrue(path.getCount() > 2);
    }

    @Test
    void diagonalConnectionCannotCrossBlockedCorner() {
        boolean[][] blocked = {
            {false, true},
            {true, false}
        };
        NavigationGrid grid = NavigationGrid.fromBlockedCells(
            blocked, 16f, 16f, 0f);
        DefaultGraphPath<NavigationNode> path = new DefaultGraphPath<>();

        boolean found = new IndexedAStarPathFinder<>(grid).searchNodePath(
            grid.getNodeAt(8f, 8f), grid.getNodeAt(24f, 24f),
            new NavigationHeuristic(), path);

        assertFalse(found);
    }

    @Test
    void clearanceExpandsObstacleOnlyWhenAgentWouldOverlapIt() {
        boolean[][] blocked = {
            {false, false, false},
            {false, true, false},
            {false, false, false}
        };

        NavigationGrid smallAgent = NavigationGrid.fromBlockedCells(
            blocked, 16f, 16f, 7.9f);
        NavigationGrid largeAgent = NavigationGrid.fromBlockedCells(
            blocked, 16f, 16f, 8.1f);

        assertTrue(smallAgent.getNodeAt(8f, 24f) != null);
        assertNull(largeAgent.getNodeAt(8f, 24f));
    }

    @Test
    void prefersLongerRoadOverShortGrassRoute() {
        boolean[][] blocked = {
            {false, false, false, false, false},
            {false, false, false, false, false},
            {false, false, false, false, false}
        };
        boolean[][] roads = {
            {true, true, true, true, true},
            {true, false, false, false, true},
            {false, false, false, false, false}
        };
        NavigationGrid grid = NavigationGrid.fromWeightedCells(
            blocked, roads, 16f, 16f, 1f, 4f, 0f);
        DefaultGraphPath<NavigationNode> path = new DefaultGraphPath<>();

        boolean found = new IndexedAStarPathFinder<>(grid).searchNodePath(
            grid.getNodeAt(8f, 24f), grid.getNodeAt(72f, 24f),
            new NavigationHeuristic(1f), path);

        assertTrue(found);
        assertTrue(path.iterator().next().row == 1);
        boolean usesRoadRow = false;
        for (NavigationNode node : path) {
            if (node.row == 0) {
                usesRoadRow = true;
                break;
            }
        }
        assertTrue(usesRoadRow);
    }
}
