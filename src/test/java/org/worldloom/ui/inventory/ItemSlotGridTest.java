package org.worldloom.ui.inventory;

import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.artemis.utils.Bag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 直接验证真实物品转移方法，不创建 Scene2D 界面或 GL 后端。 */
class ItemSlotGridTest {
    private enum Type { SWORD, WOOD, ARROW }

    @Test void equipmentToOccupiedBackpack_rejectsIncompatibleReturnItem() {
        Transfer t = transfer(Type.SWORD, Type.WOOD, Type.SWORD, null);
        t.drop();
        assertSame(t.fromDatum, t.fromGrid.getSlotData().first().first());
        assertSame(t.toDatum, t.toGrid.getSlotData().first().first());
    }

    @Test void backpackToEquipment_rejectsWrongType() {
        Transfer t = transfer(Type.WOOD, null, null, Type.SWORD);
        t.drop();
        assertSame(t.fromDatum, t.fromGrid.getSlotData().first().first());
        assertNull(t.toGrid.getSlotData().first().first());
    }

    @Test void equipmentToEmptyBackpack_movesItem() {
        Transfer t = transfer(Type.SWORD, null, Type.SWORD, null);
        t.drop();
        assertSame(t.fromDatum, t.toGrid.getSlotData().first().first());
        assertNull(t.fromGrid.getSlotData().first().first());
    }

    @Test void compatibleOccupiedSlots_swapBothItems() {
        Transfer t = transfer(Type.SWORD, Type.SWORD, Type.SWORD, Type.SWORD);
        t.toDatum.itemId = 99;
        t.drop();
        assertSame(t.fromDatum, t.toGrid.getSlotData().first().first());
        assertSame(t.toDatum, t.fromGrid.getSlotData().first().first());
    }

    @Test void sameItemStacks_mergeWithCapacityAndPreserveRemainder() {
        Transfer t = transfer(Type.ARROW, Type.ARROW, Type.ARROW, null);
        t.fromDatum.stackAmount = 8;
        t.toDatum.stackAmount = 7;
        t.drop();
        assertEquals(10, t.toDatum.stackAmount);
        assertEquals(5, t.fromDatum.stackAmount);
    }

    @Test void emptySource_doesNotReplaceTarget() {
        Transfer t = transfer(null, Type.SWORD, null, Type.SWORD);
        assertDoesNotThrow(t::drop);
        assertSame(t.toDatum, t.toGrid.getSlotData().first().first());
    }

    @Test void directExchange_cannotBypassTypeConstraint() {
        Transfer t = transfer(Type.SWORD, Type.WOOD, Type.SWORD, null);
        t.toGrid.exchangeData(t.from, t.to);
        assertSame(t.fromDatum, t.fromGrid.getSlotData().first().first());
    }

    @Test void disabledTarget_rejectsDirectDrop() {
        Transfer t = transfer(Type.SWORD, null, null, Type.SWORD);
        when(t.to.isDisabled()).thenReturn(true);
        t.drop();
        assertNull(t.toGrid.getSlotData().first().first());
    }

    @Test void directMergeWithDifferentItems_cannotBypassReverseConstraint() {
        Transfer t = transfer(Type.SWORD, Type.WOOD, Type.SWORD, null);
        t.toGrid.merge(t.from, t.to);
        assertSame(t.fromDatum, t.fromGrid.getSlotData().first().first());
    }

    @Test void fullMatchingStack_exchangeStillChecksReverseConstraint() {
        Transfer t = transfer(Type.SWORD, Type.WOOD, Type.SWORD, null);
        t.toDatum.itemId = t.fromDatum.itemId;
        t.toDatum.stackAmount = t.toDatum.maxStack;
        t.drop();
        assertSame(t.fromDatum, t.fromGrid.getSlotData().first().first());
    }

    @Test void zeroAmountSource_isIgnored() {
        Transfer t = transfer(Type.SWORD, null, null, Type.SWORD);
        t.fromDatum.stackAmount = 0;
        t.drop();
        assertNull(t.toGrid.getSlotData().first().first());
    }

    @Test void entireStackMerge_clearsSourceAndKeepsTotal() {
        Transfer t = transfer(Type.ARROW, Type.ARROW, Type.ARROW, null);
        t.fromDatum.stackAmount = 3;
        t.toDatum.stackAmount = 4;
        t.drop();
        assertEquals(7, t.toGrid.getSlotData().first().first().stackAmount);
        assertEquals(0, t.fromGrid.getSlotData().first().first().stackAmount);
    }

    @Test void emptyAcceptTypes_preservesUnrestrictedSlotBehavior() {
        Transfer t = transfer(Type.SWORD, null, null, null);
        when(t.to.getAcceptTypes()).thenReturn(new Bag<>());
        t.drop();
        assertSame(t.fromDatum, t.toGrid.getSlotData().first().first());
    }

    private Transfer transfer(Type source, Type target, Type sourceAccept, Type targetAccept) {
        SlotDatum fromDatum = datum(source), toDatum = datum(target);
        ItemSlotGrid fromGrid = grid(fromDatum), toGrid = grid(toDatum);
        ItemSlot from = slot(fromGrid, fromDatum, sourceAccept);
        ItemSlot to = slot(toGrid, toDatum, targetAccept);
        return new Transfer(fromGrid, toGrid, from, to, fromDatum, toDatum);
    }

    private SlotDatum datum(Type type) {
        if (type == null) return null;
        SlotDatum d = new SlotDatum();
        d.itemType = type; d.itemId = type.ordinal() + 1;
        d.stackAmount = 1; d.maxStack = 10;
        return d;
    }

    private ItemSlotGrid grid(SlotDatum datum) {
        ItemSlotGrid grid = mock(ItemSlotGrid.class, CALLS_REAL_METHODS);
        Array<SlotDatum> row = new Array<>(); row.add(datum);
        Array<Array<SlotDatum>> data = new Array<>(); data.add(row);
        grid.setSlotData(data);
        grid.slots = new ItemSlot[1][1];
        grid.datumFrom = new ObjectMap<>();
        doNothing().when(grid).rebuild();
        return grid;
    }

    private ItemSlot slot(ItemSlotGrid grid, SlotDatum datum, Type accepted) {
        ItemSlot slot = mock(ItemSlot.class);
        when(slot.getInvUI()).thenReturn(grid);
        when(slot.getSlotDatum()).thenReturn(datum);
        if (accepted != null) {
            Bag<Enum<?>> accepts = new Bag<>(); accepts.add(accepted);
            when(slot.getAcceptTypes()).thenReturn(accepts);
        }
        return slot;
    }

    private record Transfer(ItemSlotGrid fromGrid, ItemSlotGrid toGrid,
                            ItemSlot from, ItemSlot to, SlotDatum fromDatum, SlotDatum toDatum) {
        void drop() {
            DragAndDrop.Source source = mock(DragAndDrop.Source.class);
            when(source.getActor()).thenReturn(from);
            toGrid.onDrop(source, new DragAndDrop.Payload(), to);
        }
    }
}
