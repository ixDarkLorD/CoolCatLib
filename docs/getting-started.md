# Getting Started

This guide sets up a multi-loader mod on CoolCatLib: Core, and optionally Canvas. It assumes the usual layout: a `common` module plus one module per loader, as with Architectury Loom.

## 1. Add the dependency

CoolCatLib is published on the ModResources Maven. Artifacts are named `net.ixdarklord.coolcatlib:coolcatlib-<mod>-<module>:<minecraft>-<build>`, where `<mod>` is `core` or `canvas`.

```groovy title="build.gradle"
repositories {
    maven { url = "https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/maven/" }
}
```

=== "Common"

    ```groovy title="common/build.gradle"
    dependencies {
        api "net.ixdarklord.coolcatlib:coolcatlib-core-common:26.1.2-3"
        api "net.ixdarklord.coolcatlib:coolcatlib-canvas-common:26.1.2-3"   // only if you use Canvas
    }
    ```

=== "Fabric"

    ```groovy title="fabric/build.gradle"
    dependencies {
        api "net.ixdarklord.coolcatlib:coolcatlib-core-fabric:26.1.2-3"
        api "net.ixdarklord.coolcatlib:coolcatlib-canvas-fabric:26.1.2-3"
    }
    ```

=== "NeoForge"

    ```groovy title="neoforge/build.gradle"
    dependencies {
        api "net.ixdarklord.coolcatlib:coolcatlib-core-neoforge:26.1.2-3"
        api "net.ixdarklord.coolcatlib:coolcatlib-canvas-neoforge:26.1.2-3"
    }
    ```

=== "Forge (1.21.1, 1.20.1)"

    ```groovy title="forge/build.gradle"
    dependencies {
        modApi "net.ixdarklord.coolcatlib:coolcatlib-core-forge:1.21.1-2100.2.0.1"
        modApi "net.ixdarklord.coolcatlib:coolcatlib-canvas-forge:1.21.1-2100.1.0.0"
    }
    ```

!!! tip "Remapped versions"
    On Minecraft versions that are still remapped (1.21.1 and 1.20.1), use Loom's `modApi` instead of `api`.

Then declare the dependency in your mod's metadata. Versions take the form `<minecraft>-<build>`:

=== "Fabric"

    ```json title="fabric.mod.json"
    "depends": {
      "coolcatcore": ">=26.1.2-3",
      "coolcatcanvas": ">=26.1.2-3"
    }
    ```

=== "NeoForge / Forge"

    ```toml title="neoforge.mods.toml (mods.toml on Forge)"
    [[dependencies.mymod]]
    modId = "coolcatcore"
    type = "required"
    versionRange = "[26.1.2-3,)"
    ordering = "AFTER"
    side = "BOTH"
    ```

## 2. Write your constructors

Everything starts from constructor classes in your **common** module: one per side, each optional. They live in `net.ixdarklord.coolcatcore.api.core`.

| Interface | Runs | Methods |
|---|---|---|
| `ModConstructor` | Both sides | `onConstructMod()`: create and `register()` your `DeferredRegister`s, and register payloads, events, configs and reload listeners. `onCommonSetup()`: after registries are filled. |
| `ClientModConstructor` | Client only, so it may use client classes | `onConstructMod()`: key mappings, menu screens, tooltip components, client events. `onClientSetup()`. |
| `ServerModConstructor` | Dedicated server only | `onConstructMod()`, `onServerSetup()`. |
| `DataGenerationConstructor` | Datagen runs only | `onGatherData(DataGenerationContext context)`: add providers with `context.addProvider(...)`. |

=== "MyMod.java"

    ```java
    public class MyMod implements ModConstructor {
        public static final String MOD_ID = "mymod";

        @Override
        public void onConstructMod() {
            MyRegistries.BLOCKS.register();
            MyRegistries.ITEMS.register();
            MyConfigs.init();
            MyPayloads.register();
            ServerTickEvents.END.register(server -> { /* every server tick */ });
        }
    }
    ```

=== "MyModClient.java"

    ```java
    public class MyModClient implements ClientModConstructor {
        @Override
        public void onConstructMod() {
            MenuScreenRegistry.register(MyRegistries.CRATE_MENU, CrateScreen::new);
            ClientTickEvents.END.register(minecraft -> { /* every client tick */ });
        }
    }
    ```

!!! warning "Register early"
    Payloads, key mappings, menu screens and tooltip components must be registered in `onConstructMod()`. NeoForge collects them through registration events that fire before common setup.

## 3. Hook them up per loader

=== "Fabric"

    No Java class is needed; list your constructors as entrypoints:

    ```json title="fabric.mod.json"
    "entrypoints": {
      "coolcatcore:common": ["com.example.mymod.MyMod"],
      "coolcatcore:client": ["com.example.mymod.MyModClient"],
      "coolcatcore:server": ["com.example.mymod.MyModServer"],
      "coolcatcore:datagen": ["com.example.mymod.MyModData"],
      "fabric-datagen": ["net.ixdarklord.coolcatcore.api.core.fabric.FabricDataGenerationEntrypoint"]
    }
    ```

    `FabricDataGenerationEntrypoint` runs every `coolcatcore:datagen` constructor of the mod being generated. You only need it for datagen.

=== "NeoForge"

    Extend `NeoForgeModEntrypoint` with your `@Mod` class:

    ```java title="MyModNeoForge.java"
    @Mod(MyMod.MOD_ID)
    public final class MyModNeoForge extends NeoForgeModEntrypoint {
        public MyModNeoForge(ModContainer container) {
            super(container);
            this.common(MyMod::new);
            this.client(() -> MyModClient::new);
            this.server(() -> MyModServer::new);
            this.dataGeneration(MyModData::new);
        }
    }
    ```

    Client and server constructors are given as a *supplier of a factory*, so their classes are never loaded on the other side. The entrypoint also exposes `container`, `modEventBus` and `modId` for any NeoForge-specific setup you still need.

=== "Forge (1.21.1, 1.20.1)"

    The same idea, with `ForgeModEntrypoint` and Forge's loading context:

    ```java title="MyModForge.java"
    @Mod(MyMod.MOD_ID)
    public final class MyModForge extends ForgeModEntrypoint {
        public MyModForge(FMLJavaModLoadingContext context) {
            super(context);
            this.common(MyMod::new);
            this.client(() -> MyModClient::new);
        }
    }
    ```

## 4. Next steps

<div class="grid cards" markdown>

-   :material-bullhorn-outline: **[Events](core/events.md)**

    React to the game from common code.

-   :material-database-plus-outline: **[Registration](core/registration.md)**

    Blocks, items, creative tabs and client registries.

-   :material-access-point-network: **[Networking](core/networking.md)**

    Payloads without writing codecs.

-   :material-tune-variant: **[Configs](core/configs.md)**

    Settings players can edit in game.

-   :material-paperclip: **[Attachments](core/attachments.md)**

    Data on players, entities and items.

-   :material-palette-outline: **[Canvas](canvas/screen-effects.md)**

    Shader effects, skyboxes and animated text.

</div>
