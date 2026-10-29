package org.worldloom.map;

import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.EllipseMapObject;
import com.badlogic.gdx.maps.objects.PolygonMapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.math.Rectangle;

/** 地图区域的不可变几何快照；坐标与Pos一致，使用Y向上的源像素，不受观察图和worldScale影响。 */
public final class MapArea {
    private static final double EPSILON = 0.00001;
    private final String mapId, layerName, name;
    private final int objectId;
    private final float[] vertices;
    private final double centerX, centerY, radiusX, radiusY, cosine, sine;
    private final boolean capsule;
    private final double segmentX, segmentY, capsuleRadius;
    private final Rectangle bounds;

    /** 从已由TmxMapLoader翻转Y轴的对象复制几何，补上对象旋转和父图层偏移，不修改源对象。 */
    MapArea(String mapId, String layerName, MapObject object, float offsetX, float offsetY) {
        this.mapId = mapId;
        this.layerName = layerName;
        name = object.getName() == null ? "" : object.getName();
        objectId = object.getProperties().get("id", -1, Integer.class);
        if (objectId < 1) throw new IllegalArgumentException("区域对象缺少有效编号：" + mapId + "/" + layerName);
        Object rotation = object.getProperties().get("rotation");
        double angle = Math.toRadians(-(rotation instanceof Number number ? number.doubleValue() : 0));
        if (!Double.isFinite(angle)) throw new IllegalArgumentException("区域旋转无效：" + objectId);
        cosine = Math.cos(angle); sine = Math.sin(angle);
        capsule = object instanceof CapsuleMapObject;
        double pivotX, pivotY;
        if (object instanceof EllipseMapObject || capsule) {
            Rectangle frame;
            if (object instanceof EllipseMapObject ellipseObject) {
                var ellipse = ellipseObject.getEllipse();
                frame = new Rectangle(ellipse.x, ellipse.y, ellipse.width, ellipse.height);
            } else frame = ((CapsuleMapObject)object).bounds();
            positiveSize(frame.width, frame.height);
            pivotX = frame.x; pivotY = frame.y + frame.height;
            radiusX = frame.width / 2d; radiusY = frame.height / 2d;
            centerX = pivotX + radiusX * cosine + radiusY * sine + offsetX;
            centerY = pivotY + radiusX * sine - radiusY * cosine + offsetY;
            capsuleRadius = capsule ? Math.min(radiusX, radiusY) : 0;
            double halfX = capsule ? radiusX - capsuleRadius : 0;
            double halfY = capsule ? radiusY - capsuleRadius : 0;
            segmentX = halfX * cosine - halfY * sine;
            segmentY = halfX * sine + halfY * cosine;
            vertices = null;
            double extentX = capsule ? Math.abs(segmentX) + capsuleRadius : Math.hypot(radiusX * cosine, radiusY * sine);
            double extentY = capsule ? Math.abs(segmentY) + capsuleRadius : Math.hypot(radiusX * sine, radiusY * cosine);
            bounds = new Rectangle((float)(centerX - extentX), (float)(centerY - extentY),
                (float)(extentX * 2), (float)(extentY * 2));
        } else {
            centerX = centerY = radiusX = radiusY = 0;
            segmentX = segmentY = capsuleRadius = 0;
            if (object instanceof RectangleMapObject rectangleObject) {
                Rectangle r = rectangleObject.getRectangle();
                positiveSize(r.width, r.height);
                pivotX = r.x; pivotY = r.y + r.height;
                vertices = new float[]{r.x,r.y, r.x+r.width,r.y, r.x+r.width,r.y+r.height, r.x,r.y+r.height};
            } else if (object instanceof PolygonMapObject polygonObject) {
                vertices = polygonObject.getPolygon().getTransformedVertices().clone();
                pivotX = polygonObject.getPolygon().getX(); pivotY = polygonObject.getPolygon().getY();
                if (vertices.length < 6) throw new IllegalArgumentException("区域多边形至少需要三个顶点：" + objectId);
            } else throw new IllegalArgumentException("区域仅支持矩形、多边形、椭圆和胶囊形：" + mapId + "/" + layerName + "/" + objectId);
            float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < vertices.length; i += 2) {
                double x = vertices[i] - pivotX, y = vertices[i+1] - pivotY;
                vertices[i] = (float)(pivotX + x * cosine - y * sine + offsetX);
                vertices[i+1] = (float)(pivotY + x * sine + y * cosine + offsetY);
                minX = Math.min(minX,vertices[i]); maxX = Math.max(maxX,vertices[i]);
                minY = Math.min(minY,vertices[i+1]); maxY = Math.max(maxY,vertices[i+1]);
            }
            bounds = new Rectangle(minX,minY,maxX-minX,maxY-minY);
            double area = 0;
            for (int i=0,j=vertices.length-2;i<vertices.length;j=i,i+=2)
                area += (double)vertices[j]*vertices[i+1] - (double)vertices[i]*vertices[j+1];
            if (!Double.isFinite(area) || Math.abs(area) <= EPSILON)
                throw new IllegalArgumentException("区域多边形面积无效：" + objectId);
        }
        if (!valid(bounds)) throw new IllegalArgumentException("区域坐标无效：" + objectId);
    }

    /** 拒绝退化或非有限的矩形、椭圆及胶囊尺寸。 */
    private static void positiveSize(float width, float height) {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0)
            throw new IllegalArgumentException("区域尺寸必须为正有限数");
    }

    /** 返回区域所属地图，不能以观察图代替。 */
    public String mapId() { return mapId; }
    /** 返回显式登记的对象层名称，不限制AREA前缀。 */
    public String layerName() { return layerName; }
    /** 返回Tiled对象名称标签；允许空名或同名区域，精确身份使用编号。 */
    public String name() { return name; }
    /** 返回地图内稳定的Tiled对象编号。 */
    public int objectId() { return objectId; }
    /** 返回包围矩形副本，调用方不能修改缓存中的区域。 */
    public Rectangle bounds() { return new Rectangle(bounds); }

    /** 判断源像素点是否位于区域内部或边界。 */
    public boolean contains(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) return false;
        if (capsule) return capsuleDistance(x,y) <= capsuleRadius * capsuleRadius + EPSILON;
        if (vertices == null) return ellipseDistance(x,y) <= 1 + EPSILON;
        boolean inside = false;
        for (int i=0,j=vertices.length-2;i<vertices.length;j=i,i+=2) {
            double ax=vertices[j], ay=vertices[j+1], bx=vertices[i], by=vertices[i+1];
            double dx=bx-ax, dy=by-ay, length=dx*dx+dy*dy;
            double t=length==0 ? 0 : Math.max(0,Math.min(1,((x-ax)*dx+(y-ay)*dy)/length));
            if (Math.hypot(x-ax-t*dx,y-ay-t*dy) <= EPSILON) return true;
            if ((ay>y)!=(by>y) && x < (bx-ax)*(y-ay)/(by-ay)+ax) inside=!inside;
        }
        return inside;
    }

    /** 完整包含矩形；凹多边形还检查边界是否穿过矩形内部，不能只看四角。 */
    public boolean contains(Rectangle r) {
        if (!valid(r) || !contains(r.x,r.y) || !contains(r.x+r.width,r.y)
            || !contains(r.x+r.width,r.y+r.height) || !contains(r.x,r.y+r.height)) return false;
        if (vertices != null) for(int i=0,j=vertices.length-2;i<vertices.length;j=i,i+=2)
            if (segmentCrosses(vertices[j],vertices[j+1],vertices[i],vertices[i+1],r,true)) return false;
        return true;
    }

    /** 检查与矩形的真实相交，边界接触也算相交；包围盒只用作粗筛。 */
    public boolean overlaps(Rectangle r) {
        if (!valid(r) || bounds.x>r.x+r.width || bounds.x+bounds.width<r.x
            || bounds.y>r.y+r.height || bounds.y+bounds.height<r.y) return false;
        if (contains(r.x,r.y) || contains(r.x+r.width,r.y)
            || contains(r.x+r.width,r.y+r.height) || contains(r.x,r.y+r.height)) return true;
        if (vertices != null) {
            for(int i=0,j=vertices.length-2;i<vertices.length;j=i,i+=2)
                if (segmentCrosses(vertices[j],vertices[j+1],vertices[i],vertices[i+1],r,false)) return true;
            return false;
        }
        if (capsule) return capsuleOverlaps(r);
        if (r.contains((float)centerX,(float)centerY)) return true;
        double[] corners = {r.x,r.y,r.x+r.width,r.y,r.x+r.width,r.y+r.height,r.x,r.y+r.height};
        for(int i=0;i<8;i+=2) {
            double x=corners[i]-centerX,y=corners[i+1]-centerY;
            corners[i]=(x*cosine+y*sine)/radiusX;
            corners[i+1]=(-x*sine+y*cosine)/radiusY;
        }
        for(int i=0,j=6;i<8;j=i,i+=2) {
            double dx=corners[i]-corners[j],dy=corners[i+1]-corners[j+1],length=dx*dx+dy*dy;
            double t=length==0 ? 0 : Math.max(0,Math.min(1,-(corners[j]*dx+corners[j+1]*dy)/length));
            double x=corners[j]+t*dx,y=corners[j+1]+t*dy;
            if (x*x+y*y<=1+EPSILON) return true;
        }
        return false;
    }

    /** 逆旋转并按半轴归一化，计算点到单位圆心的距离平方。 */
    private double ellipseDistance(double x,double y) {
        double dx=x-centerX,dy=y-centerY;
        double u=(dx*cosine+dy*sine)/radiusX,v=(-dx*sine+dy*cosine)/radiusY;
        return u*u+v*v;
    }

    /** 点到旋转胶囊中心线的最短距离平方；横竖胶囊和退化为圆的正方形共用此路径。 */
    private double capsuleDistance(double x, double y) {
        double dx = x - centerX, dy = y - centerY;
        double length = segmentX * segmentX + segmentY * segmentY;
        double t = length == 0 ? 0 : Math.max(-1, Math.min(1, (dx * segmentX + dy * segmentY) / length));
        dx -= t * segmentX; dy -= t * segmentY;
        return dx * dx + dy * dy;
    }

    /** 中心线到矩形的距离不超过半径时相交，使用精确端点/角点距离而非胶囊包围盒。 */
    private boolean capsuleOverlaps(Rectangle r) {
        double ax = centerX - segmentX, ay = centerY - segmentY;
        double bx = centerX + segmentX, by = centerY + segmentY;
        if (segmentCrosses(ax, ay, bx, by, r, false)) return true;
        double limit = capsuleRadius * capsuleRadius + EPSILON;
        if (pointRectangleDistance(ax, ay, r) <= limit || pointRectangleDistance(bx, by, r) <= limit) return true;
        return capsuleDistance(r.x, r.y) <= limit || capsuleDistance(r.x + r.width, r.y) <= limit
            || capsuleDistance(r.x + r.width, r.y + r.height) <= limit || capsuleDistance(r.x, r.y + r.height) <= limit;
    }

    /** 点到轴对齐矩形的最短距离平方，用于胶囊圆弧与矩形边缘的精确相交。 */
    private static double pointRectangleDistance(double x, double y, Rectangle r) {
        double dx = Math.max(r.x - x, Math.max(0, x - r.x - r.width));
        double dy = Math.max(r.y - y, Math.max(0, y - r.y - r.height));
        return dx * dx + dy * dy;
    }

    /** 使用参数裁剪判断线段进入矩形；内部模式排除沿矩形边界的接触。 */
    private static boolean segmentCrosses(double ax,double ay,double bx,double by,Rectangle r,boolean interior) {
        double low=0,high=1;
        double[] origins={ax,ay},deltas={bx-ax,by-ay},mins={r.x,r.y},maxs={r.x+r.width,r.y+r.height};
        for(int i=0;i<2;i++) {
            if (deltas[i]==0) { if(origins[i]<mins[i] || origins[i]>maxs[i]) return false; }
            else {
                double t1=(mins[i]-origins[i])/deltas[i],t2=(maxs[i]-origins[i])/deltas[i];
                low=Math.max(low,Math.min(t1,t2));high=Math.min(high,Math.max(t1,t2));
                if(low>high) return false;
            }
        }
        if(!interior) return true;
        double middle=(low+high)/2,x=ax+(bx-ax)*middle,y=ay+(by-ay)*middle;
        return x>r.x+EPSILON && x<r.x+r.width-EPSILON && y>r.y+EPSILON && y<r.y+r.height-EPSILON;
    }

    /** 公共几何入口拒绝空、非有限和退化矩形。 */
    private static boolean valid(Rectangle r) {
        return r!=null && Float.isFinite(r.x) && Float.isFinite(r.y) && Float.isFinite(r.width)
            && Float.isFinite(r.height) && r.width>0 && r.height>0
            && Float.isFinite(r.x+r.width) && Float.isFinite(r.y+r.height);
    }
}
