package org.worldloom.ai;

import com.artemis.Entity;
import com.badlogic.gdx.ai.btree.BehaviorTree;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class BehaviorTreeCloneTest {
    @Test
    void cloneKeepsCustomTaskAttributesWithoutSharingTaskState() {
        TimeSleep sourceTask = new TimeSleep();
        sourceTask.time = 3.5f;
        BehaviorTree<Entity> source = new BehaviorTree<>(sourceTask);

        @SuppressWarnings("unchecked")
        BehaviorTree<Entity> clone = (BehaviorTree<Entity>)source.cloneTask();
        TimeSleep clonedTask = (TimeSleep)clone.getChild(0);

        assertNotSame(source, clone);
        assertNotSame(sourceTask, clonedTask);
        assertEquals(3.5f, clonedTask.time);
    }
}
