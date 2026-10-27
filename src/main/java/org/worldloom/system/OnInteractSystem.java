package org.worldloom.system;


import com.artemis.BaseSystem;
import com.badlogic.gdx.Gdx;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.plugin.extendedcomponentmapper.M;
import org.worldloom.component.inter.Interactive;
import org.worldloom.event.InteractEvent;

/**
 * 处理实体被交互的系统
 *
 * <p></p>
 *
 * @author WenLong
 * @version 1.0.0
 * @date 2026/7/1 11:21
 * @see OnInteractSystem
 */
public class OnInteractSystem extends BaseSystem {
    private final static String TAG = OnInteractSystem.class.getSimpleName();
    private M<Interactive> mInteractive;

    @Override
    protected void processSystem() {

    }



    /** 仅向同地图目标派发交互，避免相同局部坐标导致跨图操作。 */
    @Subscribe
    public void onEvent(InteractEvent interactEvent){
        int toId = interactEvent.toId;
        if (org.worldloom.Worldloom.config().isResidentMode()
            && !world.getSystem(MapIdentitySystem.class).sameMap(interactEvent.fromId, toId)) return;
        if (!mInteractive.has(toId)) {
            Gdx.app.error(TAG,"Failed to onEvent,Target entity is not has component 'Interactive';");
            return;
        }

        Interactive interactive = mInteractive.get(toId);
        interactive.onInteractListener.onEvent(interactEvent);
    }
}
