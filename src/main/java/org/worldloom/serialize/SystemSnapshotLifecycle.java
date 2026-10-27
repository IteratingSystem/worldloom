package org.worldloom.serialize;

/** 系统只在存档边界构造或恢复DTO，运行中仍以原实体及计时状态为准。 */
public interface SystemSnapshotLifecycle {
    /** 捕获字段前刷新系统快照，不推进模拟时间。 */
    void beforeSnapshot();

    /** 字段还原完成后登记恢复工作；此时实体可能尚未创建。 */
    void afterSnapshotRestore();
}
