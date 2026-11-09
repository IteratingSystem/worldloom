package org.worldloom.system;

import com.artemis.BaseSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.worldloom.event.UIEvent;
import org.worldloom.event.CameraEvent;
import org.worldloom.manager.input.InputManager;

/**
 * @Auther WenLong
 * @Date 2024/11/26 9:55
 * @Description ui系统
 **/
public class RenderUISystem extends BaseSystem {
    private final static String TAG = RenderUISystem.class.getSimpleName();
    private Stage mainStage;
    private Stack stack;
    private ExtendViewport extendViewport;
    private ObjectMap<Class<? extends Table>,Table> uiMap;
    /** 指定UI舞台的逻辑尺寸，实际窗口由resize事件更新。 */
    public RenderUISystem(int width,int height){
        extendViewport = new ExtendViewport(width,height);
    }

    /** 创建并登记本系统独占的舞台与输入处理器。 */
    @Override
    protected void initialize() {
        uiMap = new ObjectMap<>();

        stack = new Stack();
        stack.setFillParent(true);


        mainStage = new Stage(extendViewport);
        mainStage.addActor(stack);

        InputManager.addProcessor(mainStage);
    }

    /** 回调可能关闭世界；act结束后只绘制仍由本系统持有的同一舞台。 */
    @Override
    protected void processSystem() {
        Stage stage = mainStage;
        if (stage == null) return;
        stage.act();
        if (mainStage == stage) stage.draw();
    }

    /** 跟随引擎窗口尺寸更新UI投影，与世界相机共用resize通知。 */
    @Subscribe public void onResize(CameraEvent event) {
        if (event.type == CameraEvent.RESIZE && mainStage != null)
            extendViewport.update(event.width, event.height, true);
    }

    /** 世界销毁时先注销输入，再释放舞台自有批次；不释放共享皮肤。 */
    @Override protected void dispose() {
        if (mainStage == null) return;
        Stage stage = mainStage;
        mainStage = null;
        stack = null;
        uiMap.clear();
        InputManager.removeProcessor(stage);
        stage.dispose();
    }

    /** 同类只注册一个页面；拒绝空页面，避免事件缺参使舞台进入部分状态。 */
    private void register(Table ui){
        if (ui == null) {
            Gdx.app.log(TAG, "Cannot register a null UI table");
            return;
        }
        if (uiMap.containsKey(ui.getClass())) {
            Gdx.app.log(TAG,"Failed to addUI,uiMap is containsKey: "+ ui.getClass().getSimpleName());
            return;
        }
        uiMap.put(ui.getClass(),ui);
        stack.addActor(ui);
    }
    /** 查找未注册或缺少类型的页面时返回空值，各可见性操作统一保持现状。 */
    private Table getTable(Class<? extends Table> zClass){
        if (zClass == null || !uiMap.containsKey(zClass)) {
            Gdx.app.log(TAG,"UI table is not registered: " + (zClass == null ? "null" : zClass.getSimpleName()));
            return null;
        }
        return uiMap.get(zClass);
    }

    /** 隐藏已注册页面；非法目标不影响其它页面。 */
    private void hide(Class<? extends Table> zClass){
        Table table = getTable(zClass);
        if (table != null) table.setVisible(false);
    }
    /** 用页面快照迭代，允许可见性回调登记页面或关闭整个世界。 */
    private void hideAll(){
        Stage stage = mainStage;
        for (Table table : uiMap.values().toArray()) {
            if (mainStage != stage) return;
            table.setVisible(false);
        }
    }
    /** 显示已注册页面；未注册时与隐藏操作采用相同处理。 */
    private void show(Class<? extends Table> zClass){
        Table table = getTable(zClass);
        if (table != null) table.setVisible(true);
    }
    /** 先确认目标存在，再隐藏其它页面；隐藏回调关闭世界后不再操作旧页面。 */
    private void onlyShow(Class<? extends Table> zClass){
        Table table = getTable(zClass);
        if (table == null) return;
        Stage stage = mainStage;
        hideAll();
        if (mainStage == stage) table.setVisible(true);
    }

    /** 关闭后忽略迟到事件，GET_TABLE明确返回空值，不访问已释放的舞台。 */
    @Subscribe
    public void onEvent(UIEvent event){
        if (mainStage == null) {
            if (event.type == UIEvent.GET_TABLE) event.table = null;
            return;
        }
        if (event.type == UIEvent.SHOW) {
            show(event.uiClass);
            return;
        }
        if (event.type == UIEvent.ONLY_SHOW) {
            onlyShow(event.uiClass);
            return;
        }
        if (event.type == UIEvent.HIDE) {
            hide(event.uiClass);
            return;
        }
        if (event.type == UIEvent.HIDE_ALL) {
            hideAll();
            return;
        }
        if (event.type == UIEvent.REGISTER) {
            register(event.table);
            return;
        }
        if (event.type == UIEvent.GET_TABLE){
            event.table = getTable(event.uiClass);
            return;
        }
    }
}
