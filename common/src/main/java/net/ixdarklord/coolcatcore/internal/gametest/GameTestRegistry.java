package net.ixdarklord.coolcatcore.internal.gametest;

import net.ixdarklord.coolcatcore.api.gametest.GameTest;
import net.ixdarklord.coolcatcore.api.gametest.GameTestOptions;
import net.ixdarklord.coolcatcore.api.platform.Platform;
import net.ixdarklord.coolcatcore.api.registry.DeferredRegister;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestEnvironments;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

// The registered game tests (api.gametest.GameTests): their functions go in the test function registry as each mod's
// objects do (always, so client and server agree), and when tests are enabled, a test instance each is added to the
// test instance registry as a server loads its data (RegistryLoadTaskMixin), from where /test and test servers run them.
public final class GameTestRegistry {
    private static final Map<Identifier, Test> TESTS = new LinkedHashMap<>();
    private static final Map<String, DeferredRegister<Consumer<GameTestHelper>>> FUNCTIONS = new HashMap<>();
    // A test with no environment of its own runs in vanilla's "minecraft:default" (none), from the server's environment
    // registry, which is loaded and frozen before the tests'; written out if a data pack took it away.
    private static final Holder<TestEnvironmentDefinition<?>> NO_ENVIRONMENT = Holder.direct(new TestEnvironmentDefinition.AllOf(List.of()));
    private static @Nullable Registry<TestEnvironmentDefinition<?>> environments;

    private GameTestRegistry() {}

    private record Test(Identifier id, GameTestOptions options) {}

    public static synchronized void add(Identifier id, GameTestOptions options, Consumer<GameTestHelper> function) {
        if (TESTS.containsKey(id)) throw new IllegalArgumentException("A game test named " + id + " is already registered");
        FUNCTIONS.computeIfAbsent(id.getNamespace(), namespace -> {
            DeferredRegister<Consumer<GameTestHelper>> register = DeferredRegister.create(namespace, Registries.TEST_FUNCTION);
            register.register();
            return register;
        }).register(id.getPath(), () -> function);
        TESTS.put(id, new Test(id, options));
    }

    public static List<Identifier> addClass(String modId, Class<?> holder) {
        List<Identifier> ids = new ArrayList<>();
        Object instance = null;
        for (Method method : holder.getDeclaredMethods()) {
            GameTest test = method.getAnnotation(GameTest.class);
            if (test == null) continue;
            if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != GameTestHelper.class || method.getReturnType() != void.class) {
                throw new IllegalArgumentException("Game test " + holder.getName() + "." + method.getName() + " must take a GameTestHelper and return nothing");
            }
            method.setAccessible(true);
            Object target = null;
            if (!Modifier.isStatic(method.getModifiers())) {
                if (instance == null) instance = construct(holder);
                target = instance;
            }
            Object owner = target;
            Identifier id = Identifier.fromNamespaceAndPath(modId, snake(holder.getSimpleName()) + "/" + snake(test.name().isEmpty() ? method.getName() : test.name()));
            add(id, GameTestOptions.of(test), helper -> invoke(method, owner, helper));
            ids.add(id);
        }
        if (ids.isEmpty()) CoolCatCore.LOGGER.warn("{} has no @GameTest methods", holder.getName());
        return ids;
    }

    public static boolean isEnabled() {
        return Platform.isDevelopmentEnvironment() || Boolean.getBoolean("coolcatlib.gametest")
                || System.getProperty("fabric-api.gametest") != null || Boolean.getBoolean("neoforge.gameTestServer") || Boolean.getBoolean("neoforge.enableGameTest");
    }

    public static synchronized List<Identifier> ids() {
        return List.copyOf(TESTS.keySet());
    }

    /** A server's test environments, just loaded. */
    public static synchronized void environments(Registry<TestEnvironmentDefinition<?>> registry) {
        environments = registry;
    }

    /** Adds a test instance for each test to a server's test instance registry, before it's frozen. */
    public static synchronized void addInstances(WritableRegistry<GameTestInstance> registry) {
        if (TESTS.isEmpty() || !isEnabled()) return;
        Holder<TestEnvironmentDefinition<?>> defaultEnvironment = environments == null ? NO_ENVIRONMENT
                : environments.get(GameTestEnvironments.DEFAULT_KEY).<Holder<TestEnvironmentDefinition<?>>>map(holder -> holder).orElse(NO_ENVIRONMENT);
        for (Test test : TESTS.values()) {
            ResourceKey<GameTestInstance> key = ResourceKey.create(Registries.TEST_INSTANCE, test.id);
            // A data pack's test of the same name is kept.
            if (registry.containsKey(key)) continue;
            GameTestOptions options = test.options;
            Holder<TestEnvironmentDefinition<?>> environment = options.environment() == null ? defaultEnvironment : Holder.direct(options.environment());
            TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<Holder<TestEnvironmentDefinition<?>>>(environment, options.structure(), options.maxTicks(), options.setupTicks(), options.required(),
                    options.rotation(), options.manualOnly(), options.maxAttempts(), options.requiredSuccesses(), options.skyAccess(), options.padding());
            registry.register(key, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, test.id), data), RegistrationInfo.BUILT_IN);
        }
    }

    private static Object construct(Class<?> holder) {
        try {
            Constructor<?> constructor = holder.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(holder.getName() + " has instance @GameTest methods but no no-argument constructor", e);
        }
    }

    private static void invoke(Method method, Object target, GameTestHelper helper) {
        try {
            method.invoke(target, helper);
        } catch (InvocationTargetException e) {
            // The test's own failure (an assertion), as the framework expects it.
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            if (e.getCause() instanceof Error error) throw error;
            throw new RuntimeException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    // "zapsAZombie" → "zaps_a_zombie", "WandTests" → "wand_tests", "sand falls" → "sand_falls".
    private static String snake(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").replaceAll("([A-Z])([A-Z][a-z])", "$1_$2").toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "_");
    }
}
