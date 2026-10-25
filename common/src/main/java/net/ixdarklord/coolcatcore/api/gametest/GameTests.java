package net.ixdarklord.coolcatcore.api.gametest;

import net.ixdarklord.coolcatcore.internal.gametest.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

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
 * with {@code -Dcoolcatlib.gametest=true} (or the loader's own switch, {@code -Dfabric-api.gametest},
 * {@code -Dneoforge.enableGameTest=true} or {@code -Dforge.enableGameTest=true}). On NeoForge and Forge,
 * {@code -Dneoforge.enabledGameTestNamespaces=mymod,othermod} (or {@code forge.}) keeps only those mods' tests, as it
 * does for the loader's own. Then:
 * <ul>
 *   <li>in game, {@code /test run <test>} runs one by its name, which on this version is its id with dots
 *   ({@code mymod:wand_tests/zaps_a_zombie} is {@code mymod.wand_tests.zaps_a_zombie}); {@code /test runall} runs
 *   them all, {@code /test runall mymod} all of a mod's ({@code /test runall mymod.wand_tests} one class's), and
 *   {@code /test runfailed} those that failed. The {@code /test} command is there in a development environment and
 *   on Fabric clients; on NeoForge and Forge it's only in a development environment, where the loader allows it;</li>
 *   <li>a game test server runs them all and exits, failing if a required one did: on Fabric a dedicated server with
 *   {@code -Dfabric-api.gametest}, on NeoForge and Forge a development server with
 *   {@code -Dneoforge.gameTestServer=true} or {@code -Dforge.gameTestServer=true}.</li>
 * </ul>
 * A test's structure comes from a data pack ({@code data/<ns>/structure/<path>.nbt}, saved with a structure block);
 * without one, tests run on {@link GameTestOptions#PLATFORM}. The helper's positions count from the structure's
 * corner, {@code (0, 0, 0)} being its lowest block, as on newer versions, so a test reads the same on every version
 * (the game's own tests on this version count from the structure block, one below, except on Forge with its
 * {@code fixGameTestBlockPosAsserts} option).
 */
public final class GameTests {
    private GameTests() {}

    /** Registers one test. Call while your mod constructs; its id's namespace must be your mod id. */
    public static void register(ResourceLocation id, Consumer<GameTestHelper> test) {
        register(id, GameTestOptions.DEFAULT, test);
    }

    /** Registers one test with its options. Call while your mod constructs; its id's namespace must be your mod id. */
    public static void register(ResourceLocation id, GameTestOptions options, Consumer<GameTestHelper> test) {
        GameTestRegistry.add(id, options, test);
    }

    /**
     * Registers every {@link GameTest} method of a class, as {@code <modid>:<class>/<method>}. Call while your mod
     * constructs.
     *
     * @return the tests' ids
     */
    public static List<ResourceLocation> register(String modId, Class<?> holder) {
        return GameTestRegistry.addClass(modId, holder);
    }

    /** Whether tests are added to the game this run (see the class description). */
    public static boolean isEnabled() {
        return GameTestRegistry.isEnabled();
    }

    /** Every test registered so far. */
    public static List<ResourceLocation> all() {
        return GameTestRegistry.ids();
    }
}
