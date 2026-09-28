package net.ixdarklord.coolcatcore.api.core.fabric;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.ixdarklord.coolcatcore.api.core.DataGenerationConstructor;
import net.ixdarklord.coolcatcore.api.core.DataGenerationContext;
import net.ixdarklord.coolcatcore.internal.core.fabric.ConstructorServicesImpl;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataProvider;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Runs a mod's {@link DataGenerationConstructor}s on Fabric. List it as the mod's own {@code "fabric-datagen"}
 * entrypoint, next to the constructor under {@code "coolcatcore:datagen"}:
 * <pre>{@code
 * "fabric-datagen": ["net.ixdarklord.coolcatcore.api.core.fabric.FabricDataGenerationEntrypoint"],
 * "coolcatcore:datagen": ["com.example.data.MyModData"]
 * }</pre>
 * It can also be extended to override the other {@link DataGeneratorEntrypoint} methods (registries, key order).
 */
public class FabricDataGenerationEntrypoint implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        DataGenerationContext context = new Context(generator.getModId(), generator.createPack(), generator.getRegistries());
        for (Supplier<? extends DataGenerationConstructor> factory : ConstructorServicesImpl.dataGeneration(generator.getModId())) {
            factory.get().onGatherData(context);
        }
    }

    private record Context(String modId, FabricDataGenerator.Pack pack, CompletableFuture<HolderLookup.Provider> registries) implements DataGenerationContext {
        @Override
        public String getModId() {
            return this.modId;
        }

        @Override
        public CompletableFuture<HolderLookup.Provider> getRegistries() {
            return this.registries;
        }

        @Override
        public <T extends DataProvider> T addProvider(DataProvider.Factory<T> factory) {
            return this.pack.addProvider((FabricDataGenerator.Pack.Factory<T>) factory::create);
        }

        @Override
        public <T extends DataProvider> T addProvider(RegistryDependentFactory<T> factory) {
            return this.pack.addProvider((FabricDataGenerator.Pack.RegistryDependentFactory<T>) factory::create);
        }
    }
}
