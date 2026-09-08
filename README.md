# Worldloom

Worldloom 是基于 LibGDX、Artemis-ODB 和 Tiled 的嵌入式 2D ECS 游戏引擎。它负责建立并驱动 Artemis `World`，把 Tiled 对象转换为实体，并提供地图切换、存档、渲染、Box2D、输入、交互、状态机、行为树、Scene2D UI、光影和音频等通用能力。

Worldloom 不接管 LibGDX 的 `ApplicationListener` 或 `Screen`。游戏项目仍然拥有平台启动、页面和业务内容；引擎通过 `WorldloomEngine` 统一管理 ECS World 的创建、系统顺序、每帧更新、窗口变化和释放。

当前版本：`4.2.4`。版本号的选择与发布步骤见 [VERSIONING.md](VERSIONING.md)。

## 1. 环境与依赖

- JDK 21
- Gradle 8.14.5（项目当前使用版本）
- LibGDX 1.14.2
- Artemis-ODB 2.3.0
- Tiled 1.11.x 至 1.12.x
- 使用 gdx-liftoff 风格的资源模块，并生成 `assets.txt`

通过 JitPack 引入：

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    api "com.github.IteratingSystem:worldloom:4.2.4"
}
```

Worldloom 还集成了 box2dlights、gdx-ai、blade-ink、Artemis contrib event bus、extended component mapper 和 profiler 等依赖。

## 2. 总体架构

```text
[Tiled .tmx / propertytypes.json]
                |
                v
[AssetManager] -> [MapManager] -> [GameSnapshotManager] -> [EntityFactory]
                                                                    |
[WorldloomEngine] --------------------------------------------------+-> [Artemis World]
                                                                          |
                                                                          +-> [Box2D / Light]
                                                                          +-> [地图 / 实体 / UI 渲染]
                                                                          +-> [输入 / 交互 / AI / 状态机]
                                                                          +-> [多地图存档]
```

主要职责如下：

| 模块 | 职责 |
| --- | --- |
| `AssetManager` | 根据 `assets.txt` 批量加载地图、纹理、行为树、Ink、噪声纹理和音频 |
| `MapManager` | 解析全部地图的实体层与物理层，保存不可变的初始实体数据 |
| `GameSnapshotManager` | 管理一次游戏会话的当前地图、各地图实体快照和系统属性 |
| `EntityBuilder` / `EntitySerializer` | 在 Tiled 数据、存档数据和运行时实体之间转换 |
| `WorldloomEngine` | 创建并持有 Artemis `World`，统一驱动生命周期 |
| `WorldloomGameModule` | 把游戏业务系统注册到稳定的执行阶段 |
| `EventSystem` | 在游戏代码与引擎系统之间传递实体、地图、相机、UI 等命令 |

## 3. 最小接入流程

初始化顺序是引擎契约的一部分：先配置 Worldloom，再加载资源并初始化地图，随后选择新游戏或存档会话，最后创建 `WorldloomEngine`。

### 3.1 创建应用配置

在创建任何 `UIStage` 或加载引擎资源前完成配置：

```java
WorldloomConfig config = WorldloomConfig.builder()
    .ui(640, 360, 1f)
    .game(640, 360)
    .cameraZoom(1f)
    .pixelPerfectCamera(PixelPerfectCameraConfig.enabled())
    .worldScale(1f / 16f)
    .gravity(0f, -9.8f)
    .allowPhysicsSleep(false)
    .combineTileShapes(true)
    .legacyBox2dLights(false)
    .initialMap("island")
    .skin("skin/main.json")
    .entityLayer("island", "entities")
    .physicsLayers("island", "ground", "wall")
    .build();

Worldloom.configure(config);
```

`WorldloomConfig` 构建后不可修改。相机缩放等运行时状态应通过 `CameraEvent` 修改，不要把运行状态写回配置。

### 3.2 注册反射根包

Worldloom 通过类的简单名称寻找游戏项目中的状态、输入处理器、交互监听器、碰撞监听器和 Shader uniform 类：

```java
Worldloom.setGameRootClass(MainGame.class);
```

传入游戏项目根包内的类。未调用时，引擎只能扫描自己的包，游戏侧扩展类将无法实例化。

### 3.3 加载资源

这里使用的是 `org.worldloom.manager.AssetManager`，不是直接使用 LibGDX 同名类：

```java
AssetManager assets = AssetManager.getInstance();
assets.setLoaders("propertytypes.json");
assets.loadAssets();

// 在加载页面每帧调用
assets.update();
float progress = assets.getProgress();
```

当 `progress >= 1f` 后初始化地图数据：

```java
Worldloom.initializeMaps();
```

地图初始化在一个应用进程中只执行一次。不要在每次进入游戏页面时重复调用。

### 3.4 建立新游戏或读取存档

新游戏：

```java
Worldloom.startNewGame();
```

读取已有 JSON 存档：

```java
Worldloom.loadGame(saveJson);
```

二者必须在 `engineBuilder().build()` 之前完成，因为地图系统和存档恢复系统在创建 Artemis World 时就需要当前会话。

### 3.5 注册游戏系统并驱动引擎

```java
public final class GameModule implements WorldloomGameModule {
    @Override
    public void registerSystems(WorldloomSystemRegistry systems) {
        systems.add(EnginePhase.INPUT, new KeyboardSystem());
        systems.add(EnginePhase.UPDATE, new WeatherSystem());
        systems.add(EnginePhase.UPDATE, new CropGrowthSystem());
    }
}

WorldloomEngine engine = Worldloom.engineBuilder()
    .addModule(new GameModule())
    .build();
