package org.worldloom;

/** 游戏系统可以进入的稳定执行阶段。 */
public enum EnginePhase {
    INITIALIZE,
    PRE_PHYSICS,
    INPUT,
    PRE_UPDATE,
    UPDATE,
    POST_UPDATE,
    PRE_RENDER,
    WORLD_EFFECT,
    POST_AMBIENT,
    POST_RENDER,
    UI
}
