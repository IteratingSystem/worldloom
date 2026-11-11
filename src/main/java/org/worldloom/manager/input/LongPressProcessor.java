package org.worldloom.manager.input;

import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.SnapshotArray;
import com.badlogic.gdx.utils.Timer;
import java.util.Objects;

/** UI之后处理长短按；监听器退出时取消旧任务，不把上一局按键带入新世界。 */
public class LongPressProcessor extends InputAdapter {
    private final IntMap<Timer.Task> keyTasks = new IntMap<>();
    private static final float LONG_PRESS_SECONDS = .5f;
    private final SnapshotArray<LongPressListener> listeners = new SnapshotArray<>(LongPressListener.class);

    /** 同一实例只登记一次，拒绝空监听。 */
    public void addListener(LongPressListener listener) {
        Objects.requireNonNull(listener, "Long press listener is required");
        if (!listeners.contains(listener, true)) listeners.add(listener);
    }

    /** 最后一个监听退出后清理待触发计时，避免回调新会话。 */
    public void removeListener(LongPressListener listener) {
        listeners.removeValue(listener, true);
        if (!hasListeners()) cancelTasks();
    }

    /** 输入管理器据此决定是否需要在输入链中保留本处理器。 */
    boolean hasListeners() { return listeners.notEmpty(); }

    /** 只有存在监听且全局允许输入时才接受本次按下。 */
    @Override public boolean keyDown(int keycode) {
        cancelTask(keycode);
        if (!InputManager.ENABLE || !hasListeners()) return false;
        Timer.Task task = new Timer.Task() {
            /** 只执行仍属于本次按下的任务，回调前先摘除，允许监听器自行退出。 */
            @Override public void run() {
                if (keyTasks.get(keycode) != this) return;
                keyTasks.remove(keycode);
                notifyListeners(keycode, true);
            }
        };
        keyTasks.put(keycode, task);
        Timer.schedule(task, LONG_PRESS_SECONDS);
        return true;
    }

    /** 只对本处理器接受且尚未触发长按的按键派发短按；未知释放不阻断其它处理器。 */
    @Override public boolean keyUp(int keycode) {
        Timer.Task task = keyTasks.remove(keycode);
        if (task == null) return false;
        task.cancel();
        notifyListeners(keycode, false);
        return true;
    }

    /** 使用快照遍历并复核登记状态，回调删除监听器或清空输入链时不误调已退出对象。 */
    private void notifyListeners(int keycode, boolean longPress) {
        LongPressListener[] snapshot = listeners.begin();
        try {
            int size = listeners.size;
            for (int i = 0; i < size && InputManager.ENABLE; i++) {
                LongPressListener listener = snapshot[i];
                if (!listeners.contains(listener, true)) continue;
                if (longPress) listener.onLongPress(keycode);
                else listener.onShortPress(keycode);
            }
        } finally { listeners.end(); }
    }

    /** 清空待触发按键和监听；之后重新登记可正常使用。 */
    public void clear() { cancelTasks(); listeners.clear(); }

    /** 取消所有计时，不触发任何短按或长按回调。 */
    private void cancelTasks() {
        for (Timer.Task task : keyTasks.values()) task.cancel();
        keyTasks.clear();
    }

    /** 重复按下同一键时只保留最后一次计时。 */
    private void cancelTask(int keycode) {
        Timer.Task task = keyTasks.remove(keycode);
        if (task != null) task.cancel();
    }
}
