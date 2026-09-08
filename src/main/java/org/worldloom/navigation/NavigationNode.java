package org.worldloom.navigation;

/** 导航网格中的一个单元格中心。 */
public final class NavigationNode {
    public final int index;
    public final int column;
    public final int row;
    public final float x;
    public final float y;

    NavigationNode(int index, int column, int row, float x, float y) {
        this.index = index;
        this.column = column;
        this.row = row;
        this.x = x;
        this.y = y;
    }
}
