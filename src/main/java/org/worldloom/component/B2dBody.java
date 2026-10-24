package org.worldloom.component;

import com.artemis.Entity;
import com.artemis.utils.Bag;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.objects.TextureMapObject;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.TiledMapTileSet;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import org.worldloom.Worldloom;
import org.worldloom.box2d.*;
import org.worldloom.box2d.listener.EcsContactListener;
import org.worldloom.box2d.setup.FixtureSetup;
import org.worldloom.component.parent.SerializeComponent;
import org.worldloom.manager.ReflectionManager;
import org.worldloom.manager.map.MapManager;
import org.worldloom.serialize.data.CompMirror;
import org.worldloom.system.B2dSystem;
import org.worldloom.serialize.SerializeParam;
import org.worldloom.utils.ShapeUtils;
import org.worldloom.serialize.data.EntityDatum;

import java.util.Set;


/**
 * @Auther WenLong
 * @Date 2025/2/17 15:38
 * @Description Box2D身体
 **/
public class B2dBody extends SerializeComponent implements Disposable {

    private final static String TAG = B2dBody.class.getSimpleName();
    @SerializeParam
    public BodyDef.BodyType defType;//动静态类型
    @SerializeParam
    public boolean defFixed;//是否固定旋转
    @SerializeParam
    public float linearDamping;//线性阻尼
    /** Saved physical heading in radians; old saves default to zero. */
    @SerializeParam
    public float angle;


    public Bag<FixtureSetup> keyframeFixSetups;
    public World b2dWorld;
    public BodyDef bodyDef;
    public Body body;

    //创建阶段是否翻转,用于给需要随时创建和删除的形状标记创建时的初始位置
    //目前使用的地方未KeyframeShapeSystem
    //需要翻转与当前翻转状态
    public transient  boolean needFlipX = false;


