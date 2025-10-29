package net.ixdarklord.coolcatlib.internal.core.forge;

import net.ixdarklord.coolcatlib.internal.core.ModPlatform;
import net.minecraftforge.fml.loading.FMLLoader;

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
        return true;
    }
}
