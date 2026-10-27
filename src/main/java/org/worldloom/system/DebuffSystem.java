package org.worldloom.system;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.worldloom.component.Debuffs;
import org.worldloom.debuff.DebuffContext;
import org.worldloom.debuff.DebuffHandler;
import org.worldloom.debuff.DebuffRemovalReason;
import org.worldloom.debuff.DebuffState;
import org.worldloom.event.DebuffEvent;

/** 按名称管理Debuff的添加、计时、周期触发和自动移除。 */
@org.worldloom.serialize.SerializeSystem
@All(Debuffs.class)
public final class DebuffSystem extends IteratingSystem implements org.worldloom.serialize.SystemSnapshotLifecycle {
    private static final String TAG = DebuffSystem.class.getSimpleName();
    private static final int MAX_TICKS_PER_FRAME = 32;

    private final ObjectMap<String, DebuffHandler> handlers = new ObjectMap<>();
    private final IntMap<Array<DebuffState>> runtimeStates = new IntMap<>();
    private final Array<DebuffState> frameStates = new Array<>();
    private ComponentMapper<Debuffs> mDebuffs;
    @org.worldloom.serialize.SerializeParam
    public Array<EffectSnapshot> savedEffects = new Array<>();
    private boolean pendingRestore;

    /** 效果存档记录仅在保存/恢复边界使用，按稳定实体身份关联。 */
    public static final class EffectSnapshot {
        public String entityKey;
        public String name;
        public float remaining;
        public float tickElapsed;
        public String data;
    }

    /** 只捕获真实计时状态，不在每帧生成另一份权威数据。 */
    @Override public void beforeSnapshot() {
        Array<EffectSnapshot> result = new Array<>();
        for (IntMap.Entry<Array<DebuffState>> entry : runtimeStates) {
            org.worldloom.component.PersistentIdentity identity = world.getMapper(org.worldloom.component.PersistentIdentity.class).get(entry.key);
            if (identity == null) continue;
            for (DebuffState state : entry.value) {
                EffectSnapshot item = new EffectSnapshot();
                item.entityKey = identity.entityKey; item.name = state.name;
                item.remaining = state.remaining; item.tickElapsed = state.tickElapsed; item.data = state.data;
                result.add(item);
            }
        }
        savedEffects = result;
    }

    /** 等实体加载屏障结束后再解析稳定身份，避免初始化阶段寻找半成品实体。 */
    @Override public void afterSnapshotRestore() { pendingRestore = true; }

    /** 恢复周期余量与剩余时间，不重放首次施加副作用。 */
    @Override protected void begin() {
        if (!pendingRestore) return;
        pendingRestore = false;
        runtimeStates.clear();
        if (savedEffects == null) return;
        MapIdentitySystem identities = world.getSystem(MapIdentitySystem.class);
        for (EffectSnapshot item : savedEffects) {
            int id = identities.resolve(item.entityKey);
            if (id < 0 || !mDebuffs.has(id) || !mDebuffs.get(id).has(item.name)) continue;
            if (!Float.isFinite(item.remaining) || !Float.isFinite(item.tickElapsed) || item.tickElapsed < 0)
                throw new IllegalArgumentException("无效的效果计时快照");
            DebuffHandler handler = handlers.get(item.name);
            if (handler == null) continue;
            DebuffState state = new DebuffState();
            state.name = item.name; state.remaining = item.remaining; state.tickElapsed = item.tickElapsed;
            state.data = item.data == null ? "" : item.data; state.activated = true;
            states(id).add(state);
            handler.onRestored(context(id, state));
        }
        savedEffects = new Array<>();
    }

    /** 注册具体Debuff定义；持续时间、周期和行为均由定义自身提供。 */
    public void registerHandler(String name, DebuffHandler handler) {
        validateName(name);
        if (handler == null) {
            throw new IllegalArgumentException("handler cannot be null");
        }
        if (handlers.containsKey(name)) {
            throw new IllegalArgumentException(
                "debuff handler is already registered: " + name);
        }
        handlers.put(name, handler);
    }

