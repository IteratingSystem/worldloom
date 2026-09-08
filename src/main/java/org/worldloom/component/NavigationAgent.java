package org.worldloom.component;

import com.artemis.Component;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import org.worldloom.navigation.NavigationStatus;

/** 保存一次寻路所需的配置和运行状态，由导航系统按需创建。 */
public class NavigationAgent extends Component {
    /** 移动速度，单位为Tiled像素/秒。 */
    public float speed = 25f;
    /** 身体中心与障碍之间需要保留的距离，单位为Tiled像素。 */
    public float radius = 0f;
    /** 到达路径点的容差，单位为Tiled像素。 */
    public float waypointTolerance = 1f;

    public final Array<Vector2> path = new Array<>();
    public int nextWaypoint;
    public float targetX;
    public float targetY;
    public float stopDistance;
    public String pathMapName;
    public int requestId;
    public NavigationStatus status = NavigationStatus.IDLE;

    public void resetRuntime() {
        path.clear();
        nextWaypoint = 0;
        targetX = 0f;
        targetY = 0f;
        stopDistance = 0f;
        pathMapName = null;
        requestId = 0;
        status = NavigationStatus.IDLE;
    }
}
