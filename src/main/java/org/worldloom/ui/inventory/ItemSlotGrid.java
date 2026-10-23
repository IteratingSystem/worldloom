package org.worldloom.ui.inventory;

import com.artemis.Entity;
import com.artemis.World;
import com.artemis.utils.Bag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import org.worldloom.ui.BaseEcsUI;

/**
 * 库存UI
 */
public class ItemSlotGrid extends BaseEcsUI {

    //拖拽功能
    private DragAndDrop dragAndDrop;
    private final ObjectMap<ItemSlot, DragAndDrop.Source> dragSources = new ObjectMap<>();
    private final ObjectMap<ItemSlot, DragAndDrop.Target> dragTargets = new ObjectMap<>();

    /**
     * 阻止从这些类型的视图中拖拽物品并放入当前网格。
     * 例如：blockedSourceTypes.add(ShopGridView.class) 将阻止所有商店网格拖入。
     */
    private Bag<Class<? extends ItemSlotGrid>> blockedSourceTypes;
    //是否能拖到地上
    private boolean canDragStop;
    //归属者(实体id)
    public int ownerId;
    public Entity owner;
    //数据
    //用于存储拥有来源的格子,防止被rebuild清空
    public ObjectMap<SlotDatum,Integer> datumFrom;
    private Array<Array<SlotDatum>> slotData;
    //格子尺寸
    public int slotSize;
    //ui
    public Table slotTable;
    public ItemSlot.ItemSlotStyle slotStyle;
    public ItemSlot[][] slots;


    public ItemSlotGrid(World world, DragAndDrop dragAndDrop, String slotStyleName) {
        super(world);
        this.dragAndDrop = dragAndDrop;

        slotStyle = skin.get(slotStyleName, ItemSlot.ItemSlotStyle.class);
        blockedSourceTypes = new Bag<>();
        datumFrom = new ObjectMap<>();
        slotSize = 32;
        canDragStop = true;
        initUI();
    }
    public void initUI(){
        slotTable = new Table();
        add(slotTable);
    }

    //是否能拖到地上
    public void setCanDragStop(boolean canDragStop) {
        this.canDragStop = canDragStop;
    }

    //归属者
    public void setOwner(int ownerId) {
        this.ownerId = ownerId;
        this.owner = world.getEntity(ownerId);
    }
    public Entity getOwner() {
        return owner;
    }
    public int getOwnerId() {
        return ownerId;
    }

    //拖拽来源黑名单
    public void addBlockedSourceType(Class<? extends ItemSlotGrid> blockedSourceType) {
        blockedSourceTypes.add(blockedSourceType);
    }
    public void delBlockedSourceType(Class<? extends ItemSlotGrid> blockedSourceType){
        blockedSourceTypes.remove(blockedSourceType);
    }
    //数据
    public void setSlotData(Array<Array<SlotDatum>> slotData){
        this.slotData = slotData;
    }
    public Array<Array<SlotDatum>> getSlotData() {
        return slotData;
    }


    /** 重建格子前注销本视图的旧拖拽绑定，不影响共用同一拖拽管理器的其他视图。 */
    public void rebuild() {
        if (slotData == null) {
            Gdx.app.error(getTag(),"Failed to rebuild,'slotData' is null!Please run function with 'setSlotData()'");
            return;
        }
        clearSlots();
        int rows = slotData.size;
        slots = new ItemSlot[rows][];

        for (int r = 0; r < rows; r++) {
            int cols = slotData.get(r).size;
            slots[r] = new ItemSlot[cols];
            for (int c = 0; c < cols; c++) {
                ItemSlot itemSlot = new ItemSlot(world,slotStyle);
                slotTable.add(itemSlot).size(slotSize);
                itemSlot.setInvPos(r,c);
                itemSlot.setInvUI(this);
                if (owner != null){
                    itemSlot.setOwner(ownerId);
                }
                slots[r][c] = itemSlot;
                enableDrag(itemSlot);

                SlotDatum slotDatum = slotData.get(r).get(c);
                itemSlot.setSlotDatum(slotDatum);
            }
            slotTable.row();
        }
    }

