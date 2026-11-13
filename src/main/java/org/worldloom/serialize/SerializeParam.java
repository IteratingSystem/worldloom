package org.worldloom.serialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标记实体快照字段；可编辑参数默认同时从Tiled读取，纯存档状态可显式关闭地图输入。 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface SerializeParam {
    /** 是否允许由Tiled类属性配置；false仍参与运行实体快照与存档，新实体使用Java字段初值。 */
    boolean tiled() default true;
}
