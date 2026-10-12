package org.worldloom.reflectionfixture;

import com.artemis.Component;
import org.worldloom.serialize.SerializeParam;

/** 合法的游戏组件，与残留旧类具有相同的简单类名。 */
public class BTree extends Component {
    @SerializeParam public String treeName;
}
