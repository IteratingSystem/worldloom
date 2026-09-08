package org.worldloom.debuff;

/**
 * 由游戏项目实现的具体Debuff效果。
 * Worldloom只管理生命周期，不假设生命值、动画或视觉组件的结构。
 */
public interface DebuffHandler {
    /** 默认持续秒数，小于等于0表示永久。 */
    default float getDuration() {
        return 0f;
    }

    /** 周期回调间隔，小于等于0表示不需要周期回调。 */
    default float getTickInterval() {
        return 0f;
    }

    default void onApplied(DebuffContext context) {
    }

    /** 同名Debuff再次添加并刷新时间后调用。 */
    default void onRefreshed(DebuffContext context) {
    }

    default void onUpdate(DebuffContext context, float delta) {
    }

    default void onTick(DebuffContext context) {
    }

    default void onRemoved(DebuffContext context,
                           DebuffRemovalReason reason) {
    }
}
