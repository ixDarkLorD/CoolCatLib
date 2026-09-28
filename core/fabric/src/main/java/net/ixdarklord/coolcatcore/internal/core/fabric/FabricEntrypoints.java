package net.ixdarklord.coolcatcore.internal.core.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

// The custom fabric.mod.json entrypoints that construct CoolCatCore mod constructors, so a mod needs no Fabric class:
//   "coolcatcore:common": ["com.example.MyMod"], "coolcatcore:client": ["com.example.client.MyModClient"], ...
// Each one is constructed under the id of the mod that declared it.
public final class FabricEntrypoints {
    public static final String COMMON = "coolcatcore:common";
    public static final String CLIENT = "coolcatcore:client";
    public static final String SERVER = "coolcatcore:server";
    public static final String DATA_GENERATION = "coolcatcore:datagen";

    private FabricEntrypoints() {}

    public static <T> void construct(String key, Class<T> type, BiConsumer<String, Supplier<T>> constructor) {
        for (EntrypointContainer<T> container : FabricLoader.getInstance().getEntrypointContainers(key, type)) {
            String modId = container.getProvider().getMetadata().getId();
            try {
                constructor.accept(modId, container::getEntrypoint);
            } catch (RuntimeException | LinkageError e) {
                throw new RuntimeException("Could not construct the " + key + " entrypoint of " + modId, e);
            }
        }
    }
}
