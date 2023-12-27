package net.ixdarklord.coolcat_lib.platform.services;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.nio.file.Path;

public interface IPlatformHelper {
    String getPlatformName();
    Path getConfigDir();
    boolean isModLoaded(String modId);
    boolean isDevelopmentEnvironment();
    void registerConfig();

    ResourceLocation getItemLocation(Item item);
}
