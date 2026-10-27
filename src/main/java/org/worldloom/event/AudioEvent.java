package org.worldloom.event;

import org.worldloom.audio.AudioBus;
import org.worldloom.audio.AudioAssetPath;
import org.worldloom.audio.AudioPlayMode;

/**
 * 声音系统消息。通过 {@code EventSystem.dispatch(event)} 同步发送。
 * 播放后可以从 {@link #handle} 取得统一实例句柄。
 */
public class AudioEvent extends TypeEvent {
    public static final int PLAY_SOUND = 1;
    public static final int PLAY_MUSIC = 2;
    public static final int PAUSE = 3;
    public static final int RESUME = 4;
    public static final int STOP = 5;
    public static final int MOVE_SOURCE = 6;
    public static final int FADE_TO = 7;
    public static final int SET_MASTER_VOLUME = 8;
    public static final int SET_BUS_VOLUME = 9;
    public static final int PAUSE_BUS = 10;
    public static final int RESUME_BUS = 11;
    public static final int STOP_BUS = 12;
    public static final int SET_LISTENER_POSITION = 13;
    public static final int FOLLOW_LISTENER_ENTITY = 14;
    public static final int USE_CAMERA_LISTENER = 15;

    public String path;
    public AudioBus bus = AudioBus.SFX;
    public AudioPlayMode playMode = AudioPlayMode.ONCE;
    public long handle = -1L;
    public float volume = 1f;
    public float pitch = 1f;
    public float x;
    public float y;
    public boolean spatial;
    public int sourceEntityId = -1;
    /** 固定声源的地图；留空表示全局，实体跟随声源优先使用实体当前归属。 */
    public String mapId;
    public float minDistance = -1f;
    public float maxDistance = -1f;
    public float rolloff = -1f;
    public float panningStrength = -1f;
    public float fadeSeconds;
    public float targetVolume;
    public boolean replaceMusic;
    public float loopIntervalSeconds;
    public int listenerEntityId = -1;

    /**
     * 创建指定类型的底层声音事件。
     * 通常应优先使用本类提供的静态工厂方法，避免遗漏必要参数。
     *
     * @param type 事件类型常量
     */
    public AudioEvent(int type) {
        super(type);
    }

    /**
     * 创建播放短音效的事件。
     * 路径相对于 {@code audio/sounds/}，省略扩展名时默认使用
     * {@code .ogg}。例如 {@code playSound("ui/click")} 会解析为
     * {@code audio/sounds/ui/click.ogg}。
     *
     * @param path 音效相对路径或完整标准路径
     * @return 可继续配置的声音事件
     */
    public static AudioEvent playSound(String path) {
        AudioEvent event = new AudioEvent(PLAY_SOUND);
        event.path = AudioAssetPath.sound(path);
        return event;
    }

    /**
     * 创建播放流式音乐的事件。
     * 路径相对于 {@code audio/music/}，省略扩展名时默认使用
     * {@code .ogg}。音乐事件默认使用 {@link AudioBus#MUSIC} 总线。
     *
     * @param path 音乐相对路径或完整标准路径
     * @return 可继续配置的音乐事件
     */
    public static AudioEvent playMusic(String path) {
        AudioEvent event = new AudioEvent(PLAY_MUSIC);
        event.path = AudioAssetPath.music(path);
        event.bus = AudioBus.MUSIC;
        return event;
    }

    /**
     * 使用淡入淡出替换当前音乐。秒数为零时立即替换。
     * 新音乐淡入的同时，当前音乐总线上的旧音乐会淡出并停止。
     *
     * @param path 新音乐相对路径或完整标准路径
     * @param fadeSeconds 淡入淡出时间，单位为秒
     * @return 可继续配置的音乐事件
     */
    public static AudioEvent switchMusic(String path, float fadeSeconds) {
        AudioEvent event = playMusic(path);
        event.replaceMusic = true;
        event.fadeSeconds = requireNonNegative(fadeSeconds, "fadeSeconds");
        return event;
    }

