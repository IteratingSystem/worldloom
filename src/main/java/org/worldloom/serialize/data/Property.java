package org.worldloom.serialize.data;

/**
 * @Auther WenLong
 * @Date 2025/7/4 10:28
 * @Description 用于属性的序列化对象
 **/
public class Property {
    public String key;
    public String type;
    public Object value;
    /** 兼容旧引用的全局标签。 */
    public String entityReferenceTag;
    /** 跨地图保存引用时优先使用稳定身份，不依赖运行期编号。 */
    public String entityReferenceKey;
}
