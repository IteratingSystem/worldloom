package org.worldloom.ui.inventory;

import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import org.worldloom.serialize.data.EntityDatum;

public class SlotDatum {

    public int itemId;
    public String itemName;
    public Enum<?> itemType;

    public int stackAmount;
    public int maxStack;
    public int unitPrice;
    public boolean selected;

    // 存入物品的实体信息,用于快速创建实体
    public EntityDatum entityDatum;

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

    /**
     * 兼容旧调用；图标解析已移至显示层，新UI应使用ItemSlot或ItemSlotGrid的解析入口。
     * 本数据对象不缓存或拥有纹理和Drawable。
     * @deprecated 请由库存显示层解析图标。
     */
    @Deprecated
    public TextureRegionDrawable getDrawable() { return SlotIcons.resolve(this); }
}
