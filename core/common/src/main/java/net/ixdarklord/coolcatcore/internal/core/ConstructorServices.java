package net.ixdarklord.coolcatcore.internal.core;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.ixdarklord.coolcatcore.api.core.DataGenerationConstructor;

import java.util.function.Supplier;

// When each loader runs the setup stages of the mod constructors (see ModConstructors).
public interface ConstructorServices {
    @ExpectPlatform
    static ConstructorServices get() {
        throw new UnsupportedOperationException("This method has not been implemented in the loader.");
    }

    void onCommonSetup(String modId, Runnable task);

    void onClientSetup(String modId, Runnable task);

    void onServerSetup(String modId, Runnable task);

    /** Keeps the factory until data is generated for the mod; it's never called otherwise. */
    void addDataGeneration(String modId, Supplier<? extends DataGenerationConstructor> factory);
}
