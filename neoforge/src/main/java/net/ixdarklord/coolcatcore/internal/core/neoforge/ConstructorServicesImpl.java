package net.ixdarklord.coolcatcore.internal.core.neoforge;

import net.ixdarklord.coolcatcore.api.core.DataGenerationConstructor;
import net.ixdarklord.coolcatcore.api.core.DataGenerationContext;
import net.ixdarklord.coolcatcore.internal.core.ConstructorServices;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataProvider;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

// Setup runs in NeoForge's setup events on the mod's own bus, queued onto the main thread; data generation in the
// mod's gather data event.
public final class ConstructorServicesImpl implements ConstructorServices {
    private static final ConstructorServicesImpl INSTANCE = new ConstructorServicesImpl();

    public static ConstructorServices get() {
        return INSTANCE;
    }

    @Override
    public void onCommonSetup(String modId, Runnable task) {
        modBus(modId).addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(task));
    }

    @Override
    public void onClientSetup(String modId, Runnable task) {
        modBus(modId).addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(task));
    }

    @Override
    public void onServerSetup(String modId, Runnable task) {
        modBus(modId).addListener(FMLDedicatedServerSetupEvent.class, event -> event.enqueueWork(task));
    }

    @Override
    public void addDataGeneration(String modId, Supplier<? extends DataGenerationConstructor> factory) {
        modBus(modId).addListener(GatherDataEvent.class, event -> factory.get().onGatherData(new Context(event)));
    }

    private static IEventBus modBus(String modId) {
        return ModList.get().getModContainerById(modId).map(ModContainer::getEventBus)
                .orElseThrow(() -> new IllegalStateException("No mod container for " + modId));
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
            return this.event.createProvider((GatherDataEvent.DataProviderFromOutput<T>) factory::create);
        }

        @Override
        public <T extends DataProvider> T addProvider(RegistryDependentFactory<T> factory) {
            return this.event.createProvider((GatherDataEvent.DataProviderFromOutputLookup<T>) factory::create);
        }
    }
}
