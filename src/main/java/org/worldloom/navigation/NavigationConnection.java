package org.worldloom.navigation;

import com.badlogic.gdx.ai.pfa.DefaultConnection;

/** 带有直行或斜行代价的网格连接。 */
final class NavigationConnection extends DefaultConnection<NavigationNode> {
    private final float cost;

    NavigationConnection(NavigationNode fromNode, NavigationNode toNode, float cost) {
        super(fromNode, toNode);
        this.cost = cost;
    }

    @Override
    public float getCost() {
        return cost;
    }
}
