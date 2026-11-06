package org.worldloom.internal;

import com.artemis.InvocationStrategy;
import com.artemis.World;
import com.artemis.WorldConfiguration;

/** 沿用Artemis默认更新顺序，同时在构造器返回前持有可回收的世界。 */
public final class WorldConstruction extends InvocationStrategy {
    /** 仅由构建入口创建，避免外部接管未初始化完成的世界。 */
    private WorldConstruction() { }

    /** 注入或系统初始化失败时回收世界；清理失败附加到原始异常，不替换失败原因。 */
    public static World create(WorldConfiguration configuration) {
        WorldConstruction construction = new WorldConstruction();
        configuration.setInvocationStrategy(construction);
        try {
            return new World(configuration);
        } catch (RuntimeException | Error failure) {
            if (construction.world != null) {
                try { construction.world.dispose(); }
                catch (RuntimeException | Error cleanupFailure) {
                    if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }
}
