package net.ixdarklord.coolcatlib.internal.core.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.ixdarklord.coolcatlib.internal.core.ServicePlatform;

public class ServicePlatformImpl implements ServicePlatform {
    public static ServicePlatform get() {
        return new ServicePlatformImpl();
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }
}
