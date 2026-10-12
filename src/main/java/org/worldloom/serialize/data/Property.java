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
    /** Stable fallback for @EntityReference fields whose target is carried across maps. */
    public String entityReferenceTag;
}
