<h1 align="center">CoolCatLib</h1>

<p align="center">
  <img src="https://i.imgur.com/iQlGHUt.png" alt="CoolCatLib" width="160">
</p>

<p align="center">
  <b>A library mod for Minecraft: write it once, run it on every loader, and make it look purr-fect.</b>
</p>

<p align="center">
  <a href="https://www.curseforge.com/minecraft/mc-mods/coolcatlib">CurseForge</a> ·
  <a href="https://modrinth.com/mod/coolcatlib">Modrinth</a> ·
  <a href="https://ixdarklord.github.io/CoolCatLib/">Documentation</a> ·
  <a href="https://github.com/ixDarkLorD/CoolCatLib/issues">Issues</a>
</p>
<p align="center">
  <img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/coolcatlib/fabric.svg" alt="Fabric" height="26">
  <img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/coolcatlib/neoforge.svg" alt="NeoForge" height="26">
  <img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/coolcatlib/forge.svg" alt="Forge" height="26">
  <img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/coolcatlib/library.svg" alt="Library" height="26">
  <a href="https://discord.gg/ZapGEPqm2V"><img src="https://img.shields.io/discord/1173075408556654592?style=flat&logo=discord&logoColor=white&label=&labelColor=e0703a&color=3A3F58" alt="Discord" width="110" height="26"></a>
</p>

<hr>

## About

