package org.worldloom.component;

import com.artemis.Component;
import org.worldloom.serialize.EntityReference;
import org.worldloom.serialize.SerializeParam;

/** 必须随宿主跨图的表现或工具实体；不同于房屋、船只等资产的Owner关系。 */
public final class MapAttachment extends Component {
    @SerializeParam
    @EntityReference
    public int ownerId = -1;
}
