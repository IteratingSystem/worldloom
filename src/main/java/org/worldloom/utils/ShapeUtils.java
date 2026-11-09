package org.worldloom.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.*;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.Array;

import java.util.ArrayList;
import java.util.List;

/**
 * @Auther WenLong
 * @Date 2025/2/19 11:11
 * @Description 形状工具
 **/
public class ShapeUtils {
    private final static String TAG = ShapeUtils.class.getSimpleName();
    public static Shape getShapeByMapObject (MapObject mapObject){
        return getShapeByMapObject(mapObject,1);
    }
    /** 按世界比例创建独立形状；多边形顶点先复制，不能修改共享Tiled源数据。 */
    public static Shape getShapeByMapObject (MapObject mapObject,float worldScale){
        Shape shape = null;
        if (mapObject instanceof RectangleMapObject shapeObject){
            Rectangle rectangle = shapeObject.getRectangle();
            // 计算矩形的四个顶点
            float x = rectangle.getX();
            float y = rectangle.getY();
            float width = rectangle.getWidth();
            float height = rectangle.getHeight();
            float[] vertices = new float[] {
                x, y,  // 左下角
                x + width, y,  // 右下角
                x + width, y + height,  // 右上角
                x, y + height   // 左上角
            };
//                if (flipX) {
//                    vertices = flipXVertices(vertices,widthDef);
//                }

            for (int i = 0; i < vertices.length; i++) {
                vertices[i] *= worldScale;
            }
            PolygonShape polygonShape = new PolygonShape();
            polygonShape.set(vertices);

            shape = polygonShape;
        }else if (mapObject instanceof CircleMapObject shapeObject){
            Circle circle = shapeObject.getCircle();

            CircleShape circleShape = new CircleShape();
            circleShape.setPosition(new Vector2(worldScale*circle.x,worldScale*circle.y));
            circleShape.setRadius(worldScale*circle.radius);

            shape = circleShape;
        }else if (mapObject instanceof EllipseMapObject shapeObject){
            Ellipse ellipse = shapeObject.getEllipse();

            CircleShape circleShape = new CircleShape();
            circleShape.setPosition(new Vector2(worldScale*(ellipse.x+ellipse.width/2),worldScale*(ellipse.y+ellipse.height/2)));
            circleShape.setRadius(ellipse.width<ellipse.height?worldScale*(ellipse.width/2):worldScale*(ellipse.height/2));

            shape = circleShape;
        }else if (mapObject instanceof PolygonMapObject shapeObject){
            Polygon polygon = shapeObject.getPolygon();

            float[] vertices = polygon.getVertices().clone();
            for (int i = 0; i < vertices.length; i++) {
                vertices[i]*=worldScale;
            }

            PolygonShape polygonShape = new PolygonShape();
            polygonShape.set(vertices);
            shape = polygonShape;
        }else if (mapObject instanceof PolylineMapObject shapeObject){
        }else {
            Gdx.app.error(TAG,"MapObject is not a shape object!");
        }
        return shape;
    }
    /** 为实体构造自己的形状，沿用现有各形状缩放规则且保持源多边形顶点只读。 */
    public static Shape getShapeByMapObject (MapObject mapObject,float worldScale,float scaleW,float scaleH){
        Shape shape = null;
        if (mapObject instanceof RectangleMapObject shapeObject){
            Rectangle rectangle = shapeObject.getRectangle();
            // 计算矩形的四个顶点
            float x = rectangle.getX();
            float y = rectangle.getY();
            float width = rectangle.getWidth();
            float height = rectangle.getHeight();
            float[] vertices = new float[] {
                    x, y,  // 左下角
                    x + width, y,  // 右下角
                    x + width, y + height,  // 右上角
                    x, y + height   // 左上角
            };
//                if (flipX) {
//                    vertices = flipXVertices(vertices,widthDef);
//                }

            for (int i = 0; i < vertices.length; i++) {
                if (i==0 || i%2==0){
                    vertices[i] *= worldScale * scaleH;
                    continue;
                }
                vertices[i] *= worldScale * scaleW;
            }
            PolygonShape polygonShape = new PolygonShape();
            polygonShape.set(vertices);

            shape = polygonShape;
        }else if (mapObject instanceof CircleMapObject shapeObject){
            Circle circle = shapeObject.getCircle();

            CircleShape circleShape = new CircleShape();
            circleShape.setPosition(new Vector2(scaleW*worldScale*circle.x,scaleH*worldScale*circle.y));
            circleShape.setRadius(scaleW+worldScale*circle.radius);

            shape = circleShape;
        }else if (mapObject instanceof EllipseMapObject shapeObject){
            Ellipse ellipse = shapeObject.getEllipse();

            CircleShape circleShape = new CircleShape();
            circleShape.setPosition(new Vector2(scaleW*worldScale*(ellipse.x+ellipse.width/2),scaleH*worldScale*(ellipse.y+ellipse.height/2)));
            circleShape.setRadius(ellipse.width<ellipse.height?scaleW*worldScale*(ellipse.width/2):scaleH*worldScale*(ellipse.height/2));

            shape = circleShape;
        }else if (mapObject instanceof PolygonMapObject shapeObject){
            Polygon polygon = shapeObject.getPolygon();

            float[] vertices = polygon.getVertices().clone();
            for (int i = 0; i < vertices.length; i++) {
                vertices[i]*=worldScale;
            }

            PolygonShape polygonShape = new PolygonShape();
            polygonShape.set(vertices);
            shape = polygonShape;
        }else if (mapObject instanceof PolylineMapObject shapeObject){
        }else {
            Gdx.app.error(TAG,"MapObject is not a shape object!");
        }
        return shape;
    }


    /**
     * 水平翻转物理形状
     * @param body
     * @param width
     */
    public static void flipX(Body body,float width){
        Array<Fixture> fixtureList = body.getFixtureList();
        Array.ArrayIterator<Fixture> iterator = fixtureList.iterator();
        for (Fixture fixture : iterator) {
            Shape shape = fixture.getShape();
            flipX(shape,width);
        }
    }
    public static void flipX(Body body,float width,float worldScale){
        Array<Fixture> fixtureList = body.getFixtureList();
        Array.ArrayIterator<Fixture> iterator = fixtureList.iterator();
        for (Fixture fixture : iterator) {
            Shape shape = fixture.getShape();
            flipX(shape,width,worldScale);
        }
    }
    public static void flipX(Shape shape,float width){
        flipX(shape,width,1);
    }
    public static void flipX(Shape shape,float width,float worldScale){
        if (shape instanceof PolygonShape polygonShape){
            int vertexCount = polygonShape.getVertexCount();
            float[] vertices = new float[vertexCount*2];
            for (int i = 0; i < vertexCount; i++) {
                Vector2 vector2 = new Vector2();
                polygonShape.getVertex(i,vector2);
                vector2.x = width*worldScale - vector2.x;

                vertices[i*2] = vector2.x;
                vertices[i*2+1] = vector2.y;
            }

            polygonShape.set(vertices);
        }else if (shape instanceof CircleShape circleShape){
            Vector2 position = circleShape.getPosition();
            position.x = width*worldScale-position.x;
            circleShape.setPosition(position);
        }
    }
}
