package net.ixdarklord.coolcat_lib.core.fabric;

import net.fabricmc.api.ModInitializer;
import net.ixdarklord.coolcat_lib.core.CommonSetup;

public class FabricSetup implements ModInitializer {
    @Override
    public void onInitialize() {
        new CommonSetup();
    }
}
