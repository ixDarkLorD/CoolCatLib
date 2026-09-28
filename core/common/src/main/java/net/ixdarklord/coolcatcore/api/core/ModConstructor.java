package net.ixdarklord.coolcatcore.api.core;

import net.ixdarklord.coolcatcore.internal.core.ModConstructors;

import java.util.function.Supplier;

/**
 * A mod's common entry point, written once in common code and run the same way on every loader. It's constructed on
 * both sides (client and dedicated server).
 *
 * <p>Hooking it up:
 * <ul>
 *     <li>Fabric: list the class under the {@code "coolcatcore:common"} entrypoint in {@code fabric.mod.json}; no
 *     Fabric class is needed.</li>
 *     <li>NeoForge: extend {@code NeoForgeModEntrypoint} in the {@code @Mod} class and call {@code common(MyMod::new)}.</li>
 *     <li>Anywhere else: {@link #construct(String, Supplier)} from the loader's own entry point.</li>
 * </ul>
 */
public interface ModConstructor {
    /**
     * Constructs a mod's common entry point: {@link #onConstructMod()} runs right away, {@link #onCommonSetup()} once
     * the loader is ready. Call it while mods are constructed.
     */
    static void construct(String modId, Supplier<? extends ModConstructor> factory) {
        ModConstructors.constructCommon(modId, factory);
    }

    /**
     * Runs while mods are constructed: create and {@code register()} deferred registers, register payloads, events,
     * configs and reload listeners.
     */
    default void onConstructMod() {}

    /** Runs once registries are filled, on the main thread (NeoForge's common setup; right after construction on Fabric). */
    default void onCommonSetup() {}
}