```

每帧：

```java
engine.update(Gdx.graphics.getDeltaTime());
```

窗口尺寸变化时通知相机：

```java
engine.resize(width, height);
```

页面退出时调用 `engine.dispose()`；应用退出时再释放资源：

```java
AssetManager.getInstance().dispose();
```

## 4. 配置与游戏系统阶段

| Builder 方法 | 默认值 | 作用 |
| --- | --- | --- |
| `ui(640, 480, 1)` | `640 × 480` | Scene2D UI 的逻辑尺寸与缩放 |
| `game(640, 480)` | `640 × 480` | 游戏相机逻辑尺寸 |
| `cameraZoom(1)` | `1` | 初始相机缩放 |
| `pixelPerfectCamera(config)` | 关闭 | 平滑像素摄像机与扩边世界缓冲 |
| `worldScale(1)` | `1` | 像素到物理世界单位的转换比例 |
| `gravity(0, -9.8)` | 地球重力 | Box2D 重力 |
| `allowPhysicsSleep(false)` | `false` | Box2D 是否允许休眠 |
| `combineTileShapes(true)` | `true` | 是否合并地图瓦片碰撞形状 |
| `legacyBox2dLights(false)` | `false` | 是否启用原 box2dlights 环境光管线 |
| `initialMap("defaultMap")` | `defaultMap` | 新游戏初始地图 |
| `skin("skin/main.json")` | `skin/main.json` | Scene2D Skin 路径 |
| `entityLayer(map, layer)` | 空 | 每张地图的实体对象层 |
| `physicsLayers(map, layers...)` | 空 | 每张地图的静态物理层 |
| `autoComponents(...)` | `Pos/Render/ZIndex` | 地图对象默认创建的组件 |

游戏模块只能注册到以下稳定阶段，引擎内部系统顺序不向游戏项目开放：

| 阶段 | 用途 |
| --- | --- |
| `INITIALIZE` | 业务管理器和只提供服务的系统 |
| `INPUT` | 游戏输入 |
| `PRE_UPDATE` | 引擎地图事务之后的前置逻辑 |
| `UPDATE` | 时间、天气、角色和一般玩法 |
| `POST_UPDATE` | 坐标、动画、ZIndex 更新后的逻辑 |
| `PRE_RENDER` | 实体绘制前的视觉状态准备 |
| `WORLD_EFFECT` | 世界渲染效果 |
| `POST_AMBIENT` | 环境光之后、点光源之前的效果 |
| `POST_RENDER` | 点光源后的叠加效果 |
| `UI` | ECS UI 之前的游戏 UI 系统 |

同一阶段默认按注册顺序运行，也可以使用 `.before(Type.class)` 或 `.after(Type.class)` 声明游戏系统之间的依赖。循环依赖会在构建 World 前直接报错。

## 5. 资源管线

`AssetManager.loadAssets()` 根据 `assets.txt` 自动识别以下文件：

| 扩展名 | 加载结果 |
| --- | --- |
| `.tmx` | `TiledMap` |
| `.tree` | gdx-ai `BehaviorTree` |
| `.ink.json` | Ink `Story` |
| `.noise.png` | 噪声 `Texture` |
| `.png` | 普通 `Texture` |

`assets.txt` 必须在打包前由资源模块生成。不要使用 `FileHandle.list()` 扫描 jar 内资源；它在桌面开发目录中可能可用，但打包为 jar 后不可靠。

单独加载和读取资源：

```java
assets.loadAsset("maps/island.tmx", TiledMap.class);
TiledMap map = assets.getObejct("maps/island.tmx", TiledMap.class);
ObjectMap<String, TiledMap> maps = assets.getObjects(".tmx", TiledMap.class);
```

注意：当前公开方法名确实是 `getObejct`，拼写不要擅自改成 `getObject`。

## 6. 使用 Tiled 定义实体

### 6.1 基本约定

1. 在 Tiled 中导入项目使用的 `propertytypes.json`。
2. 在 `ENTITY_LAYERS` 指定的对象层中创建对象。
3. Tiled 对象的 `name` 会成为 Artemis `Tag`。
4. 自定义类属性名必须等于 Java 组件的简单类名，例如 `Pos`、`B2dBody`、`Portal`。
5. 类属性内字段名必须与组件的公共字段名一致。
6. 只有带 `@SerializeParam` 的字段会进入实体快照和存档。

Worldloom 使用 `fromMap + mapObjectId` 重新关联实体和原始 Tiled `MapObject`。Artemis 运行时 `entityId` 在重建后可能改变，不应作为跨存档的业务主键。

Worldloom 仓库中的 `src/main/resources/propertytypes.json` 是纯引擎类型源，只声明 Worldloom 组件及其依赖类型，不包含任何游戏业务组件。游戏项目供 Tiled 导入的文件应是“Worldloom 类型 + 游戏类型”的并集：升级引擎时先同步引擎类型，再保留或追加游戏自己的类型。Tiled 项目只导入游戏侧这一个完整文件。

合并时遵守以下规则：

- 同名 Worldloom 类型以当前引擎文件为准，字段、默认值和依赖枚举必须一致。
- 游戏类型不得写回 Worldloom 的类型源。
- `id` 在合并后的文件中必须唯一；类型运行时按 `name` 对应 Java 组件简单类名。
- 已废弃的 `OnInteractive` 和 `Prefabricated` 不应继续使用，当前名称是 `Interactive`。

### 6.2 自动组件

默认情况下，每个地图实体都会拥有：

- `Pos`：世界坐标和运行时 MapObject 关联。
- `Render`：纹理帧、翻转、可见性和渲染参数。
- `ZIndex`：渲染顺序，可通过 `followY` 根据 Y 坐标动态计算。

可通过 `WorldloomConfig.Builder.autoComponents(...)` 替换默认组件。自定义类必须是 Artemis `Component`。

### 6.3 自定义可序列化组件

```java
public class Health extends SerializeComponent {
    @SerializeParam
    public int current;

    @SerializeParam
    public int maximum;

    // Texture、Body、监听器等运行时对象在这里重建
    @Override
    public void reload(World world, EntityDatum datum) {
        super.reload(world, datum);
    }

    // 保存前把运行时状态同步回被标注字段
    @Override
    public void beforeSerialization() {
    }
}
```

规则：

- 需要由 Tiled 和存档构建的组件继承 `SerializeComponent`。
- 持久化字段必须是 `public` 且带 `@SerializeParam`。
- `reload(World, EntityDatum)` 在组件字段恢复后执行，用来重建不可 JSON 化的运行时对象。
- `beforeSerialization()` 在生成快照前执行，用来同步派生状态。
- 未标注字段不会保存，这是设计行为。
- `SerializeComponent.reload` 会通过 `fromMap` 和 `mapObjectId` 找回原始 Tiled 对象。需要进入存档的运行时生成实体，应从地图模板的 `EntityDatum` 构建，或由游戏代码提供等价且可恢复的来源信息。

## 7. 内置组件

| 组件 | 用途与关键点 |
| --- | --- |
| `Pos` | 实体 `x/y`；可 `set`、`copy`，并与 `B2dBody` 同步 |
| `Render` | 当前纹理帧、可见性、翻转、偏移、缩放和旋转；只有 `visible` 被序列化 |
| `ZIndex` | 控制批次排序；`followY` 开启后按纵坐标更新 |
| `B2dBody` | 根据 Tiled 对象/瓦片对象重建 Body 和 Fixture；支持 `setPos`、`flipX` |
| `StateComp` | 通过枚举简单类名建立 gdx-ai 状态机，保存前记录当前状态 |
| `StoryComp` | 保存 Ink 剧本名称、运行状态和初始节点；语言与具体剧情逻辑由游戏实现 |
| `BTree` | 通过 `treeName` 绑定 `.tree` 资源并以实体作为黑板对象 |
| `NavigationAgent` | 导航系统按需创建的运行时路径状态，不需要在Tiled中挂载 |
| `TileAnimation` | 单个 Tiled 动画，支持 LibGDX `Animation.PlayMode`、暂停和帧查询 |
| `TileAnimations` | 从 tileset 收集多个具名动画，通过 `current` 切换 |
| `LayerSampling` | 把某地图瓦片层采样为实体纹理，可跟随该层动画 |
| `ShaderComp` | 为单个实体选择 `.vert`、`.frag` 和可选 uniform 处理类 |
| `Portal` | 描述 `targetMap`、`targetPosEntity` 并发起跨地图迁移 |
| `Direction` | 保存水平、垂直和正交方向 |
| `InputProcess` | 反射创建游戏侧 `InputProcessing`，`enabled` 控制是否更新 |
| `Interactive` | 反射创建游戏侧 `OnInteractListener`，支持独占交互标记 |
| `Owner` | 保存归属者实体 ID，适合装备、建筑或其他实体归属关系 |
| `User` | 保存当前使用者实体 ID，适合载具、座位或临时占用关系 |
| `Slice` | 标记动画帧采用逐层切片渲染，由 `SliceSystem` 构建伪 3D 精灵 |
| `PointLight` | 传统 box2dlights 点光源参数，由 `PointLightSystem` 创建、更新和释放 |
| `Player` | 无字段的玩家标识组件 |
| `Inert` | 让实体退出多种逻辑和渲染系统的处理 |
| `LastId` | 保存序列化前的运行时实体 ID，仅用于重建过程中的关联 |
| `SoarHeight` | 表示实体的视觉悬浮高度 |
| `TopDownPointLight` | 俯视角点光源参数；运行时光源由 `TopDownShadowSystem` 创建和释放 |
| `TopDownShadow` | 标记精灵参与俯视角阴影，同时产生阴影并接收其他精灵的阴影 |
| `Script` | 当前为空的预留组件，尚无执行它的引擎系统 |

`PosFollowBodySystem` 会让 `Pos` 跟随 Box2D Body。直接改 `Pos` 后若实体拥有 Body，应同时调用 `b2dBody.setPos(pos)`。

`Slice` 需要同时挂载 `Render` 和 `TileAnimation`。引擎会暂停该动画的普通逐帧播放，把动画帧反转后写入 `Render.textureSheets`，并按 `0.75` 世界单位逐层偏移。游戏项目不需要再注册切片系统。

迁入 Worldloom 的通用组件使用约定：

- `Owner.id`：归属者实体 ID，默认值为 `-1`。适合表达“物品属于谁”；实体 ID 会随存档保存，但业务层仍应负责在实体删除或重建时维护关系。
- `User.id`：当前使用者实体 ID，默认值为 `-1`。适合表达“载具或设备正在被谁使用”，不用时应恢复为 `-1`。
- `Slice`：无字段标记组件，必须与 `Render + TileAnimation` 同时存在。`SliceSystem` 已由引擎自动安装，不要在游戏模块重复添加。
- `PointLight`：传统 box2dlights 光源，必须与 `Pos` 同时存在，并要求 `WorldloomConfig.Builder.legacyBox2dLights(true)`。`offsetX/offsetY` 是相对实体坐标，`distance` 是光照半径，`color` 是光色，`rays` 是射线数，`onOff` 控制开关；运行时 `light` 字段由引擎管理，不进入存档。

Tiled 中只需给对象添加对应类属性。代码创建时可直接填写数据字段，例如：

```java
Owner owner = world.getMapper(Owner.class).create(itemId);
owner.id = playerId;

