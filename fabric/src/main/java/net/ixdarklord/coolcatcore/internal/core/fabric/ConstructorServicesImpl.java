package net.ixdarklord.coolcatcore.internal.core.fabric;

import net.ixdarklord.coolcatcore.api.core.DataGenerationConstructor;
import net.ixdarklord.coolcatcore.internal.core.ConstructorServices;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

// Fabric has no setup stage after construction, so setup runs right away. Data generation waits for the mod's
// FabricDataGenerationEntrypoint.
public final class ConstructorServicesImpl implements ConstructorServices {
    private static final ConstructorServicesImpl INSTANCE = new ConstructorServicesImpl();
    private static final Map<String, List<Supplier<? extends DataGenerationConstructor>>> DATA_GENERATION = new HashMap<>();

    public static ConstructorServices get() {
        return INSTANCE;
    }

    @Override
    public void onCommonSetup(String modId, Runnable task) {
        task.run();
    }

    @Override
    public void onClientSetup(String modId, Runnable task) {
        task.run();
    }

    @Override
    public void onServerSetup(String modId, Runnable task) {
        task.run();
    }

    @Override
    public synchronized void addDataGeneration(String modId, Supplier<? extends DataGenerationConstructor> factory) {
        DATA_GENERATION.computeIfAbsent(modId, id -> new ArrayList<>()).add(factory);
    }

    public static synchronized List<Supplier<? extends DataGenerationConstructor>> dataGeneration(String modId) {
        return List.copyOf(DATA_GENERATION.getOrDefault(modId, List.of()));
    }
}
