package net.ixdarklord.coolcatcore.api.core;

import net.ixdarklord.coolcatcore.internal.core.ModConstructors;

import java.util.function.Supplier;

/**
 * A mod's client entry point, written once in common code. It's only constructed on a client, so it may use
 * client-only classes.
 *
 * <p>Hooking it up:
 * <ul>
 *     <li>Fabric: list the class under the {@code "coolcatcore:client"} entrypoint in {@code fabric.mod.json}.</li>
 *     <li>Forge: {@code client(() -> MyModClient::new)} in a {@code ForgeModEntrypoint}.</li>
 *     <li>Anywhere else: {@link #construct(String, Supplier)} from client-only code.</li>
 * </ul>
 */
public interface ClientModConstructor {
    /**
     * Constructs a mod's client entry point: {@link #onConstructMod()} runs right away, {@link #onClientSetup()} once
     * the loader is ready. Call it while mods are constructed, and only on a client.
     *
     * @throws IllegalStateException when this isn't a client
     */
    static void construct(String modId, Supplier<? extends ClientModConstructor> factory) {
        ModConstructors.constructClient(modId, factory);
    }

    /** Runs while mods are constructed: register key mappings, menu screens, tooltip components and client events. */
    default void onConstructMod() {}

    /** Runs once registries are filled, on the main thread (Forge's client setup; right after construction on Fabric). */
    default void onClientSetup() {}
}