PointLight light = world.getMapper(PointLight.class).create(lampId);
light.offsetX = 8f;
light.offsetY = 20f;
light.distance = 96f;
light.color = new Color(1f, 0.82f, 0.55f, 1f);
light.rays = 64;
light.onOff = true;
```

### 7.1 动画切换

在 tileset 的动画瓦片上添加名为 `TileAnimation` 的类属性，并设置 `name`、`playMode`、`offsetX`、`offsetY`。实体挂载 `TileAnimations` 后：

```java
TileAnimations animations = world.getMapper(TileAnimations.class).get(entityId);
animations.changeAnimation("run");

TextureRegion frame = animations.getKeyFrame();
boolean ending = animations.isLast(1);
boolean finished = animations.isAnimationFinished();
```

### 7.2 左右翻转

纹理、Body 和动画帧形状需要保持一致：

```java
boolean flip = direction.horizontal == HorizontalDir.LEFT;
render.flipX = flip;
b2dBody.flipX(render.keyframe.getRegionWidth());
b2dBody.needFlipX = flip;
```

`needFlipX` 会让之后按动画帧新建的形状使用同一方向。目前形状翻转主要针对圆形与矩形。

## 8. 事件总线

获取并派发事件：

```java
EventSystem events = world.getSystem(EventSystem.class);
events.dispatch(event);
```

事件对象既是输入参数，也是同步返回值容器。派发完成后可读取系统写回的字段。

### 8.1 实体事件

| 类型 | 输入/结果 |
| --- | --- |
| `BUILD_ALL` | 从当前地图的 `EntityData` 构建全部实体 |
| `BUILD_ENTITY` | 输入 `entityDatum`；返回 `entityId`、`entity` |
| `BUILD_ENTITIES` | 输入 `entityData` |
| `DELETE_ENTITY` | 输入 `entityId` |
| `DELETE_ALL` | 删除全部实体 |
| `DEL_AND_CREATE_ALL` | 可选输入 `entityData`，删除后重建 |
| `FILTER_DEL_ALL` | 输入 `entityTags`，保留这些 Tag 对应实体 |
| `SERIALIZER_ENTITIES` | 返回 `serializerEntitiesStr` |
| `CREATE_ENTITY_DATUM` | 输入 `entityId`；返回 `entityDatum` |

`CREATE_ENTITY`、`CREATE_PREFAB`、`GET_MAP_OBJECT`、`ADD_AUTO_COMP` 常量仍存在，但当前 `EntityFactory` 没有处理这些分支，不应作为公开工作流使用。

示例：

```java
EntityEvent request = new EntityEvent(EntityEvent.CREATE_ENTITY_DATUM);
request.entityId = playerId;
events.dispatch(request);
EntityDatum snapshot = request.entityDatum;
```

### 8.2 其他事件

| 事件 | 类型 |
| --- | --- |
| `UIEvent` | `REGISTER`, `SHOW`, `HIDE`, `ONLY_SHOW`, `HIDE_ALL`, `GET_TABLE` |
| `CameraEvent` | `SET_TARGET`, `RESIZE`, `JUMP_POS`, `UPDATE_ZOOM` |
| `InteractEvent` | `SHORT_PRESS`, `LONG_PRESS` |
| `MapEvent` | `CHANGE_MAP` |
| `B2dEvent` | `DEL_TILE_COLLIDER`, `CREATE_TILE_COLLIDER` |
| `SystemEvent` | `RESTORE_PROPS` |

## 9. 物理与碰撞

`B2dSystem` 根据 `PHY_LAYERS` 创建地图静态碰撞体，根据实体的 `B2dBody` 创建动态对象。Tiled 瓦片对象可通过 `FixDef` 类属性配置 Fixture：

实体的 `B2dBody` 类属性负责 Body 本身：`defType` 选择 `StaticBody`、`KinematicBody` 或 `DynamicBody`，`defFixed` 控制固定旋转，`linearDamping` 设置线性阻尼。

- `density`
- `friction`
- `restitution`
- `isSensor`
- `sensorType`
- `categoryBit`
- `maskBits`
- `listenerSimpleName`

碰撞分类通过 `CategoryBits` 枚举解析：

```java
short mask = CategoryBits.getMask("I,II");
```

当前解析器直接按逗号分隔并调用 `Enum.valueOf`，名称区分大小写且不要在逗号两侧加入空格。

游戏侧监听器继承以下基类之一：

- `EcsContactListener`：普通 ECS 碰撞监听。
- `SensorContactListener`：传感器监听。
- `ShapeContactListener`：形状级监听。

监听类由 `listenerSimpleName` 反射查找，并必须提供构造器：

```java
public PlayerContact(Entity entity) {
    super(entity);
}
```

Box2D 传感器通常只可靠触发 `beginContact` / `endContact`，不要依赖其 `preSolve` / `postSolve`。

当 LibGDX 日志级别为 `Logger.DEBUG` 时，`RenderPhysicsSystem` 会绘制 Box2D 调试形状。

## 10. 渲染、相机、光照与 Shader

渲染顺序：普通地图层 -> Shader 瓦片层 -> 实体批次 -> 物理调试 -> UI。实体按 `ZIndex` 排序，`Render.visible` 控制是否绘制，`Inert` 实体会被多个渲染/更新系统排除。

### 10.1 相机跟随

```java
CameraTarget target = new CameraTarget("PLAYER");
target.activeWidth = 40;
target.activeHeight = 40;
target.eCenterX = 140;
target.eCenterY = 90;
target.progress = 0.03f;

CameraEvent event = new CameraEvent(CameraEvent.SET_TARGET);
event.target = target;
events.dispatch(event);
```

立即跳转到某位置：

```java
CameraEvent jump = new CameraEvent(CameraEvent.JUMP_POS);
jump.pos = playerPos;
events.dispatch(jump);
```

#### 平滑像素摄像机

像素风项目使用最近邻采样时，连续摄像机位置会让不同精灵边缘分别跨越屏幕像素，产生闪烁；直接取整摄像机又会形成逐像素阶梯移动。可在应用配置中启用平滑像素摄像机：

```java
WorldloomConfig.builder()
    .pixelPerfectCamera(PixelPerfectCameraConfig.enabled())
    // 其它配置
    .build();
```

引擎会使用当前 BackBuffer 尺寸建立一圈扩边的世界缓冲。世界渲染时摄像机对齐屏幕像素，合成时再用逻辑摄像机与对齐位置之间的小数差整体移动缓冲。该模式不会修改实体坐标，也不会把纹理过滤改为线性；UI 在世界缓冲合成后单独绘制。

窗口尺寸变化后，世界缓冲会在下一帧自动重建，因此不需要游戏项目额外处理。默认一像素扩边已经足够；特殊后处理需要更宽采样范围时可以使用 `PixelPerfectCameraConfig.enabled(pixels)`。

游戏系统如果在 `WORLD_EFFECT`、`POST_AMBIENT` 或 `POST_RENDER` 阶段自行使用 `FrameBuffer`，结束临时缓冲后必须恢复世界目标：

```java
private PixelPerfectRenderSystem pixelPerfectRenderSystem;

temporaryBuffer.end();
pixelPerfectRenderSystem.resumeWorldTarget();
```

关闭该模式时，`resumeWorldTarget()` 是空操作，同一套游戏系统无需分支判断。引擎内置阴影以及 Island 使用的海洋缓冲均遵循这个约定。

### 10.2 Shader

`.vert` 和 `.frag` 文件由 `ShaderManager` 读取为源码。给实体挂载 `ShaderComp`，填写 `vertexName` 和 `fragmentName`。如需每帧设置 uniform，创建 `ShaderUniforms` 子类：

```java
public class HurtUniforms extends ShaderUniforms {
    public HurtUniforms(Entity entity) {
        super(entity);
    }

