package net.ixdarklord.testingmod;

import net.ixdarklord.coolcatcore.api.core.ModConstructor;
import net.ixdarklord.coolcatcore.api.event.v1.server.RegisterBrewingRecipesEvent;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

// Constructed by CoolCatLib: Core from the "coolcatcore:common" entrypoint; no Fabric initializer needed.
public class TestingMod implements ModConstructor {
    public static String MOD_ID = "testing_mod";

    @Override
    public void onConstructMod() {
        TestConfigs.init();
        TestStartupConfig.registerContent();
        TestAttachments.init();
        RegisterBrewingRecipesEvent.EVENT.register(event ->
                event.getBuilder().addRecipe(Ingredient.of(Items.POTION), Ingredient.of(Items.RAW_COPPER, Items.IRON_NUGGET), Items.COPPER_INGOT.getDefaultInstance()));
    }
}
