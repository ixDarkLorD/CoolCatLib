package net.ixdarklord.coolcat_lib.platform.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.ixdarklord.coolcat_lib.platform.services.IPlatformHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public class FabricPlatformHelper implements IPlatformHelper {
    @Override
    public String getPlatformName() {
        return "Fabric";
    }
    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }
    @Override
    public void registerConfig() {}

    @Override
    public ResourceLocation getItemLocation(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }
}