    @Override
    public void setUniforms(float delta) {
        super.setUniforms(delta);
        shaderProgram.bind();
        shaderProgram.setUniformf("u_time", iTime);
    }
}
```

然后把 `ShaderComp.uniformSimpleName` 设为 `HurtUniforms`。该类必须位于 `ReflectionManager` 配置的游戏根包下。

### 10.3 Shader 瓦片层

需要用自定义 Shader 绘制海洋等完整瓦片层时，不要先把图层采样为全地图纹理。实现 `TileLayerShaderUniforms`，并在每次绘制前显式绑定辅助纹理和 Uniform：

```java
public final class WaterUniforms implements TileLayerShaderUniforms {
    @Override
    public void apply(TiledMap map, ShaderProgram shader, float delta) {
        noiseTexture.bind(1);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        shader.setUniformi("u_noise", 1);
    }
}
```

创建引擎时把图层配置交给 `WorldloomBuilder`：

```java
WorldloomEngine engine = Worldloom.engineBuilder()
    .addShaderTileLayer(new TileLayerShaderConfig(
        "WATER", "water_layer", "water", new WaterUniforms()))
    .build();
```

对应 Tiled 图层可以保持隐藏，`ShaderTileLayerRenderSystem` 会直接绘制摄像机范围内的瓦片，并保留 Tiled 动画。系统由引擎固定安排在普通地图与实体批次之间；游戏项目不应再次注册该系统。`TileLayerShaderUniforms.initialize` 适合缓存资源，`dispose` 用于释放实现类自行创建的资源。辅助纹理由 AssetManager 持有时不要重复释放。

瓦片层顶点 Shader 应从 `a_position.xy` 传出世界坐标，不能用单块瓦片的 `v_texCoords` 推导整张地图坐标。

### 10.4 环境光后的游戏系统

天气、云影、海洋等具体视觉效果属于游戏项目。游戏侧系统需要在环境光之后、俯视角点光源和 UI 之前合成时，注册到 `POST_AMBIENT`：

```java
systems.add(EnginePhase.POST_AMBIENT, gameCloudShadowSystem);
```

`POST_AMBIENT` 只定义执行阶段，不依赖任何天气类型或 Shader。游戏侧仍拥有系统的配置、资源和生命周期；Worldloom 负责保证环境光不会覆盖该效果，后续点光源仍可照亮它，并且效果不会覆盖 UI。

### 10.5 光照

需要原 box2dlights 管线时，在 `WorldloomConfig` 中设置 `.legacyBox2dLights(true)`。`LightSystem` 与引擎 Box2D World 和相机协同更新；关闭时保留系统，但不启用实际光照流程。实体挂载 `PointLight + Pos` 后，`PointLightSystem` 会自动创建光源并同步位置，游戏项目不应重复注册该系统。

#### 动态环境光与地图配置

`DynamicAmbientLight` 根据游戏时间更新环境光，并根据 `TiledMapSystem` 的当前地图选择配置。引擎不限定游戏的时间系统；游戏侧时间系统只需实现 `AmbientLightTimeSource`：

```java
public class DateTimeSystem extends BaseSystem implements AmbientLightTimeSource {
    public int hour;
    public int minute;

    @Override
    public int getHour() {
        return hour;
    }

    @Override
    public int getMinute() {
        return minute;
    }
}
```

`AmbientLightProfile.hourly(...)` 接收严格 24 个 `Color`，分别表示 0 点到 23 点的颜色。系统根据当前分钟向下一小时颜色平滑插值。`AmbientLightProfile.constant(...)` 创建全天不变的配置，适合室内或不受世界昼夜影响的地图：

```java
AmbientLightProfile worldProfile = AmbientLightProfile.hourly(hourlyColors);
AmbientLightProfile indoorProfile = AmbientLightProfile.constant(
    new Color(1f, 1f, 1f, 1f)
);

AmbientLightConfig lightConfig = new AmbientLightConfig(worldProfile)
    .setMapProfile("house", indoorProfile)
    .setMapProfile("shop", indoorProfile);
```

构建引擎时，把时间和光照配置交给 `WorldloomBuilder`，并把时间系统注册为游戏系统：

```java
DateTimeSystem dateTimeSystem = new DateTimeSystem();
TopDownShadowConfig shadowConfig = new TopDownShadowConfig();
SunLightConfig sunConfig = new SunLightConfig();
WorldloomEngine engine = Worldloom.engineBuilder()
    .configureLighting(dateTimeSystem, lightConfig, shadowConfig, sunConfig)
    .addModule(systems ->
        systems.add(EnginePhase.UPDATE, dateTimeSystem))
    .build();
```

地图选择规则：

1. 当前地图通过 `setMapProfile(mapName, profile)` 配置过时，使用该地图的配置。
2. 当前地图没有专用配置时，自动使用 `AmbientLightConfig` 的默认配置。
3. 地图切换后不需要手动通知动态光照系统，下一帧会根据新的当前地图自动选择配置。

游戏侧只提供时间来源和配置，不直接注册光影引擎系统。Worldloom 会统一安排 `DynamicAmbientLight`、`DynamicSunLight`、`TopDownShadowSystem`、`LightSystem` 与 UI 的顺序。

### 10.6 俯视角太阳光、点光源与精灵阴影

`TopDownShadowSystem` 使用同一套纹理高度投影处理太阳光和点光源。太阳光只产生阴影；`TopDownPointLight` 使用 box2dlights 生成实际光照，同时使用纹理高度生成阴影。系统逐个渲染并累加点光源，因此不同点光源的阴影不会错误遮挡其他点光源。

太阳轨迹与阴影渲染使用不同配置：

```java
TopDownShadowConfig shadowConfig = new TopDownShadowConfig()
    .setSunShadowOpacity(0.52f)
    .setHeightRange(256f)
    .setResolutionScale(0.5f)
    .setDefaultSunEnabled(false)
    .setMapSunEnabled("world", true);

SunLightConfig sunConfig = new SunLightConfig()
    .setReference(6f, 0f)
    .setDailyBearingSweepDegree(-360f)
    .setElevationRange(26.56505f, 51.34019f);

WorldloomEngine engine = Worldloom.engineBuilder()
    .configureLighting(dateTimeSystem, lightConfig, shadowConfig, sunConfig)
    .addModule(systems ->
        systems.add(EnginePhase.UPDATE, dateTimeSystem))
    .build();
```

`SunLightConfig` 描述太阳本身，不描述角色阴影。默认以 6 点、屏幕 3 点方向为轨迹参考点，并在 24 小时内顺时针旋转完整一圈。太阳在 6、12、18、24 点依次位于钟表的 3、6、9、12 点方向。`TopDownSunLight` 自动把太阳方位增加 180 度得到光线传播和阴影方向，因此阴影始终位于物体背向太阳的一侧。

`TopDownShadowConfig` 默认在所有地图启用太阳阴影，以兼容未配置地图规则的游戏。游戏也可以使用 `setDefaultSunEnabled(false)` 默认关闭，再通过 `setMapSunEnabled(mapName, true)` 只为室外地图开启。关闭太阳阴影不会关闭俯视角点光源及其阴影，地图切换后下一帧自动应用新配置。

太阳高度每 12 小时完成一次最低点到最高点再回到最低点的变化。默认最低高度为 `26.56505` 度，最高高度为 `51.34019` 度。阴影投影比例由 `1 / tan(太阳高度)` 实时计算，因此在 6、12、18、24 点依次形成约 `2.0 -> 0.8 -> 2.0 -> 0.8` 倍。太阳阴影不会在夜间被代码关闭；夜间是否容易观察由环境光亮度决定。

系统顺序由 Worldloom 内部固定：先更新时间和太阳，再绘制地图与实体，随后合成俯视角阴影，最后执行 box2dlights 与 UI。游戏项目不应直接注册这些引擎内部系统。

在 Tiled 的 `propertytypes.json` 中添加并挂载以下组件：

- `TopDownShadow`：无字段。挂载它的可见 `Render + Pos + ZIndex` 实体才会产生并接收俯视角阴影。
- `TopDownPointLight`：需要 `Pos`，可配置 `offsetX`、`offsetY`、`height`、`distance`、`color`、`rays` 和 `onOff`。

点光源的实际世界位置是：

```text
(Pos.x + offsetX, Pos.y + offsetY) * WORLD_SCALE
```

阴影高度不需要在组件中填写。系统使用精灵实际绘制位置计算脚点：

```text
脚点Y = (Pos.y + Render.offsetY + SoarHeight.height + ZIndex.offset)
         * WORLD_SCALE
