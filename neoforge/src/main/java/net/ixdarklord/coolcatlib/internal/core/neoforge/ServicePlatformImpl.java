package net.ixdarklord.coolcatlib.internal.core.neoforge;

import net.ixdarklord.coolcatlib.internal.core.ServicePlatform;
import net.neoforged.fml.loading.FMLLoader;

public class ServicePlatformImpl implements ServicePlatform {
    public static ServicePlatform get() {
        return new ServicePlatformImpl();
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }
}
