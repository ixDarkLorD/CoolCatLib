package net.ixdarklord.coolcatcore.internal.core.forge;

import net.ixdarklord.coolcatcore.api.core.DataGenerationConstructor;
import net.ixdarklord.coolcatcore.api.core.DataGenerationContext;
import net.ixdarklord.coolcatcore.internal.core.ConstructorServices;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataProvider;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

// Setup runs in Forge's setup events on the mod's own bus, queued onto the main thread; data generation in the mod's
// gather data event.
public final class ConstructorServicesImpl implements ConstructorServices {
    private static final ConstructorServicesImpl INSTANCE = new ConstructorServicesImpl();

    public static ConstructorServices get() {
        return INSTANCE;
    }

    @Override
    public void onCommonSetup(String modId, Runnable task) {
        modBus(modId).addListener((FMLCommonSetupEvent event) -> event.enqueueWork(task));
    }

    @Override
    public void onClientSetup(String modId, Runnable task) {
        modBus(modId).addListener((FMLClientSetupEvent event) -> event.enqueueWork(task));
    }

    @Override
    public void onServerSetup(String modId, Runnable task) {
        modBus(modId).addListener((FMLDedicatedServerSetupEvent event) -> event.enqueueWork(task));
    }

    @Override
    public void addDataGeneration(String modId, Supplier<? extends DataGenerationConstructor> factory) {
        modBus(modId).addListener((GatherDataEvent event) -> factory.get().onGatherData(new Context(event)));
    }

    private static IEventBus modBus(String modId) {
        return ModList.get().getModContainerById(modId)
                .filter(FMLModContainer.class::isInstance).map(container -> ((FMLModContainer) container).getEventBus())
                .orElseThrow(() -> new IllegalStateException("No Java mod container for " + modId));
    }

    private record Context(GatherDataEvent event) implements DataGenerationContext {
        @Override
        public String getModId() {
            return this.event.getModContainer().getModId();
        }

        @Override
        public CompletableFuture<HolderLookup.Provider> getRegistries() {
            return this.event.getLookupProvider();
        }

        @Override
        public <T extends DataProvider> T addProvider(DataProvider.Factory<T> factory) {
            return this.event.getGenerator().addProvider(true, factory);
        }

        @Override
        public <T extends DataProvider> T addProvider(RegistryDependentFactory<T> factory) {
            CompletableFuture<HolderLookup.Provider> registries = this.event.getLookupProvider();
            return this.event.getGenerator().addProvider(true, (DataProvider.Factory<T>) output -> factory.create(output, registries));
        }
    }
}
