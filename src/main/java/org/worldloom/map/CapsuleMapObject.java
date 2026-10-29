package org.worldloom.map;

import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.math.Rectangle;

/** 保留Tiled胶囊形身份，避免旧版LibGDX将其静默解释为矩形。 */
public final class CapsuleMapObject extends MapObject {
    private final Rectangle bounds;

    /** 复用加载器已完成的坐标翻转及公共属性；短边的一半为胶囊圆角半径。 */
    public CapsuleMapObject(RectangleMapObject source) {
        bounds = new Rectangle(source.getRectangle());
        setName(source.getName());
        setOpacity(source.getOpacity());
        setVisible(source.isVisible());
        setColor(source.getColor().cpy());
        getProperties().putAll(source.getProperties());
    }

    /** 返回未应用对象旋转的外框副本，坐标遵循加载器的Y轴约定。 */
    public Rectangle bounds() { return new Rectangle(bounds); }
}
