package org.worldloom.manager.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.utils.SnapshotArray;

/**
 * 全局输入管理器
 * <ul>
 *     <li>自动初始化 InputMultiplexer 并设置为全局处理器</li>
 *     <li>提供长按/短按监听器的注册与移除</li>
 *     <li>支持动态添加/移除其他 InputProcessor</li>
 * </ul>
 */
public class InputManager {
    private static InputMultiplexer MULTIPLEXER;
    private static LongPressProcessor LONG_PRESS_PROCESSOR;
    public static boolean ENABLE = true;

    // ---------- 初始化 ----------
    /** 取得应用输入链；显式登记处理器时才接管当前输入。 */
    private static InputMultiplexer getMultiplexer() {
        if (MULTIPLEXER == null) {
            MULTIPLEXER = new InputMultiplexer();
        }
        Gdx.input.setInputProcessor(MULTIPLEXER);
        return MULTIPLEXER;

    }
    /** 延迟创建长按处理器，不在无监听器时占据输入链。 */
    private static LongPressProcessor getLongPressProcessor() {
        if (LONG_PRESS_PROCESSOR == null) {
            LONG_PRESS_PROCESSOR = new LongPressProcessor();
        }
        return LONG_PRESS_PROCESSOR;
    }



    // ---------- 长按监听 API（钩子） ----------
    /** 登记长短按监听，始终让UI先决定是否消费按键。 */
    public static void addLongPressListener(LongPressListener listener) {
        getLongPressProcessor().addListener(listener);
        getMultiplexer();
        moveLongPressLast();
    }
    /** 最后一个监听退出时取消计时并解绑，不覆盖菜单自行设置的输入舞台。 */
    public static void removeLongPressListener(LongPressListener listener) {
        if (LONG_PRESS_PROCESSOR == null) return;
        LONG_PRESS_PROCESSOR.removeListener(listener);
        if (!LONG_PRESS_PROCESSOR.hasListeners() && MULTIPLEXER != null)
            MULTIPLEXER.removeProcessor(LONG_PRESS_PROCESSOR);
    }

    /** 新UI加入后重排唯一长按处理器，避免第二次进入世界时它挡在舞台前面。 */
    private static void moveLongPressLast() {
        if (MULTIPLEXER == null || LONG_PRESS_PROCESSOR == null) return;
        MULTIPLEXER.removeProcessor(LONG_PRESS_PROCESSOR);
        if (LONG_PRESS_PROCESSOR.hasListeners()) MULTIPLEXER.addProcessor(LONG_PRESS_PROCESSOR);
    }

    // ---------- 通用处理器管理 ----------
    /** 登记普通处理器，保留其相对顺序并让长按处于链尾。 */
    public static void addProcessor(InputProcessor processor) {
        getMultiplexer().addProcessor(processor);
        moveLongPressLast();
    }

    /** 按指定优先级登记普通处理器，长按监听仍在所有普通处理器之后。 */
    public static void addProcessor(int index, InputProcessor processor) {
        getMultiplexer().addProcessor(index, processor);
        moveLongPressLast();
    }

    /** 解绑已有处理器，清理时不夺回其它页面的输入控制权。 */
    public static void removeProcessor(InputProcessor processor) {
        if (MULTIPLEXER != null) MULTIPLEXER.removeProcessor(processor);
    }

    /** 按序号解绑已有处理器，不改变当前页面的输入入口。 */
    public static void removeProcessor(int index) {
        if (MULTIPLEXER != null) MULTIPLEXER.removeProcessor(index);
    }

    public static SnapshotArray<InputProcessor> getProcessors() {
        return getMultiplexer().getProcessors();
    }

    /** 清空输入链和全部待触发按键，后续可重新登记监听器。 */
    public static void clear() {
        if (LONG_PRESS_PROCESSOR != null) LONG_PRESS_PROCESSOR.clear();
        getMultiplexer().clear();
    }


    public static boolean isKeyPressed(int key){
        if (!ENABLE){
            return false;
        }
        return Gdx.input.isKeyPressed(key);
    }
    public static boolean isKeyPressed(int[] keys){
        if (!ENABLE){
            return false;
        }
        for (int key : keys) {
            if (!Gdx.input.isKeyPressed(key)) {
                return false;
            }
        }
        return true;
    }
    public static boolean isKeyJustPressed(int key){
        if (!ENABLE){
            return false;
        }
        return Gdx.input.isKeyJustPressed(key);
    }
    public static boolean isKeyJustPressed(int[] keys){
        if (!ENABLE){
            return false;
        }
        for (int key : keys) {
            if (!Gdx.input.isKeyJustPressed(key)) {
                return false;
            }
        }
        return true;
    }
}
