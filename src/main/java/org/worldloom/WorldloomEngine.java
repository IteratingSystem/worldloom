package org.worldloom;

import com.artemis.BaseSystem;
import com.artemis.World;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Event;
import org.worldloom.event.CameraEvent;
import org.worldloom.event.EntityEvent;
import org.worldloom.manager.map.GameSnapshotManager;

/** 持有一个 Artemis World 的 Worldloom 运行实例。 */
public final class WorldloomEngine implements AutoCloseable {
    private final World world;
    private final EventSystem events;
    private boolean disposed;

    /** 接管已初始化世界；实体加载失败也由本入口释放，宿主无需持有失败实例。 */
    WorldloomEngine(World world) {
        this.world = world;
        try {
            events = world.getSystem(EventSystem.class);
            events.dispatch(new EntityEvent(EntityEvent.BUILD_ALL));
        } catch (RuntimeException | Error failure) {
            try {
                world.dispose();
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    public void update(float delta) {
        requireActive();
        world.setDelta(delta);
        world.process();
    }

    public void resize(int width, int height) {
        requireActive();
        CameraEvent event = new CameraEvent(CameraEvent.RESIZE);
        event.width = width;
        event.height = height;
        events.dispatch(event);
    }

    public void dispatch(Event event) {
        requireActive();
        if (event == null) {
            throw new IllegalArgumentException("event cannot be null");
        }
        events.dispatch(event);
    }

    public <T extends BaseSystem> T getSystem(Class<T> systemType) {
        requireActive();
        return world.getSystem(systemType);
    }

    /** 兼容需要把 World 传给 ECS UI 的现有游戏代码。 */
    public World getWorld() {
        requireActive();
        return world;
    }

    public String createSaveJson() {
        requireActive();
        return GameSnapshotManager.getInstance().createSaveJson(world);
    }

    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public void close() {
        dispose();
    }

    /** 进入关闭状态后才释放系统；即使清理失败也禁止更新及重复释放原生资源。 */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        world.dispose();
    }

    private void requireActive() {
        if (disposed) {
            throw new IllegalStateException("WorldloomEngine is disposed");
        }
    }
}
