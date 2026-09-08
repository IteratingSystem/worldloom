package org.worldloom.ai;

import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.ai.btree.annotation.TaskAttribute;
import com.badlogic.gdx.math.MathUtils;

/**
 * @Auther WenLong
 * @Date 2025/4/10 15:50
 * @Description 时间休眠任务,在start与end之间随机一个数字作为时间进行休眠,单位秒
 **/
public class RandomSleep extends EcsLeafTask {

    //传入休眠时间(秒)
    @TaskAttribute
    public float start = 0;
    @TaskAttribute
    public float end = 1;

    private float time;
    private float timing;

    @Override
    public void start() {
        super.start();
        time = MathUtils.random(start,end);
        timing = 0;
    }

    @Override
    public Status execute() {
        timing += world.getDelta();
        if (timing >= time){
            return Status.SUCCEEDED;
        }
        return Status.RUNNING;
    }

    @Override
    protected Task<com.artemis.Entity> copyTo(Task<com.artemis.Entity> task) {
        RandomSleep copy = (RandomSleep)task;
        copy.start = start;
        copy.end = end;
        return copy;
    }
}
