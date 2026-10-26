---
icon: material/test-tube
description: Game tests from common code, on every loader
---

# Game Tests

Game tests from common code, the same on every loader: a test is a function given a `GameTestHelper`, run in a structure built for it, and it passes or fails. CoolCatLib hands your tests to each loader's own test framework, so `/test` and the loaders' game test servers run them. Package: `net.ixdarklord.coolcatcore.api.gametest`.

!!! info "Since"
    Coming in **8.0.0**, on every version. The API is the same everywhere; where 1.21.1 and 1.20.1 differ is [below](#on-1211-and-1201).

## Writing tests

Mark methods with `@GameTest` and register their class while your mod constructs:

```java
public final class WandTests {
    @GameTest
    public static void zapsAZombie(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 4, 1, 4);
        Wand.zap(zombie);
        helper.succeedWhen(() -> helper.assertEntityNotPresent(EntityType.ZOMBIE));
    }

    @GameTest(maxTicks = 60, name = "sand falls")
    public static void sandFalls(GameTestHelper helper) {
        helper.setBlock(2, 4, 2, Blocks.SAND);
        helper.succeedWhenBlockPresent(Blocks.SAND, 2, 1, 2);
    }
}

// In onConstructMod():
GameTests.register(MOD_ID, WandTests.class);
```

A test method takes the `GameTestHelper` and returns nothing. It may be static, or belong to an instance of its class, made once with its no-argument constructor. Each test is named `<modid>:<class>/<method>` in snake case: `mymod:wand_tests/zaps_a_zombie`, and with `name`, `mymod:wand_tests/sand_falls`.

Or register a test by hand, with its options:

```java
GameTests.register(MyMod.id("crate/keeps_items"), GameTestOptions.DEFAULT.withMaxTicks(40), helper -> {
    helper.setBlock(1, 1, 1, MyBlocks.CRATE.get());
    // ...
    helper.succeed();
});
```

## Where tests run

A test runs in a structure, loaded from a data pack: `data/<namespace>/structure/<path>.nbt`, saved with a structure block (or, on 26.x, a test block). Without one of its own, it runs on CoolCatLib's **platform** (`GameTestOptions.PLATFORM`, `coolcatlib:gametest/platform`): an empty 8 × 6 × 8 space on a stone floor.

Positions in the helper count from the structure's corner, the same on every version: on the platform, `(0, 0, 0)` to `(7, 0, 7)` is the floor, and `y = 1` the first layer above it.

## Options

`@GameTest`'s elements and `GameTestOptions` set the same things. `GameTestOptions.DEFAULT` is what most tests want; change what you need with its `with…` methods:

| Option | Default | What it does |
|---|---|---|
| `structure` | the platform | The structure the test is built in. |
| `maxTicks` | 100 | How long the test may run before it fails. |
| `setupTicks` | 0 | Ticks to wait after building the structure, before the test starts. |
| `required` (`optional()`) | true | Whether its failure fails the run; an optional test's failure is only reported. |
| `rotation` | none | How the structure, and the test's positions with it, are turned (quarter turns in `@GameTest`). |
| `manualOnly` (`asManualOnly()`) | false | Only run when asked for by name, not with the others. |
| `maxAttempts`, `requiredSuccesses` (`withAttempts`) | 1, 1 | Try up to `maxAttempts` times; pass once `requiredSuccesses` tries have. For flaky tests. |
| `skyAccess` (`withSkyAccess()`) | false | Build it with open sky above. |
| `padding` | 0 | Extra space around the structure, 0 to 128 blocks. |
| `environment` (`withEnvironment`) | none | The conditions it runs in, such as `new TestEnvironmentDefinition.Weather(Weather.Type.THUNDER)`. 26.x only. |

## Running them

Tests are only added to the game when it's run to test (`GameTests.isEnabled()`): in a development environment, with `-Dcoolcatlib.gametest=true`, or with the loader's own switch (`-Dfabric-api.gametest`, `-Dneoforge.gameTestServer=true`, `-Dforge.gameTestServer=true`). A normal world or server never has them.

=== "26.1.2, 26.2 and 26.3"

    - In game: `/test run mymod:*` runs every test of a mod, `/test run mymod:wand_tests/zaps_a_zombie` one, and `/test runfailed` those that failed.
    - A game test server runs them all and exits, failing if a required one did: on Fabric, a dedicated server with `-Dfabric-api.gametest`; on NeoForge, the `gameTestServer` run.

=== "1.21.1 and 1.20.1"

    - In game: `/test runall mymod` runs every test of a mod, `/test run mymod.wand_tests.zaps_a_zombie` one. The command only reads plain words, so a test's command name is its id with dots.
    - A game test server: on Fabric, a dedicated server with `-Dfabric-api.gametest`; on NeoForge and Forge, their game test server run (`-Dneoforge.gameTestServer=true` / `-Dforge.gameTestServer=true`). Their namespace filters (`enabledGameTestNamespaces`) apply to your tests too.

## On 1.21.1 and 1.20.1

These versions have the older test framework, and CoolCatLib's tests run in it:

- No test environments: `withEnvironment` isn't there. Tests run in batches instead, one per mod unless you set `withBatch(name)`.
- `padding` does nothing, and on 1.20.1 neither does `skyAccess` (tests always have open sky there).
- On 1.20.1, structures are in `data/<namespace>/structures/` (plural).
- `/test` exists only in a development environment there (on 26.x, it's always there, for operators).
- Positions count from the structure's corner, as on 26.x, though vanilla's own helper counts from one block lower on these versions; CoolCatLib's tests get a helper that agrees with 26.1.2.

## Reference

| Type | Description |
|---|---|
| `GameTests` | `register(modId, Class)` (returns the ids), `register(id, test)`, `register(id, options, test)`, `isEnabled()`, `all()`. Register while your mod constructs. |
| `@GameTest` | `name`, `structure`, `maxTicks`, `setupTicks`, `required`, `rotation` (quarter turns), `manualOnly`, `maxAttempts`, `requiredSuccesses`, `skyAccess`, `padding`. |
| `GameTestOptions` | `DEFAULT`, `PLATFORM`; `withStructure`, `withMaxTicks`, `withSetupTicks`, `optional`, `withRotation`, `asManualOnly`, `withAttempts`, `withSkyAccess`, `withPadding`, `withEnvironment` (26.x) or `withBatch` (1.21.1, 1.20.1); `of(GameTest)`. |
