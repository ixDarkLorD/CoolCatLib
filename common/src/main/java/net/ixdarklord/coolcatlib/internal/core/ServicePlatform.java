package net.ixdarklord.coolcatlib.internal.core;

import dev.architectury.injectables.annotations.ExpectPlatform;

public interface ServicePlatform {
    @ExpectPlatform
    static ServicePlatform get() {
        throw new UnsupportedOperationException("This method has not been implemented in the loader.");
    }

    boolean isDevelopmentEnvironment();
}
