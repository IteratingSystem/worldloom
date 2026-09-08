package org.worldloom.component;

import com.badlogic.gdx.utils.Array;
import org.worldloom.component.parent.SerializeComponent;
import org.worldloom.debuff.DebuffState;
import org.worldloom.serialize.SerializeParam;

/** 保存一个实体当前拥有的全部Debuff及其剩余运行状态。 */
public class Debuffs extends SerializeComponent {
    /** 当前生效的Debuff列表，由存档系统直接序列化。 */
    @SerializeParam
    public Array<DebuffState> effects = new Array<>();

    public DebuffState find(String name) {
        if (effects == null) {
            effects = new Array<>();
        }
        for (DebuffState effect : effects) {
            if (effect.name.equals(name)) {
                return effect;
            }
        }
        return null;
    }

}
