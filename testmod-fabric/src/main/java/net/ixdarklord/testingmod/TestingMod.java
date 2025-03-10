package net.ixdarklord.testingmod;

import net.fabricmc.api.ModInitializer;
import net.ixdarklord.coolcatlib.api.event.v1.server.RegisterBrewingRecipesEvent;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

public class TestingMod implements ModInitializer {
    public static String MOD_ID = "testing_mod";
    @Override
    public void onInitialize() {
        RegisterBrewingRecipesEvent.EVENT.register(event ->
                event.getBuilder().addRecipe(Ingredient.of(Items.POTION), Ingredient.of(Items.RAW_COPPER, Items.IRON_NUGGET), Items.COPPER_INGOT.getDefaultInstance()));
    }
}