    /**
     * 暂停指定播放实例，保留其当前播放位置。
     *
     * @param handle 播放事件派发成功后返回的实例句柄
     * @return 暂停事件
     */
    public static AudioEvent pause(long handle) {
        return control(PAUSE, handle);
    }

    /**
     * 恢复指定的已暂停播放实例。
     * 如果该实例所属总线仍处于暂停状态，则等待总线恢复后再播放。
     *
     * @param handle 播放实例句柄
     * @return 恢复事件
     */
    public static AudioEvent resume(long handle) {
        return control(RESUME, handle);
    }

    /**
     * 立即停止指定播放实例并释放其播放句柄。
     * 音频资源本身仍由资源管理器持有，不会在这里释放。
     *
     * @param handle 播放实例句柄
     * @return 停止事件
     */
    public static AudioEvent stop(long handle) {
        return control(STOP, handle);
    }

    /**
     * 把已播放的空间声源移动到固定世界坐标。
     * 调用后会取消该声源原有的实体跟随关系。
     *
     * @param handle 播放实例句柄
     * @param x 新的世界横坐标
     * @param y 新的世界纵坐标
     * @return 移动声源事件
     */
    public static AudioEvent move(long handle, float x, float y) {
        AudioEvent event = control(MOVE_SOURCE, handle);
        event.x = x;
        event.y = y;
        event.spatial = true;
        return event;
    }

    /**
     * 在指定时间内把实例基础音量渐变到目标值。
     * 目标音量为 {@code 0} 时，渐变完成后会停止该实例。
     *
     * @param handle 播放实例句柄
     * @param targetVolume 目标基础音量，范围为 0 至 1
     * @param seconds 渐变时间，单位为秒；0 表示立即生效
     * @return 音量渐变事件
     */
    public static AudioEvent fade(long handle, float targetVolume,
                                  float seconds) {
        AudioEvent event = control(FADE_TO, handle);
        event.targetVolume = requireUnit(targetVolume, "targetVolume");
        event.fadeSeconds = requireNonNegative(seconds, "seconds");
        return event;
    }

    /**
     * 设置所有声音共享的主音量。
     * 最终音量由实例音量、主音量、总线音量和空间衰减共同决定。
     *
     * @param volume 主音量，范围为 0 至 1
     * @return 设置主音量事件
     */
    public static AudioEvent setMasterVolume(float volume) {
        AudioEvent event = new AudioEvent(SET_MASTER_VOLUME);
        event.volume = requireUnit(volume, "volume");
        return event;
    }

    /**
     * 设置指定音频总线的音量。
     *
     * @param bus 要调整的音频总线
     * @param volume 总线音量，范围为 0 至 1
     * @return 设置总线音量事件
     */
    public static AudioEvent setBusVolume(AudioBus bus, float volume) {
        AudioEvent event = busControl(SET_BUS_VOLUME, bus);
        event.volume = requireUnit(volume, "volume");
        return event;
    }

    /**
     * 暂停指定总线上的全部实例，此后在该总线上新播放的声音也会保持暂停。
     *
     * @param bus 要暂停的音频总线
     * @return 暂停总线事件
     */
    public static AudioEvent pauseBus(AudioBus bus) {
        return busControl(PAUSE_BUS, bus);
    }

    /**
     * 恢复指定总线因总线暂停而停止的实例。
     * 单独手动暂停的实例不会因此恢复。
     *
     * @param bus 要恢复的音频总线
     * @return 恢复总线事件
     */
    public static AudioEvent resumeBus(AudioBus bus) {
        return busControl(RESUME_BUS, bus);
    }

    /**
     * 停止并移除指定总线上的全部播放实例。
     *
     * @param bus 要停止的音频总线
     * @return 停止总线事件
     */
    public static AudioEvent stopBus(AudioBus bus) {
        return busControl(STOP_BUS, bus);
    }

