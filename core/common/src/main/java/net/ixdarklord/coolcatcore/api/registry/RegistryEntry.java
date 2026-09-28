package net.ixdarklord.coolcatcore.api.registry;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

/**
 * An object registered through a {@link DeferredRegister}, available once its registry has been filled.
 *
 * @param <T> the registered object's type
 */
public interface RegistryEntry<T> extends Supplier<T> {
    /** The object's id. */
    Identifier getId();

    /**
     * The object.
     *
     * @throws IllegalStateException before it's registered
     */
    @Override
    T get();

    /** The object's registry holder. */
    Holder<T> holder();

    /** Whether the object has been registered yet. */
    boolean isBound();
}
