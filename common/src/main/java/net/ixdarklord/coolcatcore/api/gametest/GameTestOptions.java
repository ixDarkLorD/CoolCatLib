package net.ixdarklord.coolcatcore.api.gametest;

import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import org.jetbrains.annotations.Nullable;

/**
 * How a game test runs. {@link #DEFAULT} is what most tests want; change what you need with the {@code with} methods:
 * <pre>{@code
 * GameTestOptions.DEFAULT.withStructure(MyMod.id("gametest/furnace_room")).withMaxTicks(200)
 * }</pre>
 * There are no test environments on this version (newer versions have an {@code environment} option, for game rules,
 * weather and time); set those up in the test itself. Tests run in batches instead, one per mod unless
 * {@link #withBatch(String)} says otherwise.
 *
 * @param structure         the structure the test is built in, from a data pack ({@code data/<ns>/structure/...});
 *                          {@link #PLATFORM} is an empty 8 × 6 × 8 space on a stone floor
 * @param maxTicks          how long the test may run before it fails
 * @param setupTicks        ticks to wait after building the structure, before the test starts
 * @param required          whether its failing fails the run (an optional test's failure is only reported)
 * @param rotation          how the structure, and the test's positions with it, are turned
 * @param manualOnly        only run when asked for by name ({@code /test run}), not with all the others
 * @param maxAttempts       how many times it may be tried
 * @param requiredSuccesses how many of those tries must pass
 * @param skyAccess         whether the structure is built with open sky above it rather than in a closed space
 * @param padding           extra space left around the structure, 0 to 128 blocks; on this version the game lays tests
 *                          out on its own grid, so this changes nothing
 * @param batch             the batch the test runs in, with the tests of the same batch, or null for its mod's (the
 *                          namespace of its id)
 */
public record GameTestOptions(ResourceLocation structure, int maxTicks, int setupTicks, boolean required, Rotation rotation, boolean manualOnly,
                              int maxAttempts, int requiredSuccesses, boolean skyAccess, int padding, @Nullable String batch) {
    /** CoolCatLib's own test structure: an empty 8 × 6 × 8 space on a stone floor. */
    public static final ResourceLocation PLATFORM = CoolCatCore.rl("gametest/platform");
    public static final GameTestOptions DEFAULT = new GameTestOptions(PLATFORM, 100, 0, true, Rotation.NONE, false, 1, 1, false, 0, null);

    public GameTestOptions {
        if (maxTicks < 1) throw new IllegalArgumentException("maxTicks must be positive: " + maxTicks);
        if (setupTicks < 0) throw new IllegalArgumentException("setupTicks can't be negative: " + setupTicks);
        if (maxAttempts < 1 || requiredSuccesses < 1 || requiredSuccesses > maxAttempts) {
            throw new IllegalArgumentException("Needs 1 ≤ requiredSuccesses ≤ maxAttempts, got " + requiredSuccesses + " of " + maxAttempts);
        }
        if (padding < 0 || padding > 128) throw new IllegalArgumentException("padding must be 0 to 128: " + padding);
        if (batch != null && batch.isBlank()) throw new IllegalArgumentException("batch can't be blank");
    }

    public GameTestOptions withStructure(ResourceLocation structure) {
        return new GameTestOptions(structure, this.maxTicks, this.setupTicks, this.required, this.rotation, this.manualOnly, this.maxAttempts, this.requiredSuccesses, this.skyAccess, this.padding, this.batch);
    }

    public GameTestOptions withMaxTicks(int maxTicks) {
        return new GameTestOptions(this.structure, maxTicks, this.setupTicks, this.required, this.rotation, this.manualOnly, this.maxAttempts, this.requiredSuccesses, this.skyAccess, this.padding, this.batch);
    }

    public GameTestOptions withSetupTicks(int setupTicks) {
        return new GameTestOptions(this.structure, this.maxTicks, setupTicks, this.required, this.rotation, this.manualOnly, this.maxAttempts, this.requiredSuccesses, this.skyAccess, this.padding, this.batch);
    }

    public GameTestOptions optional() {
        return new GameTestOptions(this.structure, this.maxTicks, this.setupTicks, false, this.rotation, this.manualOnly, this.maxAttempts, this.requiredSuccesses, this.skyAccess, this.padding, this.batch);
    }

    public GameTestOptions withRotation(Rotation rotation) {
        return new GameTestOptions(this.structure, this.maxTicks, this.setupTicks, this.required, rotation, this.manualOnly, this.maxAttempts, this.requiredSuccesses, this.skyAccess, this.padding, this.batch);
    }

    public GameTestOptions asManualOnly() {
        return new GameTestOptions(this.structure, this.maxTicks, this.setupTicks, this.required, this.rotation, true, this.maxAttempts, this.requiredSuccesses, this.skyAccess, this.padding, this.batch);
    }

    /** Tried up to {@code maxAttempts} times; passes once {@code requiredSuccesses} tries have. For flaky tests. */
    public GameTestOptions withAttempts(int maxAttempts, int requiredSuccesses) {
        return new GameTestOptions(this.structure, this.maxTicks, this.setupTicks, this.required, this.rotation, this.manualOnly, maxAttempts, requiredSuccesses, this.skyAccess, this.padding, this.batch);
    }

    public GameTestOptions withSkyAccess() {
        return new GameTestOptions(this.structure, this.maxTicks, this.setupTicks, this.required, this.rotation, this.manualOnly, this.maxAttempts, this.requiredSuccesses, true, this.padding, this.batch);
    }

    public GameTestOptions withPadding(int padding) {
        return new GameTestOptions(this.structure, this.maxTicks, this.setupTicks, this.required, this.rotation, this.manualOnly, this.maxAttempts, this.requiredSuccesses, this.skyAccess, padding, this.batch);
    }

    /**
     * Runs in this batch, with the other tests of the same batch. The tests of a batch are built and run together, and a
     * batch starts once the one before it has finished.
     */
    public GameTestOptions withBatch(String batch) {
        return new GameTestOptions(this.structure, this.maxTicks, this.setupTicks, this.required, this.rotation, this.manualOnly, this.maxAttempts, this.requiredSuccesses, this.skyAccess, this.padding, batch);
    }

    /** The options an annotated method asks for. */
    public static GameTestOptions of(GameTest test) {
        return new GameTestOptions(test.structure().isEmpty() ? PLATFORM : ResourceLocation.parse(test.structure()), test.maxTicks(), test.setupTicks(), test.required(),
                Rotation.values()[Math.floorMod(test.rotation(), 4)], test.manualOnly(), test.maxAttempts(), test.requiredSuccesses(), test.skyAccess(), test.padding(), null);
    }
}
