package net.ixdarklord.coolcatlib.internal.core.forge;

import net.ixdarklord.coolcatlib.internal.core.ServicePlatform;
import net.minecraftforge.fml.loading.FMLLoader;

public class ServicePlatformImpl implements ServicePlatform {
    public static ServicePlatform get() {
        return new ServicePlatformImpl();
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }
}
