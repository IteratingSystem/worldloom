package org.worldloom.ai;

import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ai.btree.annotation.TaskAttribute;
import org.worldloom.component.Pos;

/**
 * @Auther WenLong
 * @Date 2025/4/10 15:50
 * @Description 距离,用于判断目标是否在自己的radius范围中
 **/
public class Range extends EcsLeafTask {

    //对方的TAG
    @TaskAttribute
    public String targetEntityTag = "PLAYER";
    //判断距离
    @TaskAttribute
    public float radius = 0;

    @Override
    public Status execute() {
        if (!tagManager.isRegistered(targetEntityTag)) {
            Gdx.app.error(getTAG(),"entityTag is not registered:tagName:"+targetEntityTag);
            return Status.FAILED;
        }

        ComponentMapper<Pos> mPos = world.getMapper(Pos.class);
        if (!mPos.has(entity)) {
            Gdx.app.error(getTAG(),"This entity is not has 'Pos' component!Entity tag:"+entityTag);
            return Status.FAILED;
        }

        Entity target = tagManager.getEntity(targetEntityTag);
        if (!mPos.has(target)) {
            Gdx.app.error(getTAG(),"Target entity entity is not has 'Pos' component!Entity tag:"+targetEntityTag);
            return Status.FAILED;
        }

        Pos targetPos = mPos.get(target);
        Pos pos = mPos.get(entityId);
        float dst = pos.dst(targetPos);
        if (radius >= dst){
            return Status.SUCCEEDED;
        }
        return Status.FAILED;
    }

    @Override
    protected com.badlogic.gdx.ai.btree.Task<com.artemis.Entity> copyTo(
        com.badlogic.gdx.ai.btree.Task<com.artemis.Entity> task) {
        Range copy = (Range)task;
        copy.targetEntityTag = targetEntityTag;
        copy.radius = radius;
        return copy;
    }
}
