package org.worldloom.component;

import com.artemis.Component;
import org.worldloom.serialize.SerializeParam;

/** 保存及网络使用的稳定身份，与运行期实体编号分离。 */
public final class PersistentIdentity extends Component {
    @SerializeParam
    public String entityKey = "";
}
