package org.worldloom.ui.inventory;

import com.artemis.Entity;
import com.artemis.World;
import com.artemis.utils.Bag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;

/**
 * 库存单元格,默认拥有图片和堆叠数量,需要增加其它的数据或者样式直接继承就可以了
 */
public class ItemSlot extends WidgetGroup {
    //相对于库存的格子位置,在一个库存页面中,左上角为原点0,0
    private int invX;
    private int invY;
    private ItemSlotGrid invUI;

    private World world;
    private SlotDatum slotDatum;
    private Bag<Enum<?>> acceptTypes;
    public ItemSlot(World world, SlotDatum slotDatum) {}

    private ItemSlotStyle style;
    private Drawable backgroundOverride;
    private boolean backgroundOverridden;
    private Image bg;      // 背景
    private Image icon;    // 物品图标
    private Image selectedOverlay;
    private Label amount;  // 右下角数量
    private boolean hovered, pressed, checked, disabled;

    //归属者(实体id)
    private int ownerId;
    private Entity owner;
    private boolean selected;

    public ItemSlot(World world, Skin skin, String styleName) {
        this(world,skin.get(styleName, ItemSlotStyle.class));
    }

    public ItemSlot(World world, ItemSlotStyle style) {
        this.world = world;
        this.style = style;

        bg = new Image();
        icon = new Image();
        selectedOverlay = new Image();

        amount = new Label("", new Label.LabelStyle(style.font, style.fontColor));
        amount.setAlignment(Align.bottomRight);

        selectedOverlay = new Image(style.selectedOverlay);
        selectedOverlay.setVisible(false);

        addActor(bg);
        addActor(icon);
        addActor(amount);
        addActor(selectedOverlay);

        // 统一大小
        setSize(style.up.getMinWidth(), style.up.getMinHeight());
        // 首次绘制
        refreshDrawables();
        // 按钮事件（悬停/按下/点击）
        addListener(new ClickListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor fromActor) {
                hovered = true;
                refreshDrawables();
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor toActor) {
                hovered = false;
                refreshDrawables();
            }

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                pressed = true;
                refreshDrawables();
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                pressed = false;

                refreshDrawables();
            }
        });
    }



    /* =============== 外部调用 =============== */
    //归属者
    public void setOwner(int ownerId) {
        this.ownerId = ownerId;
        this.owner = world.getEntity(ownerId);
    }
    public Entity getOwner() {
        return owner;
    }

    public int getInvX() {
        return invX;
    }
    public int getInvY() {
        return invY;
    }

    public Bag<Enum<?>> getAcceptTypes() {
        return acceptTypes;
    }

    public void addAcceptType(Enum<?> acceptType) {
        if (acceptTypes == null) {
            acceptTypes = new Bag<>();
        }
        if (acceptTypes.contains(acceptType)) {
            return;
        }
        acceptTypes.add(acceptType);
    }

    /*在库存中的坐标*/
    public void setInvPos(int x,int y) {
        invX = x;
        invY = y;
    }
    public ItemSlotGrid getInvUI() {
        return invUI;
    }
    public void setInvUI(ItemSlotGrid invUI) {
        this.invUI = invUI;
    }

    /** 仅替换当前格子的常态背景，不修改Skin或其它格子共用的样式。 */
    public void setBackground(Drawable drawable) {
        backgroundOverride = drawable;
        backgroundOverridden = true;
        refreshDrawables();
    }

    public Image getIcon() {
        return icon;
    }

    /** 换物品图标 */
    private void setIcon(Drawable drawable) {
        icon.setDrawable(drawable);
        icon.setVisible(drawable != null);
    }

    /** 查询绑定数据的真实数量；单件物品隐藏数字不影响此结果。 */
    public int getAmount() { return slotDatum == null ? 0 : slotDatum.stackAmount; }
    /** 数量文本 */
    private void setAmount(int count) {
        amount.setText(count <= 1 ? "" : String.valueOf(count));
    }

    public SlotDatum getSlotDatum() {
        return slotDatum;
    }

    public void setSlotDatum(SlotDatum slotDatum) {
        if (slotDatum == null){
            slotDatum = new SlotDatum();
        }
        this.slotDatum = slotDatum;
        setIcon(slotDatum.getDrawable());
        setAmount(slotDatum.stackAmount);
    }

    /** 选中状态 */
    public void setChecked(boolean checked) {
        this.checked = checked;
        refreshDrawables();
    }

    public boolean isChecked() { return checked; }

    /** 禁用状态 */
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
        refreshDrawables();
    }

    public boolean isDisabled() { return disabled; }

    // ---------- 选中状态控制 ----------
    public void setSelected(boolean selected) {
        this.selected = selected;
        slotDatum.selected = selected;
        selectedOverlay.setVisible(selected && style.selectedOverlay != null);
    }

    public boolean isSelected() {
        return selected;
    }

    /** 根据本格子交互状态选择背景，实例覆盖只作用于常态背景。 */
    public void refreshDrawables() {
        // 背景状态机
        Drawable front = backgroundOverridden ? backgroundOverride : style.up;
        if (disabled && style.disabled != null) front = style.disabled;
        else if (pressed && style.down != null) front = style.down;
        else if (hovered && style.over != null) front = style.over;
        else if (checked && style.checked != null) front = style.checked;
        bg.setDrawable(front);
    }


    @Override
    public void layout() {
        // 背景铺满
        bg.setBounds(0, 0, getWidth(), getHeight());
        // 图标居中
        float iconSize = Math.min(getWidth(), getHeight()) * 0.8f;
        icon.setBounds((getWidth() - iconSize) / 2, (getHeight() - iconSize) / 2, iconSize, iconSize);
        // 数量右下角
        amount.setBounds(getWidth() - amount.getPrefWidth() - 6, 2, amount.getPrefWidth(), amount.getPrefHeight());
        selectedOverlay.setBounds(0, 0, getWidth(), getHeight());
    }


    public static class ItemSlotStyle extends TextButton.TextButtonStyle {
        public Drawable selectedOverlay;
    }


    public boolean equalsPos(ItemSlot itemSlot) {
        if (itemSlot == null){
            Gdx.app.log(getTag(), "Failed to 'equalsPos',itemSlot is null!");
            return false;
        }
        return equalsPos(itemSlot.invX,itemSlot.invY);
    }
    public boolean equalsPos(int x,int y) {
        if (x == invX && y == invY) {
            return true;
        }
        return false;
    }
    public String getTag(){
        return getClass().getSimpleName();
    }
}
