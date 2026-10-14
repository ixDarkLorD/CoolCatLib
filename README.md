<h1 align="center">CoolCatLib</h1>
<p align="center"><b>A Minecraft library mod.</b></p>
<p align="center">
  <a href="https://www.curseforge.com/minecraft/mc-mods/coolcatlib">CurseForge</a> ·
  <a href="https://modrinth.com/mod/ASkaoGC8">Modrinth</a> ·
  <a href="https://ixdarklord.github.io/CoolCatLib/">Documentation</a>
</p>
<hr>

One mod (`coolcatlib`), in two packages:

| Package | What it holds |
|---|---|
| `net.ixdarklord.coolcatcore` | Mod constructors, events, networking, registries, configs, attachments, containers, menus and utilities. Its configs show in [Glazed Menu](https://github.com/ixDarkLorD/GlazedMenu)'s screens, or in Configured's. |
| `net.ixdarklord.coolcatcanvas` | Screen effects, skyboxes, world effects, cutscenes, GUI widgets (the styled widgets and theme effects config screens use) and render utilities. |

The second package was its own mod, CoolCatLib: Canvas, up to version 7 (its releases are in `CHANGELOG-canvas.md`). The
old `coolcatcore` and `coolcatcanvas` mod ids are gone; Canvas's asset namespace and commands are unchanged.

The `common`, `fabric` and `neoforge` modules are in the root folder, with the mod's attributes in `gradle.properties`.
`testmods/` holds the Fabric and NeoForge test mods, which run with CoolCatLib and Glazed Menu (its own project, taken
from Maven Local); `testmods/testmod.gradle` sets them up.

## Features

Everything below works from common code on every loader.

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

<p align="center"> <img src="https://i.imgur.com/iQlGHUt.png" alt="">
