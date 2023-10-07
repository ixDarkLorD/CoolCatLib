package net.ixdarklord.coolcat_lib.platform.services;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public interface IPlatformHelper {
    String getPlatformName();
    boolean isModLoaded(String modId);
    boolean isDevelopmentEnvironment();
    void registerConfig();

    ResourceLocation getItemLocation(Item item);
}
