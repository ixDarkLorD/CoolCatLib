package net.ixdarklord.coolcatcore.internal.platform.neoforge;

import net.ixdarklord.coolcatcore.api.registry.DeferredRegister;
import net.ixdarklord.coolcatcore.api.registry.RegistryEntry;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

// NeoForge's own deferred register, hooked to the owning mod's event bus.
public final class NeoForgeDeferredRegister<T> extends DeferredRegister<T> {
    private final net.neoforged.neoforge.registries.DeferredRegister<T> register;
    private final List<RegistryEntry<? extends T>> entries = new ArrayList<>();
    private boolean hooked;

    public NeoForgeDeferredRegister(String modId, ResourceKey<? extends Registry<T>> registryKey) {
        super(modId, registryKey);
        this.register = net.neoforged.neoforge.registries.DeferredRegister.create(registryKey, modId);
    }

    @Override
    public <I extends T> RegistryEntry<I> register(String name, Supplier<? extends I> supplier) {
        Entry<T, I> entry = new Entry<>(this.register.register(name, supplier));
        this.entries.add(entry);
        return entry;
    }

    @Override
    public void register() {
        if (this.hooked) return;
        this.hooked = true;
        IEventBus bus = ModList.get().getModContainerById(this.modId).map(ModContainer::getEventBus)
                .orElseThrow(() -> new IllegalStateException("No mod container for " + this.modId + " to register " + this.registryKey.location() + " with"));
        this.register.register(bus);
    }

    @Override
    public Collection<RegistryEntry<? extends T>> getEntries() {
        return Collections.unmodifiableList(this.entries);
    }

    private record Entry<T, I extends T>(DeferredHolder<T, I> deferred) implements RegistryEntry<I> {
        @Override
        public ResourceLocation getId() {
            return this.deferred.getId();
        }

        @Override
        public I get() {
            return this.deferred.get();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Holder<I> holder() {
            return (Holder<I>) (Holder<?>) this.deferred;
        }

        @Override
        public boolean isBound() {
            return this.deferred.isBound();
        }
    }
}