    @Override
    public void reload(com.artemis.World world, EntityDatum entityDatum) {
        super.reload(world, entityDatum);
        
        //先清理旧的物理body，防止内存泄漏和冲突
        if (body != null && b2dWorld != null) {
            try {
                b2dWorld.destroyBody(body);
            } catch (Exception e) {
                //忽略销毁时的异常，可能body已经被销毁
            }
            body = null;
        }
        
        //获取传入参数的属性
        if (!(mapObject instanceof TiledMapTileMapObject tileMapObject)) {
            return;
        }
        MapProperties props = mapObject.getProperties();
        float posX = props.get("x", float.class);
        float posY = props.get("y", float.class);
        CompMirror pos = entityDatum.hasComp("Pos") ? entityDatum.getCompMirror("Pos") : null;
        if (pos != null) {
            if (pos.getValue("x") instanceof Number x) posX = x.floatValue();
            if (pos.getValue("y") instanceof Number y) posY = y.floatValue();
        }


        MapObjects allObjects = new MapObjects();
        MapObjects objects = tiledMapTile.getObjects();

        B2dSystem b2dSystem = world.getSystem(B2dSystem.class);
        b2dWorld = b2dSystem.box2DWorld;

        //构造关键数据
        bodyDef = new BodyDef();
        bodyDef.fixedRotation = defFixed;
        bodyDef.type = defType;
        bodyDef.angle = Float.isFinite(angle) ? angle : 0f;
        keyframeFixSetups = new Bag<>();

        float worldScale = Worldloom.config().getWorldScale();
        bodyDef.position.set(worldScale*posX, worldScale*posY);

        body = b2dWorld.createBody(bodyDef);
        body.setLinearDamping(linearDamping);

        //tiled原本的对象
        for (MapObject object : objects) {
            allObjects.add(object);
        }

        //动画帧中的形状对象
        MapManager mapManager = MapManager.getInstance();
        TiledMapTileSet tileSet = mapManager.getTileSet(tiledMapTile);
        String group = TileAnimations.groupOf(tileSet, tiledMapTile);
        for (TiledMapTile aTile : tileSet) {
            if (!(aTile instanceof AnimatedTiledMapTile animatedTile)) {
                continue;
            }
            MapProperties allProps = animatedTile.getProperties();
            if (!allProps.containsKey("TileAnimation")) {
                continue;
            }
            MapProperties aniProp = allProps.get("TileAnimation", MapProperties.class);
            if (!aniProp.containsKey("name")) {
                continue;
            }
            if (!group.equals(TileAnimations.groupOf(allProps))) {
                continue;
            }

            StaticTiledMapTile[] frameTiles = animatedTile.getFrameTiles();
            String aniName = aniProp.get("name", "", String.class);
            for (int i = 0; i < frameTiles.length; i++) {
                StaticTiledMapTile frameTile = frameTiles[i];
                MapObjects frameTileObjects = frameTile.getObjects();
                for (MapObject frameTileObject : frameTileObjects) {
                    MapProperties properties = frameTileObject.getProperties();
                    if (!properties.containsKey("FixDef")) {
                        continue;
                    }
                    MapProperties fixDefProps = properties.get("FixDef", MapProperties.class);
                    fixDefProps.put("keyframeIndex", i);
                    fixDefProps.put("aniName", aniName);
                    allObjects.add(frameTileObject);
                }
            }

        }

        float scaleWidth = 1;
        float scaleHeight = 1;
        TextureMapObject textureMapObject = (TextureMapObject) mapObject;
        int regionWidth = textureMapObject.getTextureRegion().getRegionWidth();
        int regionHeight = textureMapObject.getTextureRegion().getRegionHeight();
        MapProperties properties = mapObject.getProperties();
        float tileWidth = properties.get("width",(float)regionWidth, float.class);
        float tileHeight = properties.get("height",(float)regionHeight, float.class);
        scaleWidth = tileWidth/regionWidth;
        scaleHeight = tileHeight/regionHeight;

        //将所有对象中是形状的转换成shape
        for (MapObject object : allObjects) {
            MapProperties shapeProps = object.getProperties();
            if (!shapeProps.containsKey("FixDef")) {
                continue;
            }
            MapProperties fixDefProps = shapeProps.get("FixDef",MapProperties.class);
            float density = fixDefProps.get("density", float.class);
            float friction = fixDefProps.get("friction", float.class);
            float restitution = fixDefProps.get("restitution", float.class);
            boolean isSensor = fixDefProps.get("isSensor", boolean.class);
            int sensorType = fixDefProps.get("sensorType", Integer.class);
            String categoryBit = fixDefProps.get("categoryBit", String.class);
            String maskBits = fixDefProps.get("maskBits", String.class);
            String listenerSimpleName = fixDefProps.get("listenerSimpleName", String.class);
            // 帧动画数据
            String aniName = fixDefProps.get("aniName","", String.class);
            int keyframeIndex = fixDefProps.get("keyframeIndex",0, Integer.class);

            // 创建监听器
            EcsContactListener ecsContactListener = null;

            // 如果包名与类名都不为空则执行创建逻辑
            ReflectionManager reflectionManager = ReflectionManager.getInstance();
            Set<Class<? extends EcsContactListener>> subTypesOfWithGame = reflectionManager.getSubTypesOfWithGame(EcsContactListener.class);

            Class<? extends EcsContactListener> aClass = subTypesOfWithGame.stream()
                    .filter(c -> c.getSimpleName().equals(listenerSimpleName))
                    .findFirst()
                    .orElse(null);
            if (aClass != null) {
                ecsContactListener = reflectionManager.createObject(
                        aClass,
                        new Class[]{Entity.class},
                        new Entity[]{world.getEntity(entityId)});
            }

            Shape shape = ShapeUtils.getShapeByMapObject(object, worldScale,scaleWidth,scaleHeight);

            if (shape == null){
                continue;
            }

            Filter filter = new Filter();
            filter.categoryBits = CategoryBits.valueOf(categoryBit).getBit();
            filter.maskBits = CategoryBits.getMask(maskBits);


            FixtureDef fixtureDef = new FixtureDef();
            fixtureDef.density = density;
            fixtureDef.friction = friction;
            fixtureDef.restitution = restitution;
            fixtureDef.isSensor = isSensor;
            fixtureDef.filter.set(filter);
            fixtureDef.shape = shape;

            //不是动画帧中的形状时,直接创建出来
            if ("".equals(aniName)) {
                Fixture fixture = body.createFixture(fixtureDef);

                DefFixData defFixData = new DefFixData();
                defFixData.entityId = entityId;
                defFixData.entity = world.getEntity(entityId);
                defFixData.sensorType = sensorType;
                defFixData.listener = ecsContactListener;

                fixture.setUserData(defFixData);

                continue;
            }
            //否则先保存起来,后期触发的时候再创建
            KeyframeShapeData keyframeShapeData = new KeyframeShapeData();
            keyframeShapeData.entityId = entityId;
            keyframeShapeData.entity = world.getEntity(entityId);
            keyframeShapeData.sensorType = sensorType;
            keyframeShapeData.listener = ecsContactListener;
            keyframeShapeData.aniName = aniName;
            keyframeShapeData.keyframeIndex = keyframeIndex;
            FixtureSetup fixtureSetup = new FixtureSetup(fixtureDef,keyframeShapeData);
            keyframeFixSetups.add(fixtureSetup);
        }
    }

    /**
     * 传入用户对象获取keyframeFixSetups中对应的那个FixSetup
     * @return FixtureSetup
     */
    public FixtureSetup getKeyframeFixSetup(KeyframeShapeData keyframeShapeData){
        for (FixtureSetup keyframeFixSetup : keyframeFixSetups) {
            if (keyframeFixSetup.fixtureData != keyframeShapeData) {
                continue;
            }
            return keyframeFixSetup;
        }
        return null;
    }
    /**
     * 传入用户对象获取keyframeFixSetups中对应的那个FixSetup中的FixtureDef
     * @param keyframeShapeData
     * @return
     */
    public FixtureDef getKeyframeFixDef(KeyframeShapeData keyframeShapeData){
        FixtureSetup keyframeFixSetup = getKeyframeFixSetup(keyframeShapeData);
        if(keyframeFixSetup == null){
            return null;
        }
        return keyframeFixSetup.FixtureDef;
    }