    /** 查询实体是否拥有指定Debuff名称。 */
    public boolean has(int entityId, String name) {
        return isActiveEntity(entityId) && mDebuffs.has(entityId)
            && mDebuffs.get(entityId).has(name);
    }

    /** 获取当前效果的真实计时状态，切图及读档保留剩余时间。 */
    public DebuffState get(int entityId, String name) {
        if (!has(entityId, name)) {
            return null;
        }
        return findState(runtimeStates.get(entityId), name);
    }

    @Subscribe
    public void onEvent(DebuffEvent event) {
        if (event == null || !isActiveEntity(event.entityId)) {
            return;
        }
        switch (event.type) {
            case DebuffEvent.APPLY -> add(event.entityId, event.name);
            case DebuffEvent.REMOVE -> remove(event.entityId, event.name,
                DebuffRemovalReason.REMOVED);
            case DebuffEvent.CLEAR -> clear(event.entityId);
            default -> Gdx.app.error(TAG,
                "Unknown debuff event type: " + event.type);
        }
    }

    @Override
    protected void process(int entityId) {
        Debuffs debuffs = mDebuffs.get(entityId);
        ensureEffects(debuffs);
        Array<DebuffState> states = states(entityId);
        synchronize(entityId, debuffs, states);

        float delta = Math.max(0f, world.getDelta());
        frameStates.clear(); frameStates.addAll(states);
        for (int i = frameStates.size - 1; i >= 0; i--) {
            DebuffState state = frameStates.get(i);
            if (!states.contains(state, true) || !debuffs.has(state.name)) continue;
            DebuffHandler handler = handlers.get(state.name);
            if (handler != null && !handler.shouldUpdate(context(entityId, state))) continue;
            if (handler != null && !state.activated) {
                state.activated = true;
                handler.onApplied(context(entityId, state));
            }
            if (!states.contains(state, true) || !debuffs.has(state.name)) continue;

            float activeDelta = state.isPermanent()
                ? delta : Math.min(delta, Math.max(0f, state.remaining));
            if (handler != null) {
                DebuffContext context = context(entityId, state);
                handler.onUpdate(context, activeDelta);
                if (!states.contains(state, true) || !debuffs.has(state.name)) continue;
                triggerTicks(handler, context, state, activeDelta);
            }

            if (!states.contains(state, true) || !debuffs.has(state.name)) continue;
            if (!state.isPermanent()) {
                state.remaining = Math.max(0, state.remaining - delta);
                float interval = handler == null ? 0 : handler.getTickInterval();
                if (state.remaining <= 0f && (interval <= 0 || state.tickElapsed < interval)) {
                    states.removeValue(state, true);
                    debuffs.effects.removeValue(state.name, false);
                    notifyRemoved(entityId, state,
                        DebuffRemovalReason.EXPIRED);
                }
            }
        }
    }

    @Override
    protected void removed(int entityId) {
        runtimeStates.remove(entityId);
    }

    private void synchronize(int entityId, Debuffs debuffs,
                             Array<DebuffState> states) {
        for (int i = states.size - 1; i >= 0; i = Math.min(i - 1, states.size - 1)) {
            DebuffState state = states.get(i);
            if (!debuffs.has(state.name)) {
                states.removeIndex(i);
                notifyRemoved(entityId, state, DebuffRemovalReason.REMOVED);
            }
        }
        for (String name : debuffs.effects) {
            if (name == null || name.isBlank()
                || findState(states, name) != null) {
                continue;
            }
            DebuffHandler handler = handlers.get(name);
            if (handler != null) {
                states.add(createState(name, handler));
            }
        }
    }

