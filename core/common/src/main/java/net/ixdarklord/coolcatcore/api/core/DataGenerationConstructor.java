package net.ixdarklord.coolcatcore.api.core;

import net.ixdarklord.coolcatcore.internal.core.ModConstructors;

import java.util.function.Supplier;

/**
 * A mod's data generation entry point, written once in common code. It's only created when data is generated, so
 * providers may use client-only classes (models...).
 *
 * <p>Hooking it up:
 * <ul>
 *     <li>Fabric: list the class under the {@code "coolcatcore:datagen"} entrypoint, and
 *     {@code net.ixdarklord.coolcatcore.api.core.fabric.FabricDataGenerationEntrypoint} under {@code "fabric-datagen"}.</li>
 *     <li>Forge: {@code dataGeneration(MyModData::new)} in a {@code ForgeModEntrypoint}.</li>
 *     <li>Anywhere else: {@link #construct(String, Supplier)} while mods are constructed.</li>
 * </ul>
 */
@FunctionalInterface
public interface DataGenerationConstructor {
    /**
     * Registers a mod's data generation entry point. The factory is only called when data is generated for that mod.
     */
    static void construct(String modId, Supplier<? extends DataGenerationConstructor> factory) {
        ModConstructors.constructDataGeneration(modId, factory);
    }

    /** Adds the mod's data providers. */
    void onGatherData(DataGenerationContext context);
}
