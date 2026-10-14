package net.ixdarklord.coolcatcore.internal.core.forge;

import net.ixdarklord.coolcatcore.api.core.DataGenerationConstructor;
import net.ixdarklord.coolcatcore.api.core.DataGenerationContext;
import net.ixdarklord.coolcatcore.internal.core.ConstructorServices;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataProvider;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

// Setup runs in Forge's setup events on the mod's own bus, queued onto the main thread; data generation in the gather
// data event (Forge has one for client and server data).
public final class ConstructorServicesImpl implements ConstructorServices {
    private static final ConstructorServicesImpl INSTANCE = new ConstructorServicesImpl();

    public static ConstructorServices get() {
        return INSTANCE;
    }

    @Override
    public void onCommonSetup(String modId, Runnable task) {
        listen(modBus(modId), FMLCommonSetupEvent.class, event -> event.enqueueWork(task));
    }

    @Override
    public void onClientSetup(String modId, Runnable task) {
        listen(modBus(modId), FMLClientSetupEvent.class, event -> event.enqueueWork(task));
    }

    @Override
    public void onServerSetup(String modId, Runnable task) {
        listen(modBus(modId), FMLDedicatedServerSetupEvent.class, event -> event.enqueueWork(task));
    }

    @Override
    public void addDataGeneration(String modId, Supplier<? extends DataGenerationConstructor> factory) {
        listen(modBus(modId), GatherDataEvent.class, event -> factory.get().onGatherData(new Context(event)));
    }

    /** The event bus of a Java mod (every Forge mod with an {@code @Mod} class). */
    public static IEventBus modBus(String modId) {
        return ModList.get().getModContainerById(modId)
                .filter(FMLModContainer.class::isInstance)
                .map(container -> ((FMLModContainer) container).getEventBus())
                .orElseThrow(() -> new IllegalStateException("No mod container with an event bus for " + modId));
    }

    /** Adds a listener for an exact event class (Forge's lambda type resolution isn't needed). */
    public static <T extends Event> void listen(IEventBus bus, Class<T> type, Consumer<T> listener) {
        bus.addListener(EventPriority.NORMAL, false, type, listener);
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
            return this.event.getGenerator().addProvider(true, factory.create(this.event.getGenerator().getPackOutput(), this.event.getLookupProvider()));
        }
    }
}
