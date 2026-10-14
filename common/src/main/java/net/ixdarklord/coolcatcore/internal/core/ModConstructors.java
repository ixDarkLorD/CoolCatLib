package net.ixdarklord.coolcatcore.internal.core;

import net.ixdarklord.coolcatcore.api.core.ClientModConstructor;
import net.ixdarklord.coolcatcore.api.core.DataGenerationConstructor;
import net.ixdarklord.coolcatcore.api.core.ModConstructor;
import net.ixdarklord.coolcatcore.api.core.ServerModConstructor;
import net.ixdarklord.coolcatcore.api.platform.Platform;

import java.util.Objects;
import java.util.function.Supplier;

// The shared half of the mod constructors: checks, construction, then the loader decides when setup runs.
public final class ModConstructors {
    private static final ConstructorServices SERVICES = ConstructorServices.get();

    private ModConstructors() {}

    public static void constructCommon(String modId, Supplier<? extends ModConstructor> factory) {
        ModConstructor constructor = create(modId, factory, "common");
        constructor.onConstructMod();
        SERVICES.onCommonSetup(modId, constructor::onCommonSetup);
    }

    public static void constructClient(String modId, Supplier<? extends ClientModConstructor> factory) {
        if (!Platform.isClient()) {
            throw new IllegalStateException("Tried to construct the client entry point of " + modId + " on a dedicated server");
        }
        ClientModConstructor constructor = create(modId, factory, "client");
        constructor.onConstructMod();
        SERVICES.onClientSetup(modId, constructor::onClientSetup);
    }

    public static void constructServer(String modId, Supplier<? extends ServerModConstructor> factory) {
        if (Platform.isClient()) {
            throw new IllegalStateException("Tried to construct the dedicated server entry point of " + modId + " on a client");
        }
        ServerModConstructor constructor = create(modId, factory, "server");
        constructor.onConstructMod();
        SERVICES.onServerSetup(modId, constructor::onServerSetup);
    }

    public static void constructDataGeneration(String modId, Supplier<? extends DataGenerationConstructor> factory) {
        checkMod(modId);
        Objects.requireNonNull(factory, "factory");
        SERVICES.addDataGeneration(modId, factory);
    }

    private static <T> T create(String modId, Supplier<? extends T> factory, String side) {
        checkMod(modId);
        CoolCatCore.LOGGER.debug("Constructing the {} entry point of {}", side, modId);
        return Objects.requireNonNull(factory.get(), () -> "The " + side + " entry point factory of " + modId + " returned null");
    }

    private static void checkMod(String modId) {
        Objects.requireNonNull(modId, "modId");
        if (!Platform.isModLoaded(modId)) {
            throw new IllegalArgumentException("No mod with the id " + modId + " is loaded");
        }
    }
}
