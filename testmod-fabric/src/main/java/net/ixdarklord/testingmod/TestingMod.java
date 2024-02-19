package net.ixdarklord.testingmod;

import net.fabricmc.api.ModInitializer;
import net.ixdarklord.coolcat_lib.common.crafting.CraftingHelper;

public class TestingMod implements ModInitializer {
    public static String MOD_ID = "testingmod";
    @Override
    public void onInitialize() {
        CraftingHelper.register(TestingCondition.Serializer.INSTANCE);
    }
}
