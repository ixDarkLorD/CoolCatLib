package net.ixdarklord.coolcatlib.internal.core.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.ixdarklord.coolcatlib.internal.core.ModPlatform;

public class ModPlatformImpl implements ModPlatform {
    public static ModPlatform get() {
        return new ModPlatformImpl();
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public boolean isFabric() {
        return true;
    }

    @Override
    public boolean isForge() {
        return false;
    }

    @Override
    public boolean isNeoforge() {
        return false;
    }

    @Override
    public boolean isForgeLike() {
        return false;
    }
}