    /** 自定义布局重建时先调用此方法；只清理当前网格的Actor和拖拽注册。 */
    protected void clearSlots() {
        for (DragAndDrop.Source source : dragSources.values()) dragAndDrop.removeSource(source);
        for (DragAndDrop.Target target : dragTargets.values()) dragAndDrop.removeTarget(target);
        dragSources.clear();
        dragTargets.clear();
        slots = null;
        slotTable.clear();
    }

    public ItemSlot getSlot(int x,int y){
        if (slots == null) {
            return null;
        }
        return slots[x][y];
    }

    public void setSelectOnly(ItemSlot targetSlot){
        if (targetSlot == null) {
            Gdx.app.log(getTag(), "Failed to 'setSelectOnly','targetSlot' is null!");
            return;
        }
        setSelectOnly(targetSlot.getInvX(),targetSlot.getInvY());
    }
    public void setSelectOnly(int x,int y){
        for (ItemSlot[] slot : slots) {
            for (ItemSlot itemSlot : slot) {
                itemSlot.setSelected(false);
                if (itemSlot.equalsPos(x,y)) {
                    itemSlot.setSelected(true);
                }
            }
        }
    }

    /* ===== 拖拽能力 ===== */
    /** 为格子注册一次拖拽，注册对象由当前网格持有并在重建时注销。 */
    public void enableDrag(ItemSlot slot) {
        if (dragSources.containsKey(slot)) return;
        DragAndDrop.Source source = new DragAndDrop.Source(slot) {
            //按住开始拖拽
            /** 向当前网格请求拖拽载荷。 */
            @Override
            public DragAndDrop.Payload dragStart(InputEvent event, float x, float y, int pointer) {
                return onDragStart(slot);   // 交给子类
            }
            //拖到空地
            /** 根据当前网格策略处理拖拽结束。 */
            @Override
            public void dragStop(InputEvent event, float x, float y, int pointer, DragAndDrop.Payload payload, DragAndDrop.Target target) {
                if (!canDragStop) {
                    return;
                }
                onDragStop(event, x, y, pointer, payload, target);
            }
        };

        DragAndDrop.Target target = new DragAndDrop.Target(slot) {

            //拖拽后触碰到
            /** 禁用格子不接受拖放。 */
            @Override
            public boolean drag(DragAndDrop.Source source,
                                DragAndDrop.Payload payload,
                                float x, float y, int pointer) {
                return !slot.isDisabled();
            }
            //拖拽后松开按钮
            /** 校验来源后交由网格处理实际转移。 */
            @Override
            public void drop(DragAndDrop.Source source,
                             DragAndDrop.Payload payload,
                             float x, float y, int pointer) {
                //黑名单中的库存无法触发拖放
                ItemSlot fromSlot = (ItemSlot)source.getActor();
                if (blockedSourceTypes.contains(fromSlot.getInvUI().getClass())) {
                    Gdx.app.debug(getTag(),"This drag originates from the blacklist and is prohibited!");
                    return;
                }
                onDrop(source,payload,getActor());
            }

        };
        dragSources.put(slot, source);
        dragTargets.put(slot, target);
        dragAndDrop.addSource(source);
        dragAndDrop.addTarget(target);
    }

    /* ===== 子类唯一要关心的两个钩子 ===== */
    //按住开始拖拽
    public DragAndDrop.Payload onDragStart(ItemSlot slot) {
        DragAndDrop.Payload payload = new DragAndDrop.Payload();
        Image dragActor = new Image(slot.getIcon().getDrawable());
        dragActor.setScale(4f);
        payload.setDragActor(dragActor);
        return payload;
    }
    //拖到空地丢弃
    public void onDragStop(InputEvent event, float x, float y, int pointer, DragAndDrop.Payload payload, DragAndDrop.Target target){
    }
    //拖拽后松开按钮
    public void onDrop(DragAndDrop.Source source,
                       DragAndDrop.Payload payload,
                       Actor targetActor) {
        if (slots == null) {
            Gdx.app.debug(getTag(),"Failed to swap item,'slots' is null!");
            return;
        }

        if (source == null || !(source.getActor() instanceof ItemSlot fromSlot)
            || !(targetActor instanceof ItemSlot targetSlot)) {
            return;
        }
        //不允许自己拖拽到自己
        if (fromSlot == targetSlot){
            return;
        }


        SlotDatum fromDatum = fromSlot.getSlotDatum();
        SlotDatum targetDatum = targetSlot.getSlotDatum();
        if (!canTransfer(fromSlot, targetSlot, false)) {
            return;
        }
        //同一种物品需要合并
        if (!isEmpty(targetDatum) && fromDatum.itemId == targetDatum.itemId) {
            merge(fromSlot,targetSlot);
            return;
        }

        //不同物品需要交换
        exchangeData(fromSlot,targetSlot);
    }