像素高度 = max(0, 像素世界Y - 脚点Y)
```

因此 `ZIndex.offset` 同时确定排序脚点和阴影高度起点；脚点以下的非透明像素高度按 0 处理，脚点以上逐像素向上累加。系统每帧读取当前 `Render.keyframe`，也支持 `textureSheets`、缩放、翻转、旋转及动画偏移。

运行时可以调整太阳方位和高度：

```java
TopDownShadowSystem shadows = world.getSystem(TopDownShadowSystem.class);
shadows.getSunLight().setSunBearingDegree(35f);
shadows.getSunLight().setElevationDegree(50f);
```

太阳阴影长度不提供固定倍率配置，只由太阳高度计算。地面投影和精灵接收阴影使用同一个实时倍率；精灵自身与其它精灵也使用同一套高度采样规则，不再排除同一实体。`heightRange` 是高度图可表示的最大世界高度；超过它的高度会被截断。`resolutionScale` 控制阴影缓冲区相对窗口的分辨率，默认 `0.5`，提高它会改善边缘精度并增加填充与显存开销。

俯视角光影使用的 GLSL 位于引擎资源目录 `shader/topdown/`。系统通过 `ShaderManager` 按完整 internal/classpath 路径加载，不再把 Shader 字符串写在 Java 类中。`ShaderManager` 仍优先使用游戏 `assets.txt` 中的同名文件，找不到时才回退读取引擎内置资源。

### 10.7 图层采样

`SamplingUtils.getInstance().samplingLayer(map, layerName)` 可直接把一个瓦片层渲染到 `TextureRegion`。`LayerSampling` + `LayerSamplingSystem` 则将这项能力绑定到 ECS 实体，并处理动画层的逐帧采样。返回纹理由 FrameBuffer 持有，长期反复创建时应自行规划释放时机。

## 11. 输入与实体交互

`InputManager` 维护全局 `InputMultiplexer`：

```java
InputManager.addProcessor(processor);
InputManager.removeProcessor(processor);

boolean held = InputManager.isKeyPressed(Input.Keys.W);
boolean once = InputManager.isKeyJustPressed(Input.Keys.SPACE);

InputManager.ENABLE = false; // 全局禁用按键查询
```

### 11.1 实体输入处理器

游戏侧创建无参 `InputProcessing` 子类：

```java
public class PlayerInput extends InputProcessing {
    @Override
    public void update() {
        // 每个 ECS tick 调用
    }
}
```

在 Tiled 的 `InputProcess` 属性中设置：

- `simpleName = PlayerInput`
- `enabled = true`

`InputProcessSystem` 只更新启用的处理器。`InputProcessing.dispose()` 会注销其长按监听器；自定义释放逻辑应保留这一行为。

### 11.2 长按

实现 `LongPressListener`，并通过 `InputManager.addLongPressListener(listener)` 注册。停止使用时调用对应的 `removeLongPressListener`。

### 11.3 交互

给可交互实体挂载 `Interactive`，把 `simpleName` 设为游戏侧 `OnInteractListener` 子类名。监听器必须提供 `(Entity)` 构造器：

```java
public OpenChest(Entity entity) {
    super(entity);
}
```

通过 `InteractEvent(fromId, toId, InteractEvent.SHORT_PRESS)` 或 `LONG_PRESS` 发起交互。给监听器类添加 `@ExclusiveInteract` 可标记为独占交互。

## 12. 状态机与行为树

### 12.1 状态机

游戏状态定义为实现 `State<Entity>` 的枚举。`StateComp` 根据简单类名扫描游戏包、创建状态机，并在保存前记录当前枚举状态。

```java
public enum PlayerState implements State<Entity> {
    IDLE;

    @Override public void enter(Entity entity) {}
    @Override public void update(Entity entity) {}
    @Override public void exit(Entity entity) {}
    @Override public boolean onMessage(Entity entity, Telegram telegram) {
        return false;
    }
}
```

在 Tiled 的 `StateComp` 中填写该枚举的简单类名和初始状态。状态类必须位于反射根包内。

`EceState` 是额外带有 `initialize(Entity)` 方法的便捷接口，但当前 `StateComp` 和 `StateSystem` 不会自动调用该方法；使用它时应由游戏代码自行完成初始化。

### 12.2 行为树

`.tree` 文件会自动加载。实体挂载 `BTree` 并设置 `treeName` 后，`reload` 会把该实体设为行为树对象，`BTreeSystem` 每帧调用 `step()`。

自定义叶节点继承 `EcsLeafTask`，可直接访问：

- `world`
- `entity`
- `entityId`
- `entityTag`
- `tagManager`

内置叶节点：

| 叶节点 | 参数 | 用途 |
| --- | --- | --- |
| `TimeSleep` | `time` | 固定时长等待 |
| `RandomSleep` | `start`, `end` | 随机时长等待 |
| `Range` | `targetEntityTag`, `radius` | 判断目标实体是否进入范围 |
| `MoveTo` | `x`, `y`, `targetTag`, `stopDistance` | 沿当前地图导航网格移动 |

## 13. Scene2D UI 与库存

### 13.1 ECS UI

UI 类继承 `BaseEcsUI`。它本质上是 `Table`，并提供 `world`、`tagManager`、`assetSystem`、`eventSystem` 和 `skin`：

```java
public class HudTable extends BaseEcsUI {
    public HudTable(World world) {
        super(world);
        add(new Label("HUD", skin));
    }
}
```

通过事件注册和控制 UI：

```java
UIEvent register = new UIEvent(UIEvent.REGISTER);
register.table = new HudTable(world);
events.dispatch(register);

UIEvent only = new UIEvent(UIEvent.ONLY_SHOW);
only.uiClass = HudTable.class;
events.dispatch(only);
```

`RenderUISystem` 以 Table 的具体 `Class` 为键，同一类只能注册一个实例。`GET_TABLE` 派发后从 `event.table` 读取结果。

`UIStage` 是使用 Worldloom UI 尺寸和 FitViewport 的独立 Stage，可为特殊页面传入私有 zoom。主 ECS UI 则由 `RenderUISystem` 的 `ExtendViewport` 管理。

### 13.2 库存网格

相关类：

- `SlotDatum`：物品 ID、堆叠数量、最大堆叠、类型和图标等格子数据。
- `ItemSlot`：单个可选中、可禁用的格子 Actor。
- `ItemSlotGrid`：二维格子、拖放、合并、交换、来源限制和所有者管理。

```java
DragAndDrop dragAndDrop = new DragAndDrop();
ItemSlotGrid grid = new ItemSlotGrid(world, dragAndDrop, "default-slot");
grid.setOwner(playerId);
grid.setSlotData(slotRows);
grid.rebuild();
```

常用扩展点：

- `onDragStart`：定制拖动载荷。
- `onDrop`：定制格子接收行为，默认同物品合并、不同物品交换。
- `onDragStop`：处理拖到空白处。
- `addBlockedSourceType`：拒绝来自某类库存视图的物品。
- `setCanDragStop(false)`：禁止丢到空白处。
- `setSelectOnly`：单选某格。

调用 `setSlotData` 后必须调用 `rebuild()` 才会生成格子 Actor。

## 14. Ink 剧情、本地化与页面管理

### 14.1 Ink

所有 `.ink.json` 会被自动加载到 `StoryManager`：

```java
StoryManager.changeStory("intro");
Bag<String> lines = StoryManager.getLines();
StoryManager.Continue();
boolean finished = StoryManager.isFinished();
```

`getLines()` 会反复调用 Ink 的 `Continue()`，一次取完当前所有可继续文本，通常会停在选项或故事结束处。`StoryManager.Continue()` 只推进一次；不要在 `getLines()` 已经耗尽当前段落后无条件继续调用。使用前先通过 `changeStory` 选择已加载故事。

需要让地图实体持有剧情时，可在 Tiled 中挂载 `StoryComp` 并填写 `storyName`：

```java
StoryComp storyComp = entity.getComponent(StoryComp.class);
StoryManager.changeStory(storyComp.storyName);
```

`saveJson` 用于保存 Ink 运行状态，`startNode` 是首次进入剧本时的起始 knot 或 stitch，未填写时默认为 `start`。组件只保存通用剧本状态；语言后缀、剧情标签到游戏事件的转换以及对话 UI 均由游戏项目实现。

### 14.2 本地化

`BundleManager` 使用 LibGDX 原生 AssetManager：

```java
BundleManager.initialize(gdxAssetManager, "i18n/messages", "zh");
String title = BundleManager.get("menu.title");
BundleManager.changeLanguage("en");
```

`changeLanguage` 会卸载并同步重新加载 bundle，适合语言切换而非每帧调用。

### 14.3 Game 与 Screen 注册表

```java
GameManager.setCurrent(game);
Game current = GameManager.getCurrent();

