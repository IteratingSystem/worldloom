package org.worldloom.event;

/** 通过事件向实体施加、移除或清空Debuff。 */
public final class DebuffEvent extends TypeEvent {
    public static final int APPLY = 0;
    public static final int REMOVE = 1;
    public static final int CLEAR = 2;

    public int entityId = -1;
    public String name = "";

    private DebuffEvent(int type) {
        super(type);
    }

    /** 创建添加Debuff的事件，持续时间和行为由已注册的实现决定。 */
    public static DebuffEvent add(int entityId, String name) {
        DebuffEvent event = new DebuffEvent(APPLY);
        event.entityId = entityId;
        event.name = name;
        return event;
    }

    /** 创建移除指定Debuff的事件。 */
    public static DebuffEvent remove(int entityId, String name) {
        DebuffEvent event = new DebuffEvent(REMOVE);
        event.entityId = entityId;
        event.name = name;
        return event;
    }

    /** 创建清空实体全部Debuff的事件。 */
    public static DebuffEvent clear(int entityId) {
        DebuffEvent event = new DebuffEvent(CLEAR);
        event.entityId = entityId;
        return event;
    }

}
