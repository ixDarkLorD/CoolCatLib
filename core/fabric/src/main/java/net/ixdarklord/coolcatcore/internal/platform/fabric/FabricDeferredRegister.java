package net.ixdarklord.coolcatcore.internal.platform.fabric;

import net.ixdarklord.coolcatcore.api.registry.DeferredRegister;
import net.ixdarklord.coolcatcore.api.registry.RegistryEntry;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

// Fabric registers straight into the registry, so register() does the work (and later additions go in at once).
public final class FabricDeferredRegister<T> extends DeferredRegister<T> {
    private final List<Entry<? extends T>> entries = new ArrayList<>();
    private @Nullable Registry<T> registry;

    public FabricDeferredRegister(String modId, ResourceKey<? extends Registry<T>> registryKey) {
        super(modId, registryKey);
    }

    @Override
    public <I extends T> RegistryEntry<I> register(String name, Supplier<? extends I> supplier) {
        Entry<I> entry = new Entry<>(ResourceLocation.fromNamespaceAndPath(this.modId, name), supplier);
        this.entries.add(entry);
        if (this.registry != null) entry.bind(this.registry, this.registryKey);
        return entry;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void register() {
        if (this.registry != null) return;
        this.registry = (Registry<T>) Objects.requireNonNull(BuiltInRegistries.REGISTRY.get(this.registryKey.location()),
                () -> "Unknown registry " + this.registryKey.location());
        for (Entry<? extends T> entry : this.entries) entry.bind(this.registry, this.registryKey);
    }

    @Override
    public Collection<RegistryEntry<? extends T>> getEntries() {
        return Collections.unmodifiableList(this.entries);
    }

    private static final class Entry<I> implements RegistryEntry<I> {
        private final ResourceLocation id;
        private final Supplier<? extends I> supplier;
        private @Nullable Holder<I> holder;

        private Entry(ResourceLocation id, Supplier<? extends I> supplier) {
            this.id = id;
            this.supplier = supplier;
        }

        @SuppressWarnings("unchecked")
        private <T> void bind(Registry<T> registry, ResourceKey<? extends Registry<T>> registryKey) {
            if (this.holder != null) return;
            T value = (T) this.supplier.get();
            ResourceKey<T> key = ResourceKey.create(registryKey, this.id);
            this.holder = (Holder<I>) Registry.registerForHolder(registry, key, value);
        }

        @Override
        public ResourceLocation getId() {
            return this.id;
        }

        @Override
        public I get() {
            if (this.holder == null) throw new IllegalStateException("Registry entry " + this.id + " isn't registered yet");
            return this.holder.value();
        }

        @Override
        public Holder<I> holder() {
            if (this.holder == null) throw new IllegalStateException("Registry entry " + this.id + " isn't registered yet");
            return this.holder;
        }

        @Override
        public boolean isBound() {
            return this.holder != null;
        }
    }
}
