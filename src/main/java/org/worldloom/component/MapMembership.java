package org.worldloom.component;

import com.artemis.Component;
import org.worldloom.serialize.SerializeParam;

/** 实体当前地图及地图内名称；不改变素材的原始地图来源。 */
public final class MapMembership extends Component {
    @SerializeParam
    public String mapId = "";
    @SerializeParam
    public String localTag = "";
    /** 每次真实跨图递增，网络拒绝旧地图位姿。 */
    @SerializeParam
    public int mapEpoch;
}