    /**
     * 把空间声音监听者固定在指定世界坐标。
     *
     * @param x 监听者世界横坐标
     * @param y 监听者世界纵坐标
     * @return 设置监听者事件
     */
    public static AudioEvent listenerAt(float x, float y) {
        AudioEvent event = new AudioEvent(SET_LISTENER_POSITION);
        event.x = x;
        event.y = y;
        return event;
    }

    /**
     * 让空间声音监听者持续跟随指定实体的 {@code Pos} 组件。
     * 这是设置听者位置，不是设置声源跟随。
     *
     * @param entityId 监听者实体 ID
     * @return 跟随监听者事件
     */
    public static AudioEvent followListener(int entityId) {
        AudioEvent event = new AudioEvent(FOLLOW_LISTENER_ENTITY);
        event.listenerEntityId = requireEntity(entityId);
        return event;
    }

    /**
     * 恢复默认监听方式，让监听者跟随 {@code CameraSystem} 的摄像机中心。
     *
     * @return 使用摄像机监听者事件
     */
    public static AudioEvent useCameraListener() {
        return new AudioEvent(USE_CAMERA_LISTENER);
    }

    /**
     * 设置当前播放事件所属的音频总线。
     *
     * @param bus 音频总线
     * @return 当前事件，便于链式调用
     */
    public AudioEvent bus(AudioBus bus) {
        this.bus = requireBus(bus);
        return this;
    }

    /**
     * 把播放模式设为循环播放。
     * 循环声音应保存派发后产生的 {@link #handle}，以便后续停止。
     *
     * @return 当前事件，便于链式调用
     */
    public AudioEvent loop() {
        playMode = AudioPlayMode.LOOP;
        loopIntervalSeconds = 0f;
        return this;
    }

    /**
     * 把流式音乐设为带间隔的循环播放。
     * 当前音轨自然播放结束后，声音系统会等待指定时间再从头播放；
     * {@link #fadeIn(float)} 或 {@link #switchMusic(String, float)} 设置的淡入
     * 会在每次重新播放时再次生效。间隔为 0 时使用原生无缝循环。
     *
     * @param intervalSeconds 两次播放之间的静音时间，单位为秒
     * @return 当前事件，便于链式调用
     */
    public AudioEvent loop(float intervalSeconds) {
        loopIntervalSeconds = requireNonNegative(intervalSeconds,
            "intervalSeconds");
        playMode = intervalSeconds == 0f
            ? AudioPlayMode.LOOP : AudioPlayMode.INTERVAL_LOOP;
        return this;
    }

    /**
     * 设置当前实例的基础音量。
     *
     * @param volume 基础音量，范围为 0 至 1
     * @return 当前事件，便于链式调用
     */
    public AudioEvent volume(float volume) {
        this.volume = requireUnit(volume, "volume");
        return this;
    }

    /**
     * 设置短音效的播放音调和速度。
     * LibGDX 流式 {@code Music} 不支持改变音调，因此该配置主要用于
     * {@link #playSound(String)} 创建的事件。
     *
     * @param pitch 音调倍率，大于 0 且不超过 2；1 表示原始音调
     * @return 当前事件，便于链式调用
     */
    public AudioEvent pitch(float pitch) {
        if (!Float.isFinite(pitch) || pitch <= 0f || pitch > 2f) {
            throw new IllegalArgumentException("pitch must be between 0 and 2");
        }
        this.pitch = pitch;
        return this;
    }

    /**
     * 把当前声音设为空间声源，并放在固定世界坐标。
     * 系统会根据监听者的相对位置计算距离衰减和左右声像。
     *
     * @param x 声源世界横坐标
     * @param y 声源世界纵坐标
     * @return 当前事件，便于链式调用
     */
    public AudioEvent at(float x, float y) {
        this.x = x;
        this.y = y;
        spatial = true;
        sourceEntityId = -1;
        return this;
    }

