package org.worldloom.debuff;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.worldloom.component.Debuffs;
import org.worldloom.event.DebuffEvent;
import org.worldloom.system.DebuffSystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DebuffSystemTest {
    private World world;
    private DebuffSystem system;

    @BeforeEach
    void setUp() {
        system = new DebuffSystem();
        world = new World(new WorldConfigurationBuilder()
            .with(system)
            .build());
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void appliesTicksAndExpiresEffect() {
        CounterHandler handler = new CounterHandler();
        system.registerHandler("bleeding", handler);
        int entityId = world.create();
        world.process();

        system.onEvent(DebuffEvent.add(entityId, "bleeding"));

        assertEquals(1, handler.applied);
        world.setDelta(1f);
        world.process();
        world.setDelta(1f);
        world.process();

        assertEquals(2, handler.ticks);
        assertEquals(1, handler.removed);
        Debuffs debuffs = world.getMapper(Debuffs.class).get(entityId);
        assertNotNull(debuffs);
        assertEquals(0, debuffs.effects.size);
    }

    @Test
    void reappliedEffectRefreshesItsRemainingTime() {
        CounterHandler handler = new CounterHandler();
        system.registerHandler("bleeding", handler);
        int entityId = world.create();
        world.process();

        system.onEvent(DebuffEvent.add(entityId, "bleeding"));
        world.setDelta(1f);
        world.process();
        system.onEvent(DebuffEvent.add(entityId, "bleeding"));

        DebuffState state = world.getMapper(Debuffs.class)
            .get(entityId).find("bleeding");
        assertNotNull(state);
        assertEquals(2f, state.remaining);
        assertEquals(1, handler.refreshed);
    }

    private static final class CounterHandler implements DebuffHandler {
        private int applied;
        private int ticks;
        private int removed;
        private int refreshed;

        @Override
        public float getDuration() {
            return 2f;
        }

        @Override
        public float getTickInterval() {
            return 1f;
        }

        @Override
        public void onApplied(DebuffContext context) {
            applied++;
        }

        @Override
        public void onTick(DebuffContext context) {
            ticks++;
        }

        @Override
        public void onRefreshed(DebuffContext context) {
            refreshed++;
        }

        @Override
        public void onRemoved(DebuffContext context,
                              DebuffRemovalReason reason) {
            removed++;
        }
    }
}
