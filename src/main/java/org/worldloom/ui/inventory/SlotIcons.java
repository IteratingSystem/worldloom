package org.worldloom.ui.inventory;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.maps.objects.TextureMapObject;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import org.worldloom.manager.map.MapManager;

/** 默认库存显示适配器；共享模板纹理由MapManager持有，格子不负责释放。 */
final class SlotIcons {
    /** 只解析正式来源对象；没有图标或空物品统一返回null，不留下上一件物品的图片。 */
    static TextureRegionDrawable resolve(SlotDatum datum) {
        if (datum == null || datum.isBlank()) return null;
        var source = MapManager.getInstance().getMapObject(datum.entityDatum.fromMap, datum.entityDatum.mapObjectId);
        if (source instanceof TextureMapObject texture && texture.getTextureRegion() != null)
            return new TextureRegionDrawable(texture.getTextureRegion());
        if (Gdx.app != null) Gdx.app.error("SlotIcons", "Item source has no texture region");
        return null;
    }
}
