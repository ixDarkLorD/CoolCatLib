package net.ixdarklord.coolcatcore.api.registry;

import net.ixdarklord.coolcatcore.internal.platform.CommonServices;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * Registers a mod's objects into one registry at the right time on each loader.
 * <pre>{@code
 * public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(MOD_ID, Registries.ITEM);
 * public static final RegistryEntry<Item> WAND = ITEMS.register("wand", () -> new Item(...));
 * // during mod initialisation:
 * ITEMS.register();
 * }</pre>
 *
 * @param <T> the registry's type
 */
public abstract class DeferredRegister<T> {
    protected final String modId;
    protected final ResourceKey<? extends Registry<T>> registryKey;

    protected DeferredRegister(String modId, ResourceKey<? extends Registry<T>> registryKey) {
        this.modId = modId;
        this.registryKey = registryKey;
    }

    public static <T> DeferredRegister<T> create(String modId, ResourceKey<? extends Registry<T>> registryKey) {
        return CommonServices.get().createDeferredRegister(modId, registryKey);
    }

    /**
     * Adds an object, created when the registry is filled, under {@code modId:name}.
     */
    public abstract <I extends T> RegistryEntry<I> register(String name, Supplier<? extends I> supplier);

    /**
     * Hooks this register into the loader. Call once, while the mod initialises; objects added afterwards are still
     * registered on Fabric, but not on NeoForge.
     */
    public abstract void register();

    /** Everything added so far. */
    public abstract Collection<RegistryEntry<? extends T>> getEntries();

    public String getModId() {
        return this.modId;
    }

    public ResourceKey<? extends Registry<T>> getRegistryKey() {
        return this.registryKey;
    }
}
