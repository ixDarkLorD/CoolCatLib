---
title: Home
icon: material/home-outline
hide:
  - navigation
  - toc
---

<div class="ccl-hero" markdown>

<div class="ccl-hero__glow" aria-hidden="true"><span></span><span></span><span></span></div>

<div class="ccl-hero__text" markdown>

<p class="ccl-hero__eyebrow"><span class="ccl-hero__pulse"></span>Fabric · NeoForge · Forge · Minecraft 1.20.1 – 26.3</p>

# CoolCatLib { .ccl-hero__title }

<p class="ccl-hero__tagline">Write it once, run it everywhere, and make it look <em>purr-fect</em>.</p>

<p class="ccl-hero__lead">The foundation and the visuals for multi-loader Minecraft mods: one common codebase for Fabric, NeoForge and Forge, with no Architectury API needed.</p>

<div class="ccl-hero__actions" markdown>
[Get started :material-arrow-right:](getting-started.md){ .md-button .md-button--primary }
[API reference](reference/api.md){ .md-button }
[:fontawesome-brands-github: Source](https://github.com/ixDarkLorD/CoolCatLib){ .md-button }
</div>

<p class="ccl-hero__badges">
  <img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/coolcatlib/fabric.svg" alt="Fabric" height="26">
  <img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/coolcatlib/neoforge.svg" alt="NeoForge" height="26">
  <img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/coolcatlib/forge.svg" alt="Forge" height="26">
  <img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/coolcatlib/library.svg" alt="Library" height="26">
  <a href="https://discord.gg/ZapGEPqm2V"><img src="https://img.shields.io/discord/1173075408556654592?style=flat&logo=discord&logoColor=white&label=&labelColor=e0703a&color=3A3F58" alt="Discord" height="26"></a>
</p>

</div>

<div class="ccl-hero__stage">
  <figure class="ccl-hero__shot" data-ccl-slides>
    <img src="assets/screen-effects/none.webp" alt="The game with no effect" data-label="No effect" class="is-on">
    <img src="assets/screen-effects/tint-frost.webp" alt="A blue tint over the world" data-label="Frost tint" loading="lazy">
    <img src="assets/screen-effects/vignette.webp" alt="Dark corners closing in on the view" data-label="Vignette" loading="lazy">
    <img src="assets/screen-effects/desaturate.webp" alt="The world drained of color" data-label="Desaturate" loading="lazy">
    <img src="assets/screen-effects/tint-hurt.webp" alt="A red tint over the world" data-label="Hurt flash" loading="lazy">
    <img src="assets/screen-effects/invert.webp" alt="The game with its colors inverted" data-label="Invert" loading="lazy">
    <figcaption><span class="ccl-hero__live">Live</span> Screen effects · <b data-ccl-slide-label>No effect</b></figcaption>
  </figure>
  <img class="ccl-hero__logo" src="assets/logo.png" alt="">
  <span class="ccl-hero__chip ccl-hero__chip--a" aria-hidden="true">Events</span>
  <span class="ccl-hero__chip ccl-hero__chip--b" aria-hidden="true">Synced configs</span>
  <span class="ccl-hero__chip ccl-hero__chip--c" aria-hidden="true">Shaders</span>
</div>

</div>

<div class="ccl-ribbon" aria-hidden="true"><div class="ccl-ribbon__track">
<span>Entrypoints</span><span>Events</span><span>Registration</span><span>Networking</span><span>Synced configs</span><span>Attachments</span><span>Storage menus</span><span>Item decorators</span><span>Game tests</span><span>Screen effects</span><span>Skyboxes</span><span>World effects</span><span>Cutscenes</span><span>Sound filters</span><span>Gradient text</span><span>GUI widgets</span>
<span>Entrypoints</span><span>Events</span><span>Registration</span><span>Networking</span><span>Synced configs</span><span>Attachments</span><span>Storage menus</span><span>Item decorators</span><span>Game tests</span><span>Screen effects</span><span>Skyboxes</span><span>World effects</span><span>Cutscenes</span><span>Sound filters</span><span>Gradient text</span><span>GUI widgets</span>
</div></div>

<div class="ccl-stats">
  <div><b>3</b><span>loaders from one <code>common</code> module</span></div>
  <div><b>5</b><span>Minecraft lines, 1.20.1 to 26.3</span></div>
  <div><b>1</b><span>jar per loader, Core and Canvas inside</span></div>
  <div><b>0</b><span>extra APIs to install</span></div>
</div>

## One mod, two halves

<div class="ccl-halves" markdown>

<div class="ccl-half ccl-half--core" markdown>

### Core

<p class="ccl-half__pkg"><code>net.ixdarklord.coolcatcore</code> · the purr-fect foundation</p>

Entrypoints that replace per-loader setup; events, registration and networking; synced TOML configs; attachments; containers and storage menus; item decorators.

[Getting started :material-arrow-right:](getting-started.md){ .ccl-half__link }

</div>

<div class="ccl-half ccl-half--canvas" markdown>

### Canvas

<p class="ccl-half__pkg"><code>net.ixdarklord.coolcatcanvas</code> · the cat's whiskers of visuals</p>

Live post-processing shaders, layered skyboxes, gradient and rainbow text, outlines, panels, pan & zoom views and easing curves.

[Screen effects :material-arrow-right:](canvas/screen-effects.md){ .ccl-half__link }

</div>

</div>

## Why CoolCatLib

<div class="grid cards ccl-features" markdown>

-   :material-earth:{ .lg .middle } **Truly cross-loader**

    Your logic lives in `common`. Events, registries, payloads, menus and client registries are bridged to every loader.

-   :material-puzzle-outline:{ .lg .middle } **No boilerplate payloads**

    Record payloads get their `StreamCodec` derived for you, from primitives to lists, maps, optionals and records.

-   :material-tune-variant:{ .lg .middle } **Configs players love**

    Typed values with ranges, presets and migrations. Server values sync live, and players edit them in game with Glazed Menu or Configured.

-   :material-paperclip:{ .lg .middle } **Data on anything**

    Attachments on entities, block entities and item stacks, with sync policies, copy on death and change events.

-   :material-package-variant-closed:{ .lg .middle } **Inventories done right**

    Slot roles, per-face access, automation-style transfer and texture-free storage menus.

-   :material-creation-outline:{ .lg .middle } **Visuals without the pain**

    Shader effects with per-frame uniforms, layered skies, gradient text and outlines, all animated.

</div>

## A thirty-second tour

```java title="MyMod.java — runs on Fabric, NeoForge and Forge alike"
public class MyMod implements ModConstructor {
    public static final ConfigBuilder CONFIG = Config.builder("mymod", ConfigScope.SERVER);
    public static final ConfigValue<Integer> MAX_HOMES = CONFIG.intValue("maxHomes", 3).range(0, 64).slider().build();

    @Override
    public void onConstructMod() {
        CONFIG.build(); // (1)!
        Network.registerServerbound(PingPayload.class, (payload, context) -> // (2)!
                context.getPlayer().sendSystemMessage(Component.literal("Pong from " + payload.pos())));
        PlayerEvents.JOIN.register(player -> // (3)!
                player.sendSystemMessage(ColorGradient.RAINBOW.literal("Welcome, " + player.getName().getString() + "!")));
    }
}

public record PingPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<PingPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mymod", "ping"));
    @Override public Type<PingPayload> type() { return TYPE; }
}
```

1.  Registers and loads `config/mymod-server.toml`. Its values sync to every client, and players can edit it in game with Glazed Menu or Configured.
2.  A record payload: CoolCatLib derives its network codec from the record's components.
3.  An animated rainbow message, drawn by Canvas wherever the text shows up.

## How it fits together

```mermaid
flowchart LR
    subgraph YourMod["Your mod"]
        C["common module<br/>ModConstructor · ClientModConstructor"]
        F["fabric"]
        N["neoforge / forge"]
    end
    subgraph CCL["CoolCatLib"]
        CORE["Core<br/>events · registries · network<br/>configs · attachments"]
        CANVAS["Canvas<br/>effects · skyboxes · text · GUI"]
    end
    F -- "coolcatlib:* entrypoints" --> C
    N -- "NeoForgeModEntrypoint<br/>ForgeModEntrypoint" --> C
    C --> CORE
    C --> CANVAS
    CANVAS --> CORE
```

## Versions and loaders

| Minecraft | Latest | NeoForge | Forge | Fabric | Source |
|---|---|:-:|:-:|:-:|---|
| **26.3** | `26.3-7` | :material-check: | – | :material-check: | [`26.3`](https://github.com/ixDarkLorD/CoolCatLib/tree/26.3) |
| **26.2** | `26.2-6` | :material-check: | – | :material-check: | [`26.2`](https://github.com/ixDarkLorD/CoolCatLib/tree/26.2) |
| **26.1 – 26.1.2** | `26.1.2-6` | :material-check: | – | :material-check: | [`26.1.2`](https://github.com/ixDarkLorD/CoolCatLib/tree/26.1.2) |
| **1.21 – 1.21.1** | `2100.4.0.0` | :material-check: | :material-check: | :material-check: | [`1.21`](https://github.com/ixDarkLorD/CoolCatLib/tree/1.21) |
| **1.20.1** | `2001.4.0.1` | – | :material-check: | :material-check: | [`1.20.1`](https://github.com/ixDarkLorD/CoolCatLib/tree/1.20.1) |

!!! note "About these docs"
    The pages are written against **26.1.2**; 26.2 and 26.3 have the same API. Pages say when something is newer than the latest release, or missing from a version. The backports share the same API, adjusted for their Minecraft version: `ResourceLocation` instead of `Identifier`, `GuiGraphics` instead of `GuiGraphicsExtractor`, and a Forge entrypoint.
