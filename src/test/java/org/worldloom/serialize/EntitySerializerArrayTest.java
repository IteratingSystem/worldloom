package org.worldloom.serialize;

import com.badlogic.gdx.utils.Array;
import org.junit.jupiter.api.Test;
import org.worldloom.component.Debuffs;
import org.worldloom.debuff.DebuffState;
import org.worldloom.manager.JsonManager;
import org.worldloom.serialize.data.Property;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class EntitySerializerArrayTest {
    @Test
    void restoresCustomArrayElementsFromGenericPropertyValue() throws Exception {
        DebuffState effect = new DebuffState();
        effect.name = "poison";
        effect.remaining = 4.5f;
        effect.tickElapsed = 0.25f;
        effect.data = "game-state";

        Property source = new Property();
        source.key = "effects";
        source.type = Array.class.getName();
        source.value = new Array<>(new DebuffState[]{effect});

        Property restored = JsonManager.fromJson(Property.class,
            JsonManager.toJson(source));
        Array<?> effects = (Array<?>) restored.value;
        Field field = Debuffs.class.getField("effects");
        EntitySerializer.restoreArrayElements(field, "effects", effects);

        DebuffState restoredEffect = assertInstanceOf(
            DebuffState.class, effects.first());
        assertEquals("poison", restoredEffect.name);
        assertEquals(4.5f, restoredEffect.remaining);
        assertEquals(0.25f, restoredEffect.tickElapsed);
        assertEquals("game-state", restoredEffect.data);
    }
}