    private void add(int entityId, String name) {
        validateName(name);
        DebuffHandler handler = handlers.get(name);
        if (handler == null) {
            Gdx.app.error(TAG, "Debuff handler is not registered: " + name);
            return;
        }

        Debuffs debuffs = mDebuffs.has(entityId)
            ? mDebuffs.get(entityId) : mDebuffs.create(entityId);
        ensureEffects(debuffs);
        if (!debuffs.effects.contains(name, false)) {
            debuffs.effects.add(name);
        }

        Array<DebuffState> states = states(entityId);
        DebuffState existing = findState(states, name);
        if (existing != null) {
            resetTimer(existing, handler);
            handler.onRefreshed(context(entityId, existing));
            return;
        }

        DebuffState created = createState(name, handler);
        states.add(created);
        created.activated = true;
        handler.onApplied(context(entityId, created));
    }

    private DebuffState createState(String name, DebuffHandler handler) {
        DebuffState state = new DebuffState();
        state.name = name;
        resetTimer(state, handler);
        return state;
    }

    private void resetTimer(DebuffState state, DebuffHandler handler) {
        float duration = handler.getDuration();
        state.remaining = duration <= 0f ? -1f : duration;
        state.tickElapsed = 0f;
    }

    private void triggerTicks(DebuffHandler handler, DebuffContext context,
                              DebuffState state, float delta) {
        float interval = handler.getTickInterval();
        if (interval <= 0f) {
            return;
        }
        state.tickElapsed += delta;
        int tickCount = 0;
        while (state.tickElapsed >= interval
            && tickCount < MAX_TICKS_PER_FRAME) {
            state.tickElapsed -= interval;
            handler.onTick(context);
            tickCount++;
            if (!has(context.getEntityId(), state.name) || !states(context.getEntityId()).contains(state, true)) break;
        }

    }

    private void remove(int entityId, String name,
                        DebuffRemovalReason reason) {
        if (!mDebuffs.has(entityId)) {
            return;
        }
        Debuffs debuffs = mDebuffs.get(entityId);
        ensureEffects(debuffs);
        debuffs.effects.removeValue(name, false);
        Array<DebuffState> states = runtimeStates.get(entityId);
        DebuffState state = findState(states, name);
        if (state != null) {
            states.removeValue(state, true);
            notifyRemoved(entityId, state, reason);
        }
    }

    private void clear(int entityId) {
        if (!mDebuffs.has(entityId)) {
            return;
        }
        Debuffs debuffs = mDebuffs.get(entityId);
        ensureEffects(debuffs);
        debuffs.effects.clear();
        Array<DebuffState> states = runtimeStates.get(entityId);
        if (states == null) {
            return;
        }
        Array<DebuffState> removed = new Array<>(states);
        states.clear();
        for (DebuffState state : removed) notifyRemoved(entityId, state, DebuffRemovalReason.CLEARED);
    }

    private void notifyRemoved(int entityId, DebuffState state,
                               DebuffRemovalReason reason) {
        DebuffHandler handler = handlers.get(state.name);
        if (handler != null) {
            handler.onRemoved(context(entityId, state), reason);
        }
    }

    private DebuffContext context(int entityId, DebuffState state) {
        return new DebuffContext(world, entityId, state);
    }

    private Array<DebuffState> states(int entityId) {
        Array<DebuffState> states = runtimeStates.get(entityId);
        if (states == null) {
            states = new Array<>();
            runtimeStates.put(entityId, states);
        }
        return states;
    }

    private DebuffState findState(Array<DebuffState> states, String name) {
        if (states == null) {
            return null;
        }
        for (DebuffState state : states) {
            if (state.name.equals(name)) {
                return state;
            }
        }
        return null;
    }

    private void ensureEffects(Debuffs debuffs) {
        if (debuffs.effects == null) {
            debuffs.effects = new Array<>();
        }
    }

    private boolean isActiveEntity(int entityId) {
        return entityId >= 0 && world.getEntityManager().isActive(entityId);
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("debuff name cannot be blank");
        }
    }
}
