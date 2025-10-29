package net.ixdarklord.coolcatlib.internal.core;

import dev.architectury.injectables.annotations.ExpectPlatform;

public interface ModPlatform {
    @ExpectPlatform
    static ModPlatform get() {
        throw new UnsupportedOperationException("This method has not been implemented in the loader.");
    }

    boolean isDevelopmentEnvironment();

    boolean isFabric();

    boolean isForge();

    boolean isNeoforge();

    boolean isForgeLike();
}