ScreenManager.register(menuScreen);
Screen menu = ScreenManager.getScreen(MenuScreen.class);
GameManager.getCurrent().setScreen(menu);
```

`ScreenManager` 只负责按类保存和查找 Screen，不会自动执行 `Game.setScreen`。

## 15. 存档、读档与多地图切换

### 15.1 数据模型

一次游戏会话由单例 `GameSnapshotManager` 持有：

```text
GameSnapshot
├── saveFormatVersion               独立存档格式版本
├── curtMap                         当前地图
├── entityData[mapName]             每张地图的实体快照
└── systemProps[systemClassName]    可序列化系统属性
```

新游戏并不是只创建当前地图。`startNewGame(initialMap)` 会从 `MapManager` 的初始模板复制所有地图的 `EntityData`，因此尚未进入的地图也有独立初始状态。

### 15.2 保存

```java
String json = engine.createSaveJson();
```

`createSaveJson` 会先：

1. 从当前 ECS World 序列化当前地图全部实体。
2. 把结果覆盖到 `entityData[currentMap]`。
3. 采集所有 `@SerializeSystem` 系统中的 `@SerializeParam` 公共字段。
4. 序列化完整 `GameSnapshot`。

仅调用 `getSaveJson()` 不会先捕获运行时变化；正常保存应使用 `createSaveJson(world)`。

### 15.3 读档

```java
Worldloom.loadGame(json);
WorldloomEngine engine = Worldloom.engineBuilder()
    .addModule(new GameModule())
    .build();
```

读档的正确含义是“先用 JSON 替换会话状态，再创建新的 ECS World”。不要先创建 World 再调用 `loadSaveJson`，否则地图系统已经按旧状态初始化。

### 15.4 保存自定义系统字段

```java
@SerializeSystem
public class QuestSystem extends BaseSystem {
    @SerializeParam
    public int chapter;

    @Override
    protected void processSystem() {}
}
```

`SysRestoreSystem` 在 World 初始化阶段按系统完整类名恢复字段。Worldloom 4.0.0 会自动把旧存档中的 `org.ltae.*` 类型名迁移为 `org.worldloom.*`；其他系统改名仍需要新增迁移规则。字段类型和字段名同样属于存档格式。

### 15.5 Portal 切图

在 Tiled 实体上挂载 `Portal`：

- `targetMap`：目标地图名，必须与 MapManager 使用的地图名一致。
- `targetPosEntity`：目标地图内作为出生点的实体 Tag。

触发传送：

```java
Portal portal = world.getMapper(Portal.class).get(portalEntityId);
int[] carried = {playerId, companionId};
portal.teleport(carried, playerId, true);
```

`switchMap = true` 的事务顺序：

1. 游戏代码调用 `portal.teleport(..., true)`。
2. `MapTransitionSystem` 捕获当前地图的实体快照和系统状态。
3. 从来源地图快照中移除需要携带的实体。
4. 保留携带实体，删除当前 ECS World 中的其他实体。
5. 切换 Tiled 地图、渲染器和瓦片碰撞。
6. 根据目标地图快照重建目标地图实体。
7. 把携带实体移动到 `targetPosEntity` 对应的位置。
8. 把相机跳转到 `playerEntityId` 对应实体的位置。
9. 把当前地图更新为 `targetMap`。

目标地图与当前地图相同时，不重建世界，只把携带实体移动到 `targetPosEntity`。

`switchMap = false` 表示只迁移数据：实体会从当前地图快照和 ECS World 中删除，其最新快照被加入目标地图，但当前地图、渲染器和相机不切换。适合把 NPC、掉落物等送往其他地图。

关键约束：

- `entityIds` 必须包含所有需要跨图保留的运行时实体。
- `playerEntityId` 仅用于切图后相机跳转。
- 携带实体应有 `Pos`；有 `B2dBody` 时位置会同步到 Body。
- 找不到目标 Tag 时实体会被放到 `(0, 0)` 并记录错误日志。
- 目标地图快照由 `GameSnapshotManager.getEntityData(targetMap)` 提供，不存在时会创建空数据。

## 16. Worldloom 安装的系统

正常设计下，引擎负责以下系统，游戏项目不要重复注册：

| 阶段 | 系统 |
| --- | --- |
| 基础设施 | `EventSystem`, `AssetSystem`, `TiledMapSystem`, `B2dSystem` |
| 游戏逻辑 | `InputProcessSystem`, `OnInteractSystem`, `MapTransitionSystem`, `PosFollowBodySystem`, `BTreeSystem`, `StateSystem`, `CameraSystem`, `AudioSystem`, `KeyframeShapeSystem`, `TileAnimSystem`, `SliceSystem`, `LayerSamplingSystem`, `ZIndexSystem` |
| 光影 | `DynamicAmbientLight`, `DynamicSunLight`, `TopDownShadowSystem`, `PointLightSystem`, `LightSystem`, `TopDownPointLightRenderSystem` |
| 渲染 | `RenderTiledSystem`, `ShaderTileLayerRenderSystem`, `RenderBatchingSystem`, `RenderFrameSystem`, `RenderPhysicsSystem` |
| 恢复与 UI | `SysRestoreSystem`, `EntityFactory`, `RenderUISystem` |

同时依赖：

- `ExtendedComponentMapperPlugin`
- `ProfilerPlugin`
- `TagManager`
- `PlayerManager`
- `TeamManager`
- `EntityLinkManager`
- `WorldSerializationManager`

## 17. 实用工具与底层入口

- `JsonManager`：统一的 LibGDX JSON `toJson` / `fromJson`。
- `SkinManager.getSkin(path)`：加载或获取 Scene2D Skin。
- `ShaderManager`：读取已加载的 vertex/fragment shader 文本。
- `ShapeUtils`：从 Tiled `MapObject` 创建 Box2D Shape，并支持水平翻转。
- `SamplingUtils`：把地图瓦片层渲染成纹理。
- `MapManager.getTiledMap(name)`：按地图名取 TiledMap。
- `MapManager.getMapObject(map, id)`：按来源地图和对象 ID 重新关联对象。
- `MapManager.getPhyLayer(map)`：取得配置的物理层。
- `EntityBuilder` / `EntitySerializer` / `EntityDeleter`：底层实体构建、快照和删除 API。业务代码优先使用事件或 `GameSnapshotManager`，避免绕过会话状态。

`org.worldloom.script.Script` 当前是未接入运行时流程的预留类型，不应把它视为已经完成的脚本系统。

## 18. 已知限制与排查

### `WorldState is not initialized`

调用 `engineBuilder().build()` 前未执行 `Worldloom.startNewGame()` 或 `Worldloom.loadGame(json)`。

### 地图或实体层为空

确认资源已经 `update()` 到 100%，随后才调用 `Worldloom.initializeMaps()`；同时检查配置中的地图名和实体层名。

### 游戏侧反射类找不到

确认已调用 `Worldloom.setGameRootClass(MainGame.class)`，类位于该根包下，Tiled 中填写的是简单类名，并满足构造器约定。

### Tiled 组件没有生成

确认 `propertytypes.json` 中的类型名与 Java 组件简单类名完全一致。不要继续使用旧文档中的 `OnInteractive`、`BaseUI` 或 `LtaeBuilder`。

### 存档后字段丢失

只有公共且标注 `@SerializeParam` 的字段会保存。Body、Texture、监听器、状态机等运行时对象应由 `reload` 重建。

### 读档后实体 ID 改变

这是 Artemis 重建实体的正常结果。持久身份应使用 Tiled 来源信息、Tag 或游戏自己的稳定 ID。

### 无物理 Body 的实体读档后回到 Tiled 初始坐标

当前 `Pos.reload()` 无条件从原始 `MapObject` 重新设置 `x/y`。拥有 `B2dBody` 的实体会用快照坐标重建 Body，之后 `PosFollowBodySystem` 可把位置同步回来；只有 `Pos` 而没有 Body 的自由移动实体则会保留 Tiled 初始坐标。这是当前实现行为，需要保存此类实体位置时应先修正 `Pos.reload` 生命周期。

### jar 中找不到资源

不要依赖 `FileHandle.list()`；检查资源模块是否正确生成并打包 `assets.txt`。

### 自动化验证范围

当前仓库包含环境光曲线与二维空间音频的自动化测试。版本升级后仍应在实际游戏项目中验证：新游戏、保存、读档、同图传送、跨图传送、携带实体、系统字段恢复、UI 输入链路以及真实音频后端播放。

## 19. 推荐的项目侧分层

为了避免存读档和切图逻辑再次分散，游戏项目建议保持以下边界：

- 菜单/存档 UI：只负责读取文件或字符串，并调用 `Worldloom.startNewGame()` / `Worldloom.loadGame(json)`。
- 游戏 Screen：只负责创建、驱动和销毁 `WorldloomEngine`，首次实体构建由引擎完成。
- 存档服务：只通过 `engine.createSaveJson()` 获取最新完整存档。
- 门、剧情和交互代码：只调用 `Portal.teleport(...)`，不自行拼接删除、建图、重建和相机步骤。
- 自定义组件：只声明可保存数据与 `reload` 生命周期，不直接管理整个 `GameSnapshot`。
- 自定义系统：需要跨存档的数据统一使用 `@SerializeSystem` + `@SerializeParam`。

这样，Tiled 负责静态初始定义，`GameSnapshotManager` 负责会话数据，`MapTransitionSystem` 负责切图事务，游戏层只负责触发流程和持久化 JSON。

## 20. 声音系统

Worldloom 使用 LibGDX 原生音频后端，并通过现有 `EventSystem` 接收播放与控制消息。短音效使用内存中的 `Sound`，长音乐使用流式 `Music`。声音资源属于游戏内容，应放在游戏项目而不是 Worldloom：

```text
assets/audio/sounds/   短音效、循环环境声，可并发播放
assets/audio/music/    背景音乐、较长音轨，流式播放
```

`AssetManager.loadAssets()` 会根据目录决定相同扩展名的加载类型，支持 `.ogg`、`.wav` 和 `.mp3`。推荐使用 OGG。资源必须出现在游戏生成的 `assets.txt` 中。

播放事件只需要传入对应目录下的相对名称，并可省略默认的 `.ogg` 后缀。例如 `playSound("ui/click")` 对应 `audio/sounds/ui/click.ogg`，`playMusic("island_day")` 对应 `audio/music/island_day.ogg`。传入 `.wav`、`.mp3` 或完整标准路径时会原样保留；声音事件传入音乐目录或音乐事件传入声音目录会立即报错。

声音系统由 Worldloom 自动注册，游戏项目不要手动添加 `AudioSystem`。可在构建引擎时覆盖默认配置：

```java
AudioConfig audioConfig = new AudioConfig()
    .setMasterVolume(0.9f)
    .setBusVolume(AudioBus.MUSIC, 0.7f)
    .setMaxSoundInstances(48)
    .setMaxInstancesPerSound(6)
    .setDefaultMinDistance(32f)
    .setDefaultMaxDistance(480f)
    .setDefaultRolloff(1f)
    .setDefaultPanningStrength(1f);

