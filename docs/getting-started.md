---
icon: material/rocket-launch-outline
description: Set up a multi-loader mod on CoolCatLib
---

# Getting Started

This guide sets up a multi-loader mod on CoolCatLib. It assumes the usual layout: a `common` module plus one module per loader, as with Architectury Loom.

## 1. Add the dependency

CoolCatLib is published on the [ModResources Maven](https://ixdarklord.github.io/ModResources/), free to use with no token; its page lists every version. Artifacts are named `net.ixdarklord.coolcatlib:coolcatlib-<module>:<minecraft>-<build>`, and one artifact holds everything, the foundation (Core) and the visuals (Canvas).

```groovy title="build.gradle"
repositories {
    maven { url = "https://ixdarklord.github.io/ModResources/maven/" }
}
```

=== "Common"

    ```groovy title="common/build.gradle"
    dependencies {
        api "net.ixdarklord.coolcatlib:coolcatlib-common:8.0.0+26.1.2"
    }
    ```

=== "Fabric"

    ```groovy title="fabric/build.gradle"
    dependencies {
        api "net.ixdarklord.coolcatlib:coolcatlib-fabric:8.0.0+26.1.2"
    }
    ```

=== "NeoForge"

    ```groovy title="neoforge/build.gradle"
    dependencies {
        api "net.ixdarklord.coolcatlib:coolcatlib-neoforge:8.0.0+26.1.2"
    }
    ```

=== "Forge (1.21.1, 1.20.1)"

    ```groovy title="forge/build.gradle"
    dependencies {
        modApi "net.ixdarklord.coolcatlib:coolcatlib-forge:8.0.0+1.21.1"   // 1.20.1: 8.0.0+1.20.1
    }
    ```

!!! tip "Remapped versions"
    On Minecraft versions that are still remapped (1.21.1 and 1.20.1), use Loom's `modApi` instead of `api`.

Then declare the dependency in your mod's metadata, on the mod id `coolcatlib`. Versions take the form `<version>+<minecraft>`, the same on every Minecraft version: `8.0.0+26.1.2`, `8.0.0+1.21.1`. Loaders compare the part before the `+`, so ask for that:

=== "Fabric"

    ```json title="fabric.mod.json"
    "depends": {
      "coolcatlib": ">=8.0.0"
    }
    ```

=== "NeoForge / Forge"

    ```toml title="neoforge.mods.toml (mods.toml on Forge)"
    [[dependencies.mymod]]
    modId = "coolcatlib"
    type = "required"
    versionRange = "[8.0.0,)"
    ordering = "AFTER"
    side = "BOTH"
    ```

!!! info "Versions before 8.0.0"
    Until 8.0.0, versions were `<minecraft>-<build>` on 26.x (`26.1.2-7`, `26.3-7`) and plain numbers on 1.21.1 (`2100.4.0.0`) and 1.20.1 (`2001.4.0.1`). Those compare as newer than 8.0.0, so a range of `>=8.0.0` also lets them in: when you need something new in 8.0.0, say so in your mod's description too.

!!! warning "Coming from CoolCatLib: Core and Canvas"
    Mods built for the two old mods have to move over: depend on `coolcatlib` alone (in Gradle and in the mod metadata), and rename the `coolcatcore:*` Fabric entrypoints to `coolcatlib:*`. The packages are unchanged, so no imports change. Players remove the old Core and Canvas jars; CoolCatLib refuses to start next to them.

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
      "coolcatlib:common": ["com.example.mymod.MyMod"],
      "coolcatlib:client": ["com.example.mymod.MyModClient"],
      "coolcatlib:server": ["com.example.mymod.MyModServer"],
      "coolcatlib:datagen": ["com.example.mymod.MyModData"],
      "fabric-datagen": ["net.ixdarklord.coolcatcore.api.core.fabric.FabricDataGenerationEntrypoint"]
    }
    ```

    `FabricDataGenerationEntrypoint` runs every `coolcatlib:datagen` constructor of the mod being generated. You only need it for datagen.

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