    /** 合并数据 **/
    public void merge(ItemSlot fromSlot,ItemSlot targetSlot){
        if (!canTransfer(fromSlot, targetSlot, false)) return;
        SlotDatum fromDatum = fromSlot.getSlotDatum();
        SlotDatum targetDatum = targetSlot.getSlotDatum();
        if (isEmpty(targetDatum) || fromDatum.itemId != targetDatum.itemId
            || targetDatum.stackAmount >= targetDatum.maxStack) {
            exchangeData(fromSlot,targetSlot);
            return;
        }

        int canSetAmount = targetDatum.maxStack - targetDatum.stackAmount;
        if (fromDatum.stackAmount <= canSetAmount){
            targetDatum.stackAmount += fromDatum.stackAmount;
            fromDatum = new SlotDatum();
        }else {
            targetDatum.stackAmount += canSetAmount;
            fromDatum.stackAmount -= canSetAmount;
        }

        ItemSlotGrid fromInvUI = fromSlot.getInvUI();
        ItemSlotGrid targetInvUI = targetSlot.getInvUI();
        fromInvUI.slotData.get(fromSlot.getInvX()).set(fromSlot.getInvY(), fromDatum);
        targetInvUI.slotData.get(targetSlot.getInvX()).set(targetSlot.getInvY(), targetDatum);
        fromInvUI.rebuild();
        targetInvUI.rebuild();

    }
    /** 交换数据 **/
    public void exchangeData(ItemSlot fromSlot,ItemSlot targetSlot){
        if (!canTransfer(fromSlot, targetSlot, true)) return;
        SlotDatum fromDatum = fromSlot.getSlotDatum();
        SlotDatum targetDatum = targetSlot.getSlotDatum();
        SlotDatum swapDatum = fromDatum;

        ItemSlotGrid fromInvUI = fromSlot.getInvUI();
        ItemSlotGrid targetInvUI = targetSlot.getInvUI();
        fromInvUI.slotData.get(fromSlot.getInvX()).set(fromSlot.getInvY(), targetDatum);
        targetInvUI.slotData.get(targetSlot.getInvX()).set(targetSlot.getInvY(), swapDatum);

        //记录数据来源
        if (targetInvUI == this && fromInvUI != this){
            datumFrom.put(swapDatum,fromInvUI.ownerId);
        }

        fromInvUI.rebuild();
        targetInvUI.rebuild();
    }

    // 交换物品时必须校验双方是否接受，若仅校验目标槽，
    // 将装备拖到材料堆上就可能把材料反向放入装备槽。
    private static boolean canTransfer(ItemSlot from, ItemSlot to, boolean exchange) {
        if (from == null || to == null || from == to || from.isDisabled()
            || to.isDisabled() || from.getInvUI() == null || to.getInvUI() == null) return false;
        SlotDatum source = from.getSlotDatum();
        return !isEmpty(source) && accepts(to, source)
            && (!exchange || accepts(from, to.getSlotDatum()));
    }

    private static boolean accepts(ItemSlot slot, SlotDatum datum) {
        if (isEmpty(datum)) return true;
        Bag<Enum<?>> types = slot.getAcceptTypes();
        return types == null || types.isEmpty() || types.contains(datum.itemType);
    }

    private static boolean isEmpty(SlotDatum datum) {
        return datum == null || datum.stackAmount <= 0;
    }
}