    /** 为固定位置声音指定地图，避免后台地图相同坐标的声音传入当前画面。 */
    public AudioEvent map(String mapId) {
        if (mapId == null || mapId.isBlank()) {
            throw new IllegalArgumentException("audio mapId cannot be blank");
        }
        this.mapId = mapId;
        return this;
    }

    /**
     * 把当前声音设为空间声源，并持续跟随指定实体的 {@code Pos} 组件。
     * 这是设置声源跟随，不是设置监听者跟随。实体删除后，循环声音和音乐会
     * 自动停止。
     *
     * @param entityId 声源实体 ID
     * @return 当前事件，便于链式调用
     */
    public AudioEvent follow(int entityId) {
        sourceEntityId = requireEntity(entityId);
        spatial = true;
        return this;
    }

    /**
     * 设置空间声音的完整音量范围和最大传播距离。
     * 在最小距离内保持完整音量，达到最大距离后静音，中间按照衰减指数变化。
     *
     * @param minDistance 保持完整音量的距离，必须大于或等于 0
     * @param maxDistance 最大可听距离，必须大于最小距离
     * @return 当前事件，便于链式调用
     */
    public AudioEvent distances(float minDistance, float maxDistance) {
        if (!Float.isFinite(minDistance) || !Float.isFinite(maxDistance)
            || minDistance < 0f || maxDistance <= minDistance) {
            throw new IllegalArgumentException("invalid distance range");
        }
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        return this;
    }

    /**
     * 设置空间声音的距离衰减指数。
     * {@code 1} 为线性衰减，大于 1 时声音衰减更快，小于 1 时衰减更慢。
     *
     * @param rolloff 大于 0 的衰减指数
     * @return 当前事件，便于链式调用
     */
    public AudioEvent rolloff(float rolloff) {
        if (!Float.isFinite(rolloff) || rolloff <= 0f) {
            throw new IllegalArgumentException("rolloff must be greater than 0");
        }
        this.rolloff = rolloff;
        return this;
    }

    /**
     * 设置左右声像强度。
     * {@code 0} 会关闭左右偏移，{@code 1} 使用默认强度，更大的值会更快偏向
     * 左右声道，最终结果限制在 -1 至 1。
     *
     * @param strength 非负的左右声像强度
     * @return 当前事件，便于链式调用
     */
    public AudioEvent panning(float strength) {
        if (!Float.isFinite(strength) || strength < 0f) {
            throw new IllegalArgumentException("panning strength cannot be negative");
        }
        panningStrength = strength;
        return this;
    }

    /**
     * 设置新播放实例的淡入时间。实例会从静音逐渐达到 {@link #volume(float)}
     * 设置的基础音量。
     *
     * @param seconds 淡入时间，单位为秒；0 表示立即达到目标音量
     * @return 当前事件，便于链式调用
     */
    public AudioEvent fadeIn(float seconds) {
        fadeSeconds = requireNonNegative(seconds, "seconds");
        return this;
    }

    private static AudioEvent control(int type, long handle) {
        if (handle < 0L) {
            throw new IllegalArgumentException("handle cannot be negative");
        }
        AudioEvent event = new AudioEvent(type);
        event.handle = handle;
        return event;
    }

    private static AudioEvent busControl(int type, AudioBus bus) {
        AudioEvent event = new AudioEvent(type);
        event.bus = requireBus(bus);
        return event;
    }

    private static AudioBus requireBus(AudioBus bus) {
        if (bus == null) {
            throw new IllegalArgumentException("bus cannot be null");
        }
        return bus;
    }

    private static int requireEntity(int entityId) {
        if (entityId < 0) {
            throw new IllegalArgumentException("entityId cannot be negative");
        }
        return entityId;
    }

    private static float requireUnit(float value, String name) {
        if (!Float.isFinite(value) || value < 0f || value > 1f) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
        return value;
    }

    private static float requireNonNegative(float value, String name) {
        if (!Float.isFinite(value) || value < 0f) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
        return value;
    }
}
