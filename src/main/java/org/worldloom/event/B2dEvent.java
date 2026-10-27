package org.worldloom.event;

/**
 * @Auther WenLong
 * @Date 2025/6/30 14:56
 * @Description 相机事件
 **/
public class B2dEvent extends TypeEvent{
    /** 目标地图；null仅供旧当前图调用兼容，跨图业务必须显式填写。 */
    public String mapId;
    public static final int DEL_TILE_COLLIDER = 1;
    public static final int CREATE_TILE_COLLIDER = 2;
    public B2dEvent(int type) {
        super(type);
    }
}
