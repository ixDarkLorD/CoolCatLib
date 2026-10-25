package net.ixdarklord.coolcatcore.api.gametest;

import net.ixdarklord.coolcatcore.internal.gametest.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.function.Consumer;

/**
 * Game tests from common code, the same on every loader: a test is a function given a {@code GameTestHelper}, run in
 * a structure built for it, and it passes or fails. Register them while your mod constructs, by class (methods marked
 * {@link GameTest}) or one by one:
 * <pre>{@code
 * GameTests.register(MOD_ID, WandTests.class);
 * GameTests.register(MyMod.id("crate/keeps_items"), GameTestOptions.DEFAULT.withMaxTicks(40), helper -> {
 *     helper.setBlock(1, 1, 1, MyBlocks.CRATE.get());
 *     ...
 *     helper.succeed();
 * });
 * }</pre>
 * Tests are added to the game only when it's run to test ({@link #isEnabled()}): in a development environment, or
 * with {@code -Dcoolcatlib.gametest=true} (or the loader's own switch, {@code -Dfabric-api.gametest} or
 * {@code -Dneoforge.gameTestServer=true}). Then:
 * <ul>
 *   <li>in game, {@code /test run <tests>} runs them by id or pattern ({@code /test run mymod:*} runs all of a mod's),
 *   and {@code /test runfailed} those that failed;</li>
 *   <li>a game test server runs them all and exits, failing if a required one did: on Fabric a dedicated server with
 *   {@code -Dfabric-api.gametest}, on NeoForge the {@code gameTestServer} run.</li>
 * </ul>
 * A test's structure comes from a data pack ({@code data/<ns>/structure/<path>.nbt}, saved with a structure block or
 * the test block); without one, tests run on {@link GameTestOptions#PLATFORM}.
 */
public final class GameTests {
    private GameTests() {}

    /** Registers one test. Call while your mod constructs; its id's namespace must be your mod id. */
    public static void register(Identifier id, Consumer<GameTestHelper> test) {
        register(id, GameTestOptions.DEFAULT, test);
    }

    /** Registers one test with its options. Call while your mod constructs; its id's namespace must be your mod id. */
    public static void register(Identifier id, GameTestOptions options, Consumer<GameTestHelper> test) {
        GameTestRegistry.add(id, options, test);
    }

    /**
     * Registers every {@link GameTest} method of a class, as {@code <modid>:<class>/<method>}. Call while your mod
     * constructs.
     *
     * @return the tests' ids
     */
    public static List<Identifier> register(String modId, Class<?> holder) {
        return GameTestRegistry.addClass(modId, holder);
    }

    /** Whether tests are added to the game this run (see the class description). */
    public static boolean isEnabled() {
        return GameTestRegistry.isEnabled();
    }

    /** Every test registered so far. */
    public static List<Identifier> all() {
        return GameTestRegistry.ids();
    }
}
