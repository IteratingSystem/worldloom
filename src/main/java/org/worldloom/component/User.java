package org.worldloom.component;

import org.worldloom.component.parent.SerializeComponent;
import org.worldloom.serialize.SerializeParam;
import org.worldloom.serialize.EntityReference;

/**
 * 保存当前使用者的实体ID。
 */
public class User extends SerializeComponent {
    @SerializeParam
    @EntityReference
    public int id = -1;
}