CoolCatLib gives multi-loader mods one common codebase across Fabric, NeoForge and Forge: events, networking,
registries, configs and attachments, with screen effects, custom skies, world effects, cutscenes and GUI widgets on top.
Players only need it when another mod requires it. For mod developers, the
[documentation](https://ixdarklord.github.io/CoolCatLib/) covers the API.

It is one mod, with the id `coolcatlib`, in two packages:

| Package | What it holds |
|---|---|
| `net.ixdarklord.coolcatcore` | Mod constructors, events, networking, registries, configs, attachments, containers, menus and utilities. |
| `net.ixdarklord.coolcatcanvas` | Screen effects, skyboxes, world effects, cutscenes, camera shake and zoom, GUI widgets and render utilities. |

> [!NOTE]
> Up to 26.1.2-4 / 2100.2.0.1 / 2001.2.0.1, CoolCatLib was two mods, **CoolCatLib: Core** (`coolcatcore`) and
> **CoolCatLib: Canvas** (`coolcatcanvas`). Since **26.1.2-5**, **2100.3.0.0** and **2001.3.0.0** they are one: remove
> the old Core and Canvas jars when updating, and mods built on them depend on `coolcatlib` alone.

## Features

Everything below works from common code on every loader. Some visuals (world effects, cutscenes, camera zoom, scene viewports) are on Minecraft 26.1 – 26.1.2 only.

### 🐾 Mod constructors

Write your mod's setup once: `ModConstructor`, `ClientModConstructor`, `ServerModConstructor` and `DataGenerationConstructor`, each with a construct stage and a setup stage. CoolCatLib wires them into each loader. On Fabric that's an entrypoint in `fabric.mod.json`; on NeoForge and Forge, a single call in your `@Mod` class.

### 📣 Events

Listen to server, player, entity, block and client events from common code. Listeners are ordered by phase and can pass, allow, deny or interrupt. You can also make your own events with `EventInvoker.create`.
- Server lifecycle and ticks, data pack syncs
- Player join, leave and tick, entity loading
- Block break and place, commands
- Client ticks, HUD rendering, item tooltips, client commands

### 📡 Networking

Register serverbound and clientbound payloads and send them in one line. **A record is the whole payload:** CoolCatLib derives the codec from its components (primitives, strings, common Minecraft types, enums, nested records, lists, maps, optionals and more), so there's no `StreamCodec` to write.

### 📦 Registries & menus

- `DeferredRegister` and `RegistryEntry` that work the same on every loader
- Creative tabs, reload listeners, block entity types, entity attributes and renderers
- `ExtendedMenus`: menu types that open with extra data
- Key mappings, menu screens and tooltip components from common code

### 🧶 Attachments

Typed data on **players, entities, block entities and item stacks**: saved with a codec, synced to the players you pick (`NONE`, `SELF`, `TRACKING`, `ALL`, or your own rule), kept on death or when dropped, and backed by change listeners.

### ⚙️ Configs

Declare settings in code, with defaults, ranges and comments:
- Scopes: client, common, server (synced to every player), world (stored per world) and startup (read before content is registered)
- Booleans, ranged numbers, strings, enums, colors, ids, lists and any `Codec`
- TOML or JSON5 files with generated comments
- Validation, presets, versioned migrations and renamed keys
- Hot reloading, change listeners and config events
- `/coolcatlib config` commands to get, set, reset and reload values

### 🌈 Screen effects

Full-screen shaders whose settings can change every frame.
- Ready-made **tint** and **bloom**, or shaders of your own
- Smooth fades, timed pulses, animated strength and conditions
- Stack as many as you like as **layers**, drawn over the world or over the whole screen
- Control a player's effects **from the server**
- Players get their own **Effects screen** to switch effects on and off and set their strength

### 🌌 Custom skies

Replace or decorate the sky, drawn right inside the vanilla sky around the sun, moon and stars.
- Layer types: **cubemap**, **panorama**, **sprite** (planets!), **gradient** and **your own shader**
- Blend modes, tints, rotations, and day-night or weather-driven visibility
- Vanilla's sun, moon, stars and End sky fade out smoothly as your skybox fades in
- Add skies with **no code**: a resource pack JSON is enough

### 💫 World effects

Glowing shapes drawn in the world that hide behind solid things and fade softly where they meet them.
- Effect particles with eased size, opacity and spin, gravity, drag and stretched sparks
- Beams, billboards, ground flashes, ribbons that follow a trail, and **rifts** that tear open in the air
- Your own fragment shaders as materials
- Play an effect **from the server** as one small packet

### 🎬 Cutscenes & camera

- **Cutscenes**: a camera flown along keyframes, with letterbox bars, fades, subtitles and markers; built in code, read from a resource pack, or made in the **in-game editor**
- Play, stop and follow a cutscene **from the server**
- **Camera shake** and **zoom**, respecting the player's accessibility settings

### 🪟 GUI & text

- Panels, scrolling and draggable widgets, slide animations and colorable buttons
- **Scene viewports**: a small 3D scene of blocks and entities inside a screen
- Rainbow and gradient text, outlines for text and widgets
- `RenderUtils`, nine-slice drawing, screen anchors, OBJ models and a full set of **easing curves**

### 🧰 Also included

Item containers and layouts, sided inventories, item decorators, brewing recipes, data components, platform helpers (loader, environment, mod checks, fake players) and a box of small utilities.

## Downloads

| CurseForge | Modrinth | Maven |
|---|---|---|
| [coolcatlib](https://www.curseforge.com/minecraft/mc-mods/coolcatlib) | [coolcatlib](https://modrinth.com/mod/coolcatlib) | `net.ixdarklord.coolcatlib:coolcatlib-<loader>` on `https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/maven/` |

## Versions

Each Minecraft version lives on its own branch. Discontinued versions get no more updates or fixes.

| Minecraft | Loaders | Branch | Status | Latest |
|---|---|---|---|---|
| 26.1 – 26.1.2 | NeoForge, Fabric | [`26.1.2`](https://github.com/ixDarkLorD/CoolCatLib/tree/26.1.2) | Supported | 26.1.2-5 |
| 1.21.1 | NeoForge, Forge, Fabric | [`1.21`](https://github.com/ixDarkLorD/CoolCatLib/tree/1.21) | Supported | 2100.3.0.0 |
| 1.20.1 | Forge, Fabric | [`1.20.1`](https://github.com/ixDarkLorD/CoolCatLib/tree/1.20.1) | Supported | 2001.3.0.0 |
| 1.19.2 | Forge, Fabric | [`1.19.2`](https://github.com/ixDarkLorD/CoolCatLib/tree/1.19.2) | Discontinued | – |
| 1.18.2 | Forge, Fabric | [`1.18.2`](https://github.com/ixDarkLorD/CoolCatLib/tree/1.18.2) | Discontinued | – |

The Fabric versions also need Fabric API.

## Building

Check out the branch for your Minecraft version, then run:

```bash
./gradlew build
```

The jars end up in each loader's folder, e.g. `fabric/build/libs` and `neoforge/build/libs`.

## Reporting issues

Found a bug or have an idea? Open an [issue](https://github.com/ixDarkLorD/CoolCatLib/issues) with the bug report or
feature request template.

## License

See [LICENSE](LICENSE).
