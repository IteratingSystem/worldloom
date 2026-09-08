package org.worldloom.component;

import com.badlogic.gdx.utils.Array;
import org.worldloom.component.parent.SerializeComponent;
import org.worldloom.serialize.SerializeParam;

/** 保存一个实体当前拥有的全部Debuff名称。 */
public class Debuffs extends SerializeComponent {
    /** 当前生效的Debuff名称列表。 */
    @SerializeParam
    public Array<String> effects = new Array<>();

    public boolean has(String name) {
        if (effects == null) {
            effects = new Array<>();
        }
        return effects.contains(name, false);
    }

}
