package net.ixdarklord.coolcat_lib.platform.forge;

import net.ixdarklord.coolcat_lib.platform.services.IPlatformHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.registries.ForgeRegistries;

public class ForgePlatformHelper implements IPlatformHelper {
    @Override
    public String getPlatformName() {
        return "Forge";
    }
    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get() != null && ModList.get().isLoaded(modId);
    }
    @Override
    public boolean isDevelopmentEnvironment() {
        //noinspection UnstableApiUsage
        return !FMLLoader.isProduction();
    }
    @Override
    public void registerConfig() {}

    @Override
    public ResourceLocation getItemLocation(Item item) {
        return ForgeRegistries.ITEMS.getKey(item);
    }
}
