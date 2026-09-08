package org.worldloom.system;

import com.artemis.annotations.Exclude;
import com.artemis.annotations.One;
import com.artemis.systems.IteratingSystem;
import net.mostlyoriginal.api.plugin.extendedcomponentmapper.M;
import org.worldloom.component.BTree;
import org.worldloom.component.Inert;

/**
 * @Auther WenLong
 * @Date 2025/4/9 16:29
 * @Description 行为树系统
 **/

@One(BTree.class)
@Exclude(Inert.class)
public class BTreeSystem extends IteratingSystem {
    private M<BTree> mBTree;

    @Override
    protected void process(int entityId) {
        BTree bTree = mBTree.get(entityId);
        if (bTree.tree != null) {
            bTree.tree.step();
        }
    }
}
