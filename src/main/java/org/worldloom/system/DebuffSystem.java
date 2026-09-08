package org.worldloom.system;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.worldloom.component.Debuffs;
import org.worldloom.debuff.DebuffContext;
import org.worldloom.debuff.DebuffHandler;
import org.worldloom.debuff.DebuffRemovalReason;
import org.worldloom.debuff.DebuffState;
import org.worldloom.event.DebuffEvent;

/** 按名称管理Debuff的添加、计时、周期触发和自动移除。 */
@All(Debuffs.class)
public final class DebuffSystem extends IteratingSystem {
    private static final String TAG = DebuffSystem.class.getSimpleName();
    private static final int MAX_TICKS_PER_FRAME = 32;

    private final ObjectMap<String, DebuffHandler> handlers = new ObjectMap<>();
    private ComponentMapper<Debuffs> mDebuffs;

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

    /** 查询实体是否拥有指定Debuff。 */
    public boolean has(int entityId, String name) {
        return get(entityId, name) != null;
    }

    /** 获取指定Debuff的剩余运行状态；不存在时返回null。 */
    public DebuffState get(int entityId, String name) {
        if (!isActiveEntity(entityId) || !mDebuffs.has(entityId)) {
            return null;
        }
        return mDebuffs.get(entityId).find(name);
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
        float delta = Math.max(0f, world.getDelta());
        for (int i = debuffs.effects.size - 1; i >= 0; i--) {
            DebuffState state = debuffs.effects.get(i);
            DebuffHandler handler = handlers.get(state.name);
            if (handler != null && !state.activated) {
                state.activated = true;
                handler.onApplied(context(entityId, state));
            }

            float activeDelta = state.isPermanent()
                ? delta : Math.min(delta, Math.max(0f, state.remaining));
            if (handler != null) {
                DebuffContext context = context(entityId, state);
                handler.onUpdate(context, activeDelta);
                triggerTicks(handler, context, state, activeDelta);
            }

            if (!state.isPermanent()) {
                state.remaining -= delta;
                if (state.remaining <= 0f) {
                    debuffs.effects.removeIndex(i);
                    notifyRemoved(entityId, state,
                        DebuffRemovalReason.EXPIRED);
                }
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
        DebuffState existing = debuffs.find(name);
        if (existing != null) {
            resetTimer(existing, handler);
            handler.onRefreshed(context(entityId, existing));
            return;
        }

        DebuffState created = new DebuffState();
        created.name = name;
        resetTimer(created, handler);
        debuffs.effects.add(created);
        created.activated = true;
        handler.onApplied(context(entityId, created));
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
        }
        if (tickCount == MAX_TICKS_PER_FRAME
            && state.tickElapsed >= interval) {
            state.tickElapsed %= interval;
            Gdx.app.error(TAG, "Debuff tick overflow: " + state.name);
        }
    }

    private void remove(int entityId, String name,
                        DebuffRemovalReason reason) {
        if (!mDebuffs.has(entityId)) {
            return;
        }
        Debuffs debuffs = mDebuffs.get(entityId);
        DebuffState state = debuffs.find(name);
        if (state != null) {
            debuffs.effects.removeValue(state, true);
            notifyRemoved(entityId, state, reason);
        }
    }

    private void clear(int entityId) {
        if (!mDebuffs.has(entityId)) {
            return;
        }
        Debuffs debuffs = mDebuffs.get(entityId);
        ensureEffects(debuffs);
        for (int i = debuffs.effects.size - 1; i >= 0; i--) {
            DebuffState state = debuffs.effects.removeIndex(i);
            notifyRemoved(entityId, state, DebuffRemovalReason.CLEARED);
        }
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
