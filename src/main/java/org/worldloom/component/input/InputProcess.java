package org.worldloom.component.input;


import com.artemis.World;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Disposable;
import org.worldloom.component.parent.SerializeComponent;
import org.worldloom.manager.ReflectionManager;
import org.worldloom.manager.input.InputManager;
import org.worldloom.serialize.SerializeParam;
import org.worldloom.serialize.data.EntityDatum;
import java.util.Set;

/**
 * 操控组件
 *
 * <p>在状态机或者其它地方处理输入控制实体,可以使用此组件来控制相应的代码是否生效</p>
 *
 * @author WenLong
 * @version 1.0.0
 * @date 2026/6/23 11:52
 * @see InputProcess
 */

public class InputProcess extends SerializeComponent implements Disposable {
    // 是否开启操控
    @SerializeParam
    public boolean enabled;
    @SerializeParam
    public String simpleName;

    public InputProcessing processing;

    /** 重载前解绑旧输入监听；构造失败时不保留上一份处理器。 */
    @Override
    public void reload(World world, EntityDatum entityDatum) {
        dispose();
        super.reload(world, entityDatum);

        ReflectionManager reflectionManager = ReflectionManager.getInstance();
        Set<Class<? extends InputProcessing>> subTypes = reflectionManager.getSubTypesOfWithGame(InputProcessing.class);
        Class<? extends InputProcessing> targetClass = subTypes.stream()
                .filter(c -> simpleName.equals(c.getSimpleName()))
                .findFirst()
                .orElse(null);

        if (targetClass == null) {
            Gdx.app.error(getTag(), "No suitable InputProcess found for " + simpleName);
            return;
        }
        processing = reflectionManager.createObject(
                targetClass,
                null,
                null
        );

        if (processing == null) {
            Gdx.app.error(getTag(), "Failed to create processing,SimpleName: " + simpleName);
            return;
        }
        Gdx.app.log(getTag(), "Instantiated InputProcessing: " + simpleName);
    }

    @Override
    public void dispose() {
        if (processing != null) {
            processing.dispose();
            processing = null;
        }
    }
}
