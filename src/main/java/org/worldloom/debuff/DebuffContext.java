package org.worldloom.debuff;

import com.artemis.World;

/** Worldloom调用游戏Debuff处理器时提供的上下文。 */
public final class DebuffContext {
    private final World world;
    private final int entityId;
    private final DebuffState state;

    public DebuffContext(World world, int entityId, DebuffState state) {
        this.world = world;
        this.entityId = entityId;
        this.state = state;
    }

    public World getWorld() {
        return world;
    }

    public int getEntityId() {
        return entityId;
    }

    public float getRemaining() {
        return state.remaining;
    }

    public String getData() {
        return state.data;
    }

    public void setData(String data) {
        state.data = data == null ? "" : data;
    }
}
