package net.ixdarklord.coolcatlib.internal.core.neoforge;

import net.ixdarklord.coolcatlib.internal.core.ModPlatform;
import net.neoforged.fml.loading.FMLLoader;

public class ModPlatformImpl implements ModPlatform {
    public static ModPlatform get() {
        return new ModPlatformImpl();
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }

    @Override
    public boolean isFabric() {
        return false;
    }

    @Override
    public boolean isForge() {
        return false;
    }

    @Override
    public boolean isNeoforge() {
        return true;
    }

    @Override
    public boolean isForgeLike() {
        return true;
    }
}
