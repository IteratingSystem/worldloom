package org.worldloom.ai;

import com.artemis.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.ai.btree.annotation.TaskAttribute;
import org.worldloom.component.Pos;
import org.worldloom.navigation.NavigationStatus;
import org.worldloom.system.NavigationSystem;

/** 让实体沿当前地图的导航网格移动到坐标或带Tag的实体。 */
public class MoveTo extends EcsLeafTask {
    /** 目标的Tiled像素横坐标，targetTag为空时使用。 */
    @TaskAttribute
    public float x;
    /** 目标的Tiled像素纵坐标，targetTag为空时使用。 */
    @TaskAttribute
    public float y;
    /** 目标实体Tag；非空时优先使用目标实体的Pos。 */
    @TaskAttribute
    public String targetTag = "";
    /** 与目标相距多少Tiled像素时视为到达。 */
    @TaskAttribute
    public float stopDistance;

    private NavigationSystem navigationSystem;
    private int requestId = -1;

    @Override
    public void start() {
        super.start();
        navigationSystem = world.getSystem(NavigationSystem.class);
        float targetX = x;
        float targetY = y;
        if (targetTag != null && !targetTag.isBlank()) {
            if (!tagManager.isRegistered(targetTag)) {
                Gdx.app.error(getTAG(), "Target tag is not registered: " + targetTag);
                requestId = -1;
                return;
            }
            Entity target = tagManager.getEntity(targetTag);
            Pos targetPos = target.getComponent(Pos.class);
            if (targetPos == null) {
                Gdx.app.error(getTAG(), "Target entity has no Pos component: " + targetTag);
                requestId = -1;
                return;
            }
            targetX = targetPos.x;
            targetY = targetPos.y;
        }
        requestId = navigationSystem.navigate(entityId, targetX, targetY,
            Math.max(0f, stopDistance));
    }

    @Override
    public Status execute() {
        if (requestId < 0 || navigationSystem == null) {
            return Status.FAILED;
        }
        NavigationStatus status = navigationSystem.getStatus(entityId, requestId);
        return switch (status) {
            case MOVING -> Status.RUNNING;
            case SUCCEEDED -> Status.SUCCEEDED;
            case IDLE, FAILED -> Status.FAILED;
        };
    }

    @Override
    public void end() {
        if (getStatus() == Status.CANCELLED && navigationSystem != null
            && requestId >= 0) {
            navigationSystem.cancel(entityId);
        }
    }

    @Override
    protected Task<com.artemis.Entity> copyTo(Task<com.artemis.Entity> task) {
        MoveTo copy = (MoveTo)task;
        copy.x = x;
        copy.y = y;
        copy.targetTag = targetTag;
        copy.stopDistance = stopDistance;
        return copy;
    }
}
