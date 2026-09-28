package net.ixdarklord.testingmod;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.ixdarklord.coolcatcore.api.core.DataGenerationConstructor;
import net.ixdarklord.coolcatcore.api.core.DataGenerationContext;

public class DataGeneration implements DataGenerationConstructor {
    @Override
    public void onGatherData(DataGenerationContext context) {
        context.addProvider((output, registries) -> new RecipeGenerator((FabricPackOutput) output, registries));
    }
}