WorldloomEngine engine = Worldloom.engineBuilder()
    .configureAudio(audioConfig)
    .addModule(new GameModule())
    .build();
```

### 20.1 播放音效和音乐

```java
EventSystem events = world.getSystem(EventSystem.class);

// 普通 UI 音效，不计算空间位置
events.dispatch(AudioEvent.playSound("ui/click")
    .bus(AudioBus.UI));

// 世界音效：监听者左侧为左声道，右侧为右声道，并按距离衰减
AudioEvent footstep = AudioEvent.playSound(
        "world/footstep_grass")
    .at(x, y)
    .distances(24f, 300f)
    .rolloff(1.2f)
    .volume(0.8f)
    .pitch(1f);
events.dispatch(footstep);

// 循环声源跟随实体，系统每帧读取该实体的 Pos
AudioEvent campfire = AudioEvent.playSound(
        "world/fire_loop")
    .bus(AudioBus.AMBIENT)
    .loop()
    .follow(campfireEntityId)
    .distances(32f, 420f);
events.dispatch(campfire);
long campfireHandle = campfire.handle;

// 背景音乐；switchMusic 会让旧音乐淡出、新音乐淡入
events.dispatch(AudioEvent.switchMusic(
    "island_day", 1.5f).loop().volume(0.7f));

// 非无缝音轨：播放结束后静音 5 秒，再次淡入播放
events.dispatch(AudioEvent.switchMusic(
    "town_theme.mp3", 1.5f).loop(5f).volume(0.7f));
```

`dispatch` 是同步的，播放成功后句柄写入同一个事件的 `handle`。加载失败时句柄保持 `-1`，并由 `AudioSystem` 记录英文错误日志。

`loop()` 使用底层音乐的原生无间隔循环，适合已经制作成无缝首尾的音轨。`loop(intervalSeconds)` 会等待音轨自然结束，再经过指定的静音间隔重新播放；已经配置的淡入时间会应用到每次重新播放。间隔等待期间，暂停实例或音乐总线也会暂停倒计时。

### 20.2 控制播放实例

```java
events.dispatch(AudioEvent.pause(campfireHandle));
events.dispatch(AudioEvent.resume(campfireHandle));
events.dispatch(AudioEvent.move(campfireHandle, newX, newY));
events.dispatch(AudioEvent.fade(campfireHandle, 0f, 0.8f));
events.dispatch(AudioEvent.stop(campfireHandle));
```

总线可统一控制同类声音：

```java
events.dispatch(AudioEvent.setMasterVolume(0.8f));
events.dispatch(AudioEvent.setBusVolume(AudioBus.MUSIC, 0.5f));
events.dispatch(AudioEvent.pauseBus(AudioBus.SFX));
events.dispatch(AudioEvent.resumeBus(AudioBus.SFX));
events.dispatch(AudioEvent.stopBus(AudioBus.AMBIENT));
```

### 20.3 监听者与二维立体声

默认监听者是 `CameraSystem` 摄像机中心，适合摄像机跟随玩家的常规场景。也可以让监听者直接跟随带有 `Pos` 的实体，或设为固定坐标：

```java
events.dispatch(AudioEvent.followListener(playerEntityId));
events.dispatch(AudioEvent.listenerAt(x, y));
events.dispatch(AudioEvent.useCameraListener());
```

空间声音按声源与监听者的距离计算音量衰减，并按水平相对位置计算 `pan`。这属于俯视角 2D 立体声，不是带耳廓、遮挡和混响模拟的 HRTF。瞬时音效在触发时取得位置；使用 `.follow(entityId)` 的声音会持续更新，实体被删除后循环声音会自动停止。

`minDistance` 内保持完整音量，`maxDistance` 外静音，二者之间按 `rolloff` 衰减。`panningStrength` 为 `0` 时关闭左右声像，默认值 `1`。

### 20.4 使用边界

- 同一个 `Sound` 可以并发播放，系统会限制全局实例数和同资源实例数，超出时停止最早实例。
- `Music` 是单个流式播放器，适合背景音乐，不适合同一资源的并发复音。需要空间复音的长循环环境声可酌情作为 `Sound` 加载。
- `Sound` 后端没有完成回调。一次性音效的句柄会在配置的跟踪时间后清理，但声音本身由 LibGDX 正常播放。
- 音频偏好设置可直接转换成 master 与各总线音量事件，不需要业务系统持有 LibGDX 音频对象。

## 21. 寻路与行为树移动

`NavigationSystem` 使用 gdx-ai 的八方向 A*。地图只提供导航网格范围和瓦片尺寸，
障碍直接读取当前 Box2D 世界中非传感器的静态 Fixture，因此瓦片碰撞、建筑实体和
其他静态形状共用同一份物理结果。地图切换后会自动丢弃旧图缓存；A* 只在发起请求时
运行，不会每帧重新计算。

### 21.1 配置道路偏好

可以为每张地图指定一个道路瓦片层。道路不是可通行边界：整个地图仍然可以行走，
Box2D Fixture仍然是硬障碍，道路格只拥有更低的寻路代价，使角色在合理距离内优先
绕行道路而不是穿过草地：

```java
WorldloomConfig.builder()
    .navigationRoadLayer("island", "ROAD")
    .navigationCosts(1f, 4f);
