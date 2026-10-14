package net.ixdarklord.coolcatcore.internal.core.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

// The custom fabric.mod.json entrypoints that construct CoolCatLib mod constructors, so a mod needs no Fabric class:
//   "coolcatlib:common": ["com.example.MyMod"], "coolcatlib:client": ["com.example.client.MyModClient"], ...
// Each one is constructed under the id of the mod that declared it.
public final class FabricEntrypoints {
    public static final String COMMON = "coolcatlib:common";
    public static final String CLIENT = "coolcatlib:client";
    public static final String SERVER = "coolcatlib:server";
    public static final String DATA_GENERATION = "coolcatlib:datagen";

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
