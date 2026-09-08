package org.worldloom.debuff;

/** Debuff结束的原因，供具体游戏效果执行不同的清理逻辑。 */
public enum DebuffRemovalReason {
    EXPIRED,
    REMOVED,
    REPLACED,
    CLEARED
}
