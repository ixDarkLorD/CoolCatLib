package net.ixdarklord.coolcatcore.internal.gametest;

import net.ixdarklord.coolcatcore.api.gametest.GameTest;
import net.ixdarklord.coolcatcore.api.gametest.GameTestOptions;
import net.ixdarklord.coolcatcore.api.platform.Platform;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.ixdarklord.coolcatcore.internal.mixin.GameTestHelperAccessor;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

// The registered game tests (api.gametest.GameTests). When tests are enabled, each becomes a TestFunction in the game's
// own registry of them (net.minecraft.gametest.framework.GameTestRegistry), from where /test and the loaders' test
// servers run them. Commands only take plain words with dots, so a test is named after its id with dots
// ("mymod:wand_tests/zaps_a_zombie" → "mymod.wand_tests.zaps_a_zombie"), and the mod id and each folder of the path are
// added as test "classes", for /test runall <modid>. Manual-only tests stay out of the list everything runs from and
// are found by name only (GameTestRegistryMixin). Tests get a helper that counts positions from the structure's corner,
// as on newer versions (CornerGameTestHelper).
public final class GameTestRegistry {
    private static final Map<ResourceLocation, GameTestOptions> TESTS = new LinkedHashMap<>();
    private static final Map<String, TestFunction> MANUAL_ONLY = new HashMap<>();

    private GameTestRegistry() {}

    public static synchronized void add(ResourceLocation id, GameTestOptions options, Consumer<GameTestHelper> function) {
        if (TESTS.containsKey(id)) throw new IllegalArgumentException("A game test named " + id + " is already registered");
        String name = testName(id);
        if (isEnabled() && isNamespaceEnabled(id.getNamespace())) {
            if (find(name).isPresent()) throw new IllegalArgumentException("A game test named " + name + " (for " + id + ") is already registered");
            TestFunction test = new TestFunction(options.batch() == null ? id.getNamespace() : options.batch(), name, options.structure().toString(),
                    options.rotation(), options.maxTicks(), options.setupTicks(), options.required(), options.requiredSuccesses(), options.maxAttempts(),
                    helper -> function.accept(new CornerGameTestHelper(((GameTestHelperAccessor) helper).coolcatlib$testInfo())));
            if (options.manualOnly()) {
                MANUAL_ONLY.put(name, test);
            } else {
                net.minecraft.gametest.framework.GameTestRegistry.getAllTestFunctions().add(test);
                Collection<String> classes = net.minecraft.gametest.framework.GameTestRegistry.getAllTestClassNames();
                String prefix = id.getNamespace();
                classes.add(prefix);
                String[] folders = id.getPath().split("/");
                for (int i = 0; i < folders.length - 1; i++) classes.add(prefix += "." + folders[i]);
            }
        }
        TESTS.put(id, options);
    }

    public static List<ResourceLocation> addClass(String modId, Class<?> holder) {
        List<ResourceLocation> ids = new ArrayList<>();
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
            ResourceLocation id = new ResourceLocation(modId, snake(holder.getSimpleName()) + "/" + snake(test.name().isEmpty() ? method.getName() : test.name()));
            add(id, GameTestOptions.of(test), helper -> invoke(method, owner, helper));
            ids.add(id);
        }
        if (ids.isEmpty()) CoolCatCore.LOGGER.warn("{} has no @GameTest methods", holder.getName());
        return ids;
    }

    public static boolean isEnabled() {
        return Platform.isDevelopmentEnvironment() || Boolean.getBoolean("coolcatlib.gametest")
                || System.getProperty("fabric-api.gametest") != null || Boolean.getBoolean("forge.enableGameTest") || Boolean.getBoolean("forge.gameTestServer");
    }

    public static synchronized List<ResourceLocation> ids() {
        return List.copyOf(TESTS.keySet());
    }

    /** A manual-only test, by its name in commands (as the game finds tests: ignoring case). */
    public static synchronized Optional<TestFunction> findManualOnly(String name) {
        return MANUAL_ONLY.values().stream().filter(test -> test.getTestName().equalsIgnoreCase(name)).findFirst();
    }

    private static Optional<TestFunction> find(String name) {
        Optional<TestFunction> test = net.minecraft.gametest.framework.GameTestRegistry.getAllTestFunctions().stream()
                .filter(function -> function.getTestName().equalsIgnoreCase(name)).findFirst();
        return test.isPresent() ? test : findManualOnly(name);
    }

    private static String testName(ResourceLocation id) {
        return id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    // Forge's own filter for its game tests: when -Dforge.enabledGameTestNamespaces lists mods, only theirs are added.
    private static boolean isNamespaceEnabled(String namespace) {
        String enabled = System.getProperty("forge.enabledGameTestNamespaces");
        if (enabled == null || !Platform.isForge()) return true;
        Set<String> namespaces = Arrays.stream(enabled.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        return namespaces.isEmpty() || namespaces.contains(namespace);
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
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").replaceAll("([A-Z])([A-Z][a-z])", "$1_$2").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "_");
    }
}
