package org.worldloom.ui.inventory;

import com.artemis.World;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.TextureMapObject;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import org.worldloom.manager.map.MapManager;
import org.worldloom.serialize.data.EntityDatum;

public class SlotDatum {
    private final static String TAG = SlotDatum.class.getSimpleName();

    public int itemId;
    public String itemName;
    public Enum<?> itemType;

    public int stackAmount;
    public int maxStack;
    public int unitPrice;
    public boolean selected;

    // 存入物品的实体信息,用于快速创建实体
    public EntityDatum entityDatum;
    private transient TextureRegionDrawable drawable;
    private transient String drawableMap;
    private transient int drawableObjectId;

    public void copy(SlotDatum fromSlotDatum){
        this.itemId = fromSlotDatum.itemId;
        this.itemName = fromSlotDatum.itemName;
        this.stackAmount = fromSlotDatum.stackAmount;
        this.maxStack = fromSlotDatum.maxStack;
        this.entityDatum = fromSlotDatum.entityDatum;
        this.unitPrice = fromSlotDatum.unitPrice;
        this.itemType = fromSlotDatum.itemType;
    }

    public void exchange(SlotDatum slotDatum){
        SlotDatum newSlotDatum = new SlotDatum();
        newSlotDatum.copy(this);

        this.copy(slotDatum);
        slotDatum.copy(newSlotDatum);
    }

    public boolean isBlank(){
        return stackAmount == 0 || entityDatum == null;
    }

    // 用于对比是否是同一个对象
    public boolean itemEquals(SlotDatum slotDatum){
        if (slotDatum.itemId == itemId && itemId != -1) {
            return true;
        }
        return false;
    }

    /** 按源地图与对象编号缓存图标包装；复制或换物后按实际来源重新绑定。 */
    public TextureRegionDrawable getDrawable() {
        if (entityDatum == null){
            drawable = null;
            return null;
        }

        if (drawable != null && drawableObjectId == entityDatum.mapObjectId
            && java.util.Objects.equals(drawableMap, entityDatum.fromMap)) return drawable;

        MapManager mapManager = MapManager.getInstance();
        String fromMap = entityDatum.fromMap;
        MapObject mapObject = mapManager.getMapObject(fromMap,entityDatum.mapObjectId);
        TextureRegion textureRegion = null;
        if (mapObject instanceof TextureMapObject textureMapObject) {
            textureRegion = textureMapObject.getTextureRegion();
        }else {
            Gdx.app.error(TAG,"Failed to getDrawable(),mapObject is not instanceof TextureMapObject,not has textureRegion!!");
            return null;
        }
        drawable = new TextureRegionDrawable(textureRegion);
        drawableMap = fromMap;
        drawableObjectId = entityDatum.mapObjectId;
        return drawable;
    }
}
