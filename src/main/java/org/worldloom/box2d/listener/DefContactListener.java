package org.worldloom.box2d.listener;

import com.badlogic.gdx.physics.box2d.*;
import com.artemis.utils.Bag;
import org.worldloom.box2d.DefFixData;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @Auther WenLong
 * @Date 2025/3/27 16:47
 * @Description 自定义碰撞监听,用于初始化给Box2D的World,实现的功能是触发每个形状给定的监听器逻辑
 **/
public class DefContactListener implements ContactListener {
    // 只保存仍在接触的夹具；链条可能让同一对夹具产生多个接触，因此按次数维护。
    private final Map<Fixture, Map<Fixture, Integer>> touching = new HashMap<>();

    /** 将夹具当前接触的其它夹具追加到调用方集合，不保留原生Contact。 */
    public void collectContacts(Fixture fixture, Bag<Fixture> output) {
        Map<Fixture, Integer> neighbours = touching.get(fixture);
        if (neighbours != null) for (Fixture neighbour : neighbours.keySet()) output.add(neighbour);
    }

    /** 更新单向接触计数，结束最后一个接触时解除全部夹具引用。 */
    private void changeContact(Fixture fixture, Fixture other, int delta) {
        Map<Fixture, Integer> neighbours = touching.get(fixture);
        if (neighbours == null) {
            if (delta < 0) return;
            neighbours = new LinkedHashMap<>();
            touching.put(fixture, neighbours);
        }
        int count = neighbours.getOrDefault(other, 0) + delta;
        if (count > 0) neighbours.put(other, count);
        else neighbours.remove(other);
        if (neighbours.isEmpty()) touching.remove(fixture);
    }

    /** 物理世界销毁后解除索引引用，不访问已经释放的原生夹具。 */
    public void clearContacts() { touching.clear(); }

    /**
     * 碰撞前处理
     * @param contact
     * @param manifold
     */
    @Override
    public void preSolve(Contact contact, Manifold manifold) {
        Fixture fixtureA = contact.getFixtureA();
        Fixture fixtureB = contact.getFixtureB();
        Object userDataA = fixtureA.getUserData();
        Object userDataB = fixtureB.getUserData();
        if (userDataA instanceof DefFixData defFixData) {
            if (defFixData.listener != null) {
                defFixData.listener.preSolve(contact,manifold);
            }
        }
        if (userDataB instanceof DefFixData defFixData) {
            if (defFixData.listener != null) {
                defFixData.listener.preSolve(contact,manifold);
            }
        }
    }

    /** 先登记双向接触，再通知夹具业务监听器。 */
    @Override
    public void beginContact(Contact contact) {
        Fixture fixtureA = contact.getFixtureA();
        Fixture fixtureB = contact.getFixtureB();
        changeContact(fixtureA, fixtureB, 1);
        changeContact(fixtureB, fixtureA, 1);
        Object userDataA = fixtureA.getUserData();
        Object userDataB = fixtureB.getUserData();
        if (userDataA instanceof DefFixData defFixData) {
            if (defFixData.listener != null) {
                defFixData.listener.beginContact(contact);
            }
        }
        if (userDataB instanceof DefFixData defFixData) {
            if (defFixData.listener != null) {
                defFixData.listener.beginContact(contact);
            }
        }
    }

    /** 先移除结束接触，再通知业务；销毁夹具也会沿用Box2D的结束回调。 */
    @Override
    public void endContact(Contact contact) {
        Fixture fixtureA = contact.getFixtureA();
        Fixture fixtureB = contact.getFixtureB();
        changeContact(fixtureA, fixtureB, -1);
        changeContact(fixtureB, fixtureA, -1);
        Object userDataA = fixtureA.getUserData();
        Object userDataB = fixtureB.getUserData();
        if (userDataA instanceof DefFixData defFixData) {
            if (defFixData.listener != null) {
                defFixData.listener.endContact(contact);
            }
        }
        if (userDataB instanceof DefFixData defFixData) {
            if (defFixData.listener != null) {
                defFixData.listener.endContact(contact);
            }
        }
    }


    @Override
    public void postSolve(Contact contact, ContactImpulse contactImpulse) {
        Fixture fixtureA = contact.getFixtureA();
        Fixture fixtureB = contact.getFixtureB();
        Object userDataA = fixtureA.getUserData();
        Object userDataB = fixtureB.getUserData();
        if (userDataA instanceof DefFixData defFixData) {
            if (defFixData.listener != null) {
                defFixData.listener.postSolve(contact,contactImpulse);
            }
        }
        if (userDataB instanceof DefFixData defFixData) {
            if (defFixData.listener != null) {
                defFixData.listener.postSolve(contact,contactImpulse);
            }
        }
    }
}
