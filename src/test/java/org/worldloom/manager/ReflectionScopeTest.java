package org.worldloom.manager;

import com.artemis.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.worldloom.component.Pos;
import org.worldloom.reflectionfixture.BTree;
import org.worldloom.reflectionfixture.ScopeRoot;
import org.worldloom.reflectionfixture.sub.ChildComponent;
import org.worldloom.reflectionfixture.sub.EngineInheritedComponent;
import org.worldloom.reflectionfixtureextra.ForeignComponent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 所有测试夹具包共用一个代码来源地址，模拟 IDE 编译输出中的旧类残留。 */
class ReflectionScopeTest {
    private Object previousRoot;
    private ReflectionManager reflections;

    @BeforeEach void createIsolatedManager() throws Exception {
        Field root = ReflectionManager.class.getDeclaredField("ROOT_CLASS_GAME");
        root.setAccessible(true); previousRoot = root.get(null);
        ReflectionManager.setRootClass(ScopeRoot.class);
        Constructor<ReflectionManager> constructor = ReflectionManager.class.getDeclaredConstructor();
        constructor.setAccessible(true); reflections = constructor.newInstance();
    }

    @AfterEach void restoreRoot() throws Exception {
        Field root = ReflectionManager.class.getDeclaredField("ROOT_CLASS_GAME");
        root.setAccessible(true); root.set(null, previousRoot);
    }

    @Test void sharedOutput_staleSameNamedComponentCannotPolluteGameDiscovery() {
        assertEquals(ScopeRoot.class.getProtectionDomain().getCodeSource().getLocation(),
            org.ltae.component.BTree.class.getProtectionDomain().getCodeSource().getLocation());
        Set<Class<? extends Component>> found = reflections.getSubTypesOfWithGame(Component.class);
        assertEquals(Set.of(BTree.class, ChildComponent.class, EngineInheritedComponent.class), found);
        assertFalse(found.contains(org.ltae.component.BTree.class));
    }

    @Test void packageBoundary_doesNotIncludeSimilarlyPrefixedSibling() {
        assertFalse(reflections.getReflections(ScopeRoot.class)
            .getSubTypesOf(Component.class).contains(ForeignComponent.class));
    }

    @Test void legalSubpackage_keepsDirectAndInheritedComponents() {
        Set<Class<? extends Component>> found = reflections.getSubTypesOfWithGame(Component.class);
        assertTrue(found.contains(BTree.class));
        assertTrue(found.contains(ChildComponent.class));
    }

    @Test void gameComponentWithEngineParent_remainsDiscoverable() {
        assertTrue(reflections.getSubTypesOfWithGame(Component.class).contains(EngineInheritedComponent.class));
    }

    @Test void combinedQuery_keepsEngineAndGameWhileExcludingExternalPackages() {
        Set<Class<? extends Component>> found = reflections.getSubTypesOfWithEngineAndGame(Component.class);
        assertTrue(found.contains(Pos.class));
        assertTrue(found.contains(BTree.class));
        assertFalse(found.contains(org.ltae.component.BTree.class));
        assertFalse(found.contains(ForeignComponent.class));
    }

    @Test void differentRootsInSameOutput_keepTheirOwnCachedScopes() {
        reflections.getReflections(ScopeRoot.class);
        Set<Class<? extends Component>> legacy = reflections.getReflections(org.ltae.component.BTree.class)
            .getSubTypesOf(Component.class);
        assertEquals(Set.of(org.ltae.component.BTree.class), legacy);
        assertEquals(Set.of(BTree.class, ChildComponent.class, EngineInheritedComponent.class),
            reflections.getSubTypesOfWithGame(Component.class));
    }
}
