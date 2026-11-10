package org.worldloom.loader;

/**
 * @Auther WenLong
 * @Date 2025/2/13 11:04
 * @Description 旧版Tiled类型模型，仅为外部源码兼容保留，运行时及引擎内部检查不再依赖。
 * @deprecated 地图类型文件统一通过EcsMapLoader读取，不要另建默认值转换入口。
 **/
@Deprecated(since = "4.19.1")
public class PropertyType {
    public String color;
    public boolean drawFill;
    public int id;
    public Member[] members;

    public String name;
    public String type;
    public String[] useAs;


    public String storageType;
    public String[] values;
    public boolean valuesAsFlags;
}