```

`navigationCosts`依次接收道路代价与非道路代价，必须满足
`0 < roadCost <= offRoadCost`。道路层可以隐藏；格子内存在任意瓦片即视为道路。
没有配置道路层的地图保持普通等权寻路。

### 21.2 在 Tiled 中配置可寻路角色

需要寻路的角色必须同时具有：

- `Pos`：保存角色的Tiled像素坐标。使用默认自动组件配置时不需要手动添加。
- `B2dBody`：提供实际移动和碰撞；NPC通常使用 `DynamicBody` 并开启固定旋转。

挂载步骤：

1. 确认Tiled项目导入的是Island中的 `assets/tiled/propertytypes.json`。
2. 在地图的实体对象层中选中需要寻路的角色对象。
3. 给对象挂载 `BTree`，并在行为树中使用 `MoveTo`。
4. 确认对象同时挂有 `B2dBody`，并且角色使用的瓦片已经定义所需Fixture。
5. 保存地图。无需在Island中注册 `NavigationSystem`，引擎会按固定顺序自动安装。

`NavigationAgent` 会在第一次寻路时自动创建，默认参数为：

- `speed`：移动速度，单位为 Tiled 像素/秒。
- `radius`：实体中心需要与障碍保持的净空，单位为 Tiled 像素。
- `waypointTolerance`：到达中间路径点的容差，单位为 Tiled 像素。

`radius`应参考角色脚底碰撞体的水平半径，而不是整张角色纹理的宽度。例如角色纹理宽
32px，但脚底Fixture只有8px宽时，`radius`可先设置为 `4`。数值过大会使角色无法通过
窄路，设置为 `0` 则只按导航格中心判断障碍。Island当前角色移动速度约为25时，可先使用：

```text
speed: 25
radius: 4
waypointTolerance: 1
```

游戏代码也可以先取得或创建 `NavigationAgent` 调整这些参数；普通行为树角色无需处理。

### 21.3 通过代码发起寻路

引擎已经按固定顺序安装 `NavigationSystem`，游戏项目不需要再次注册。业务代码可直接发起导航：

```java
NavigationSystem navigation = world.getSystem(NavigationSystem.class);
int requestId = navigation.navigate(npcEntityId, targetX, targetY, 4f);
NavigationStatus status = navigation.getStatus(npcEntityId, requestId);
navigation.cancel(npcEntityId);
```

第四个参数是允许停止的目标距离。每个请求都有独立编号；若同一实体收到新请求，旧编号查询时会返回 `FAILED`，避免旧任务误判新路径的结果。

### 21.4 通过行为树发起寻路

要让角色由行为树控制，还需要在同一个Tiled对象上添加 `BTree`，并把 `treeName` 设置为
行为树文件名（不包含 `.tree` 后缀）。该 `.tree` 文件必须位于游戏资源目录并进入
`assets.txt`，以便Worldloom资源管理器自动加载。

行为树可使用内置 `MoveTo` 叶节点。使用坐标目标：

```text
import moveTo:"org.worldloom.ai.MoveTo"

root
  moveTo x:320 y:240 stopDistance:2
```

或者使用实体 Tag，非空的 `targetTag` 优先于 `x/y`：

```text
import moveTo:"org.worldloom.ai.MoveTo"

root
  moveTo targetTag:"PLAYER" stopDistance:24
```

目标 Tag 在任务开始时解析，本版本不持续追踪移动目标。无路径、目标越界、目标位于障碍格或缺少必要组件时任务返回 `FAILED`；到达返回 `SUCCEEDED`，其余时间返回 `RUNNING`。任务被行为树中断时会取消导航并停止实体。

### 21.5 使用边界

当前实现只负责已加载地图内的静态 Box2D 障碍。传感器和动态 Body 不会固化进导航图，
动态实体之间仍由 Box2D 碰撞处理；跨地图路线、后台 NPC 模拟和动态障碍重规划不属于这一层。

如果游戏在当前地图运行期间创建或删除了静态Fixture，应调用
`world.getSystem(NavigationSystem.class).invalidate()`，让下一次寻路请求重新读取Box2D世界。

## 22. Debuff系统

Worldloom只管理Debuff名称、剩余时间和通用生命周期，不包含扣血、减速、动画或
粒子等游戏规则。持续时间、周期和具体行为全部由游戏项目中的Debuff实现决定；
同名效果再次添加时会刷新持续时间。

引擎会按固定顺序安装 `DebuffSystem`，游戏项目不需要重复注册。施加效果时发送事件：

```java
eventSystem.dispatch(DebuffEvent.add(entityId, "poison"));
```

移除一个效果或清空实体全部效果：

```java
eventSystem.dispatch(DebuffEvent.remove(entityId, "poison"));
eventSystem.dispatch(DebuffEvent.clear(entityId));
```

游戏项目通过实现 `DebuffHandler` 定义效果：

```java
public final class PoisonHandler implements DebuffHandler {
    @Override
    public float getDuration() {
        return 10f;
    }

    @Override
    public float getTickInterval() {
        return 1f;
    }

    @Override
    public void onTick(DebuffContext context) {
        // 在游戏项目中查找自己的生命组件并扣除伤害
    }
}
```

在游戏系统初始化时注册：

```java
world.getSystem(DebuffSystem.class)
    .registerHandler("poison", new PoisonHandler());
```

可用回调包括首次生效 `onApplied`、重复添加 `onRefreshed`、逐帧更新 `onUpdate`、
周期触发 `onTick` 和结束清理 `onRemoved`。`Debuffs.effects` 只保存当前效果的字符串
名称；计时状态只存在于 `DebuffSystem` 内存中，不写入存档。读档或重新创建实体后，
效果会按照自身定义的完整持续时间重新开始。
通过事件动态添加时，实体不需要预先在Tiled中挂 `Debuffs`，系统会自动创建。

## 23. 从 LTAE 3.8.2.37 迁移

Worldloom 4.0.0 是一次破坏性升级，旧版本和旧 Git 标签继续保留。迁移时需要同时完成以下修改：

1. 依赖改为 `com.github.IteratingSystem:worldloom:4.0.0`。
2. Java 包从 `org.ltae` 改为 `org.worldloom`。
3. 用 `WorldloomConfig` 和 `Worldloom.configure(...)` 替换静态 `LtaePluginRule`。
4. 资源完成后调用 `Worldloom.initializeMaps()`。
5. 用 `Worldloom.startNewGame()` 或 `Worldloom.loadGame(json)` 建立会话。
6. 用 `WorldloomEngine` 替代游戏项目直接创建 Artemis World。
7. 游戏业务系统通过 `WorldloomGameModule` 和 `EnginePhase` 注册。
8. Scene2D Skin 中的完整样式类名同步改为 `org.worldloom.*`。

Tiled 中使用的组件简单名称保持不变，不需要为了品牌迁移修改地图对象。旧存档中的 `org.ltae.*` 系统类名和字段类型会在读取时自动迁移，加载成功后再次保存即可写成当前格式。
