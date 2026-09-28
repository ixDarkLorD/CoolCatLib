package net.ixdarklord.coolcatcore.api.core;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import java.util.concurrent.CompletableFuture;

/**
 * Where a {@link DataGenerationConstructor} adds its providers; everything lands in the mod's generated resources.
 * On Fabric the {@link PackOutput} handed to factories is a {@code FabricDataOutput}.
 */
public interface DataGenerationContext {
    /** The mod data is generated for. */
    String getModId();

    /** The registries (with the loader's additions), for providers that need them. */
    CompletableFuture<HolderLookup.Provider> getRegistries();

    /** Adds a provider built from the pack output: {@code context.addProvider(MyLanguageProvider::new)}. */
    <T extends DataProvider> T addProvider(DataProvider.Factory<T> factory);

    /** Adds a provider built from the pack output and the registries: {@code context.addProvider(MyRecipeProvider.Runner::new)}. */
    <T extends DataProvider> T addProvider(RegistryDependentFactory<T> factory);

    @FunctionalInterface
    interface RegistryDependentFactory<T extends DataProvider> {
        T create(PackOutput output, CompletableFuture<HolderLookup.Provider> registries);
    }
}
