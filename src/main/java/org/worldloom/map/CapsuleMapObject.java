package org.worldloom.map;

import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.math.Rectangle;

/** 保留Tiled胶囊形身份，避免旧版LibGDX将其静默解释为矩形。 */
public final class CapsuleMapObject extends MapObject {
    private final Rectangle bounds;
    private final MapProperties properties;

    /** 复用加载器已完成的坐标翻转及公共属性；短边的一半为胶囊圆角半径。 */
    public CapsuleMapObject(RectangleMapObject source) {
        bounds = new Rectangle(source.getRectangle());
        properties = source.getProperties();
        setName(source.getName());
        setOpacity(source.getOpacity());
        setVisible(source.isVisible());
        setColor(source.getColor().cpy());
    }

    /** 返回未应用对象旋转的外框副本，坐标遵循加载器的Y轴约定。 */
    public Rectangle bounds() { return new Rectangle(bounds); }

    /** 保留原属性容器，确保加载结束后解析的对象引用仍写入胶囊本身。 */
    @Override public MapProperties getProperties() { return properties; }
}
