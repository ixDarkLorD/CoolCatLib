package net.ixdarklord.coolcatcore.api.core;

import net.ixdarklord.coolcatcore.internal.core.ModConstructors;

import java.util.function.Supplier;

/**
 * A mod's dedicated server entry point, written once in common code. It's only constructed on a dedicated server;
 * what runs on both sides (including a client's integrated server) belongs in {@link ModConstructor}.
 *
 * <p>Hooking it up:
 * <ul>
 *     <li>Fabric: list the class under the {@code "coolcatcore:server"} entrypoint in {@code fabric.mod.json}.</li>
 *     <li>NeoForge: {@code server(() -> MyModServer::new)} in a {@code NeoForgeModEntrypoint}.</li>
 *     <li>Anywhere else: {@link #construct(String, Supplier)} from dedicated server code.</li>
 * </ul>
 */
public interface ServerModConstructor {
    /**
     * Constructs a mod's dedicated server entry point: {@link #onConstructMod()} runs right away,
     * {@link #onServerSetup()} once the loader is ready. Call it while mods are constructed, and only on a dedicated
     * server.
     *
     * @throws IllegalStateException when this isn't a dedicated server
     */
    static void construct(String modId, Supplier<? extends ServerModConstructor> factory) {
        ModConstructors.constructServer(modId, factory);
    }

    /** Runs while mods are constructed. */
    default void onConstructMod() {}

    /** Runs once registries are filled, on the main thread (NeoForge's dedicated server setup; right after construction on Fabric). */
    default void onServerSetup() {}
}
