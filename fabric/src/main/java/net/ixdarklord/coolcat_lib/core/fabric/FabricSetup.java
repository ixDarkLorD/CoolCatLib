package net.ixdarklord.coolcat_lib.core.fabric;

import net.fabricmc.api.ModInitializer;
import net.ixdarklord.coolcat_lib.common.crafting.ConditionalRecipe;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public class FabricSetup implements ModInitializer {
    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, ConditionalRecipe.Serializer.NAME, new ConditionalRecipe.Serializer<>());
    }
}
