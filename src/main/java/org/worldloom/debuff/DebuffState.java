package org.worldloom.debuff;

/** 一个实体身上某个Debuff在当前会话中的计时状态。 */
public final class DebuffState {
    public String name = "";
    /** 剩余秒数，小于0表示永久。 */
    public float remaining = -1f;
    /** 距离上次周期触发已经经过的秒数。 */
    public float tickElapsed;
    /** 具体Debuff可自行使用的临时恢复数据。 */
    public String data = "";
    public transient boolean activated;

    public boolean isPermanent() {
        return remaining < 0f;
    }
}