    /**
     * 左右翻转,假设角色左右翻转,所以他的Body也要左右翻转;
     * 这里没有用强制性的翻转方向,而是将Body内部的所有矩形和圆形移动到翻转后的位置来模拟的翻转;
     * 翻转逻辑会忽略动画帧形状中的翻转情况,因为动画帧形状的翻转状态会在创建时判断,通过needFlipX属性来判断是否需要翻转;
     */
    public void flipX(float regionWidth){
        Array.ArrayIterator<Fixture> iterator = new Array.ArrayIterator<>(body.getFixtureList());
        while (iterator.hasNext()) {
            Fixture fixture = iterator.next();
            Object userData = fixture.getUserData();
            //不需要处理动画帧中的形状,因为其在创建阶段翻转
            if (userData instanceof KeyframeShapeData keyframeShapeData) {
                continue;
            }
            ShapeUtils.flipX(fixture.getShape(),regionWidth);
        }
    }

    /**
     * 通过类型获取夹具
     * @return fixtures
     */
    public Bag<Fixture> getFixtures(int sensorType){
        Bag<Fixture> fixtures = new Bag<>();
        Array.ArrayIterator<Fixture> iterator = new Array.ArrayIterator<>(body.getFixtureList());
        while (iterator.hasNext()) {
            Fixture fixture = iterator.next();
            Object userData = fixture.getUserData();
            if (userData instanceof DefFixData defFixData) {
                if (defFixData.sensorType == sensorType) {
                    fixtures.add(fixture);
                }
            }
        }
        return fixtures;
    }
    /**
     * 通过位码获取夹具
     * @return fixtures
     */
    public Bag<Fixture> getFixtures(CategoryBits categoryBits){
        Bag<Fixture> fixtures = new Bag<>();
        Array.ArrayIterator<Fixture> iterator = new Array.ArrayIterator<>(body.getFixtureList());
        while (iterator.hasNext()) {
            Fixture fixture = iterator.next();
            if (fixture.getFilterData().categoryBits == categoryBits.getBit()) {
                fixtures.add(fixture);
            }
        }
        return fixtures;
    }

    /**
     * 获取传入夹具碰撞到的所有其它夹具,掩码过滤掉的不会发生碰撞也就不会呗获取
     * @return fixtures
     */
    public Bag<Fixture> getContactFixtures(Fixture fixture){
        Bag<Fixture> fixtures = new Bag<>();
        Array.ArrayIterator<Contact> iterator = new Array.ArrayIterator<>(b2dWorld.getContactList());
        while (iterator.hasNext()) {
            Contact contact = iterator.next();
            if (!contact.isTouching()) {
                continue;
            }
            Fixture fixtureA = contact.getFixtureA();
            Fixture fixtureB = contact.getFixtureB();

            if (fixture != fixtureA && fixture != fixtureB){
                continue;
            }
            Fixture contactFix = fixtureA;
            if (fixture == fixtureA){
                contactFix = fixtureB;
            }
            fixtures.add(contactFix);
        }
        return fixtures;
    }

    /**
     * 获取所有碰撞的夹具
     * @return contactFixtures
     */
    public Bag<Fixture> getContactFixtures(int sensorType){
        Bag<Fixture> contactFixtures = new Bag<>();
        Bag<Fixture> fixtures = getFixtures(sensorType);
        for (Fixture fixture : fixtures) {
            contactFixtures.addAll(getContactFixtures(fixture));
        }
        return contactFixtures;
    }
    /**
     * 获取所有与之碰撞的夹具
     * @return contactFixtures
     */
    public Bag<Fixture> getContactFixtures(CategoryBits categoryBits){
        Bag<Fixture> contactFixtures = new Bag<>();
        Bag<Fixture> fixtures = getFixtures(categoryBits);
        for (Fixture fixture : fixtures) {
            contactFixtures.addAll(getContactFixtures(fixture));
        }
        return contactFixtures;
    }
    /**
     * 获取夹具的主人(实体)
     * @param fixture
     * @return
     */
    public Entity getEntity(Fixture fixture) {
        Object userData = fixture.getUserData();
        if (userData instanceof DefFixData defFixData) {
            return defFixData.entity;
        }
        return null;
    }

    @Override
    public void dispose() {
        if (body != null && b2dWorld != null) {
            try {
                b2dWorld.destroyBody(body);
            } catch (Exception e) {
                //忽略销毁时的异常，可能body已经被销毁
            }
            body = null;
        }
    }

    public void setPos(Pos pos) {
        float worldScale = Worldloom.config().getWorldScale();
        body.setTransform(worldScale*pos.x, worldScale*pos.y, body.getAngle());
    }

    @Override
    public void beforeSerialization() {
        if (body != null) angle = body.getAngle();
    }
}
