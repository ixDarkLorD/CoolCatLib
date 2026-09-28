package net.ixdarklord.coolcatcore.internal.platform.forge;

import net.ixdarklord.coolcatcore.api.registry.DeferredRegister;
import net.ixdarklord.coolcatcore.api.registry.RegistryEntry;
import net.ixdarklord.coolcatcore.internal.core.forge.ConstructorServicesImpl;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

// Forge's own deferred register, hooked to the owning mod's event bus.
public final class ForgeDeferredRegister<T> extends DeferredRegister<T> {
    private final net.minecraftforge.registries.DeferredRegister<T> register;
    private final List<RegistryEntry<? extends T>> entries = new CopyOnWriteArrayList<>();
    private boolean hooked;

    public ForgeDeferredRegister(String modId, ResourceKey<? extends Registry<T>> registryKey) {
        super(modId, registryKey);
        this.register = net.minecraftforge.registries.DeferredRegister.create(registryKey, modId);
    }

    @Override
    public <I extends T> RegistryEntry<I> register(String name, Supplier<? extends I> supplier) {
        Entry<I> entry = new Entry<>(this.register.register(name, supplier));
        this.entries.add(entry);
        return entry;
    }

    @Override
    public synchronized void register() {
        if (this.hooked) return;
        this.hooked = true;
        this.register.register(ConstructorServicesImpl.modBus(this.modId));
    }

    @Override
    public Collection<RegistryEntry<? extends T>> getEntries() {
        return Collections.unmodifiableList(this.entries);
    }

    private record Entry<I>(RegistryObject<I> object) implements RegistryEntry<I> {
        @Override
        public ResourceLocation getId() {
            return this.object.getId();
        }

        @Override
        public I get() {
            if (!this.object.isPresent()) throw new IllegalStateException("Registry entry " + this.object.getId() + " isn't registered yet");
            return this.object.get();
        }

        // Forge only has a holder once the object is registered (or right away for registries without intrusive holders).
        @Override
        public Holder<I> holder() {
            return this.object.getHolder().orElseThrow(() -> new IllegalStateException("Registry entry " + this.object.getId() + " has no holder yet"));
        }

        @Override
        public boolean isBound() {
            return this.object.isPresent();
        }
    }
}
