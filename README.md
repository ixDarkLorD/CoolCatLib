<p align="center">
  <img src="https://raw.githubusercontent.com/ixDarkLorD/CoolCatLib/26.1.2/common/src/main/resources/mod_logo.png" alt="CoolCatLib" width="160">
</p>

<h1 align="center">CoolCatLib</h1>

<p align="center">
  <b>Write it once, run it everywhere, and make it look purr-fect.</b><br>
  A library mod for Fabric, NeoForge and Forge.
</p>

<p align="center">
  <a href="https://modrinth.com/mod/coolcatlib"><img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/coolcatlib/modrinth.svg" alt="Modrinth" height="26"></a>
  <a href="https://www.curseforge.com/minecraft/mc-mods/coolcatlib"><img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/coolcatlib/curseforge.svg" alt="CurseForge" height="26"></a>
  <a href="https://ixdarklord.github.io/CoolCatLib/"><img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/coolcatlib/docs.svg" alt="Docs" height="26"></a>
  <a href="https://discord.gg/ZapGEPqm2V"><img src="https://img.shields.io/discord/1173075408556654592?style=flat&logo=discord&logoColor=white&label=&labelColor=e0703a&color=3A3F58" alt="Discord" width="110" height="26"></a>
</p>
<p align="center">
  <img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/coolcatlib/fabric.svg" alt="Fabric" height="26">
  <img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/coolcatlib/neoforge.svg" alt="NeoForge" height="26">
  <img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/coolcatlib/forge.svg" alt="Forge" height="26">
  <img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/coolcatlib/library.svg" alt="Library" height="26">
</p>

<hr>

## ✨ About

CoolCatLib gives multi-loader mods one common codebase across Fabric, NeoForge and Forge: events, networking,
registries, configs and attachments, with screen effects, custom skies, world effects, cutscenes and GUI widgets on top.
Players only need it when another mod requires it.

It is one mod, `coolcatlib`, in two packages: `net.ixdarklord.coolcatcore` (constructors, events, networking,
registries, configs, attachments, menus and utilities) and `net.ixdarklord.coolcatcanvas` (screen effects, skies, world
effects, cutscenes, the camera and GUI).

> [!NOTE]
> Up to 26.1.2-4, 2100.2.0.1 and 2001.2.0.1, CoolCatLib was two mods, **CoolCatLib: Core** and **CoolCatLib: Canvas**.
> They are one mod now: remove both old jars when you update.

## 🧩 Features

Everything works from common code on every loader.

- **🐾 Mod constructors.** Write your mod's setup once, for the client, the server and data generation; CoolCatLib wires
  it into each loader.
- **📣 Events.** Server, player, entity, block, interaction and client events, ordered by phase, and your own with
  `EventInvoker.create`.
- **📡 Networking.** A record is the whole payload: its codec is derived from its components, so there's no
  `StreamCodec` to write.
- **📦 Registries and menus.** Deferred registers, creative tabs, reload listeners, menus that open with extra data, key
  mappings and tooltip components.
- **🧶 Attachments.** Typed data on players, entities, block entities and item stacks: saved, synced to the players you
  pick, kept on death, with change listeners.
- **⚙️ Configs.** Client, common, server, world and startup scopes in TOML or JSON5, with validation, presets,
  migrations, hot reloading and `/coolcatlib config` commands.
- **🌈 Screen effects.** Full-screen shaders in layers, with fades and pulses, controlled from the server, and an
  Effects screen for players.
- **🌌 Custom skies.** Cubemaps, panoramas, sprites, gradients and shaders inside the vanilla sky, added with a resource
  pack alone.
- **💫 World effects.** Glowing particles, beams, ribbons and rifts that fade softly into the world, played from the
  server in one packet.
- **🎬 Cutscenes and camera.** Keyframed cutscenes with an in-game editor, camera shake and zoom.
- **🪟 GUI and text.** Panels, scrolling and draggable widgets, scene viewports, gradient text, nine-slices and easing
  curves.

The [documentation](https://ixdarklord.github.io/CoolCatLib/) covers the whole API.

## 📦 Download

| CurseForge | Modrinth |
|---|---|
| [coolcatlib](https://www.curseforge.com/minecraft/mc-mods/coolcatlib) | [coolcatlib](https://modrinth.com/mod/coolcatlib) |

On Fabric it also needs [Fabric API](https://modrinth.com/mod/fabric-api).

## 🧭 Versions

Each Minecraft version lives on its own branch. Discontinued versions get no more updates or fixes.

| Minecraft | Loaders | Branch | Status | Latest |
|---|---|---|:-:|---|
| **26.3** | Fabric, NeoForge | [`26.3`](https://github.com/ixDarkLorD/CoolCatLib/tree/26.3) | ✅ Supported | 26.3-6 |
| **26.2** | Fabric, NeoForge | [`26.2`](https://github.com/ixDarkLorD/CoolCatLib/tree/26.2) | ✅ Supported | 26.2-6 |
| **26.1 – 26.1.2** | Fabric, NeoForge | [`26.1.2`](https://github.com/ixDarkLorD/CoolCatLib/tree/26.1.2) | ✅ Supported | 26.1.2-6 |
| **1.21.1** | Fabric, NeoForge, Forge | [`1.21`](https://github.com/ixDarkLorD/CoolCatLib/tree/1.21) | ✅ Supported | 2100.4.0.0 |
| **1.20.1** | Fabric, Forge | [`1.20.1`](https://github.com/ixDarkLorD/CoolCatLib/tree/1.20.1) | ✅ Supported | 2001.4.0.1 |
| 1.19.2 | Fabric, Forge | [`1.19.2`](https://github.com/ixDarkLorD/CoolCatLib/tree/1.19.2) | Discontinued | – |
| 1.18.2 | Fabric, Forge | [`1.18.2`](https://github.com/ixDarkLorD/CoolCatLib/tree/1.18.2) | Discontinued | – |

## 🔌 For mod developers

CoolCatLib is on the [ModResources Maven](https://ixdarklord.github.io/ModResources/), free to use with no token:

```groovy
repositories {
    maven {
        name = "ModResources"
        url = "https://ixdarklord.github.io/ModResources/maven/"
    }
}

dependencies {
    compileOnly "net.ixdarklord.coolcatlib:coolcatlib-common:26.1.2-6"       // in the common module
    implementation "net.ixdarklord.coolcatlib:coolcatlib-fabric:26.1.2-6"    // in each loader's module
}
```

Artifacts are `net.ixdarklord.coolcatlib:coolcatlib-<module>:<minecraft>-<version>`, the module being `common`,
`fabric`, `neoforge` or `forge`. On 1.21.1 and older, use `modImplementation` for the loader jars. The
[Maven's page](https://ixdarklord.github.io/ModResources/) lists every version, ready to copy.

## 🛠️ Building

Check out the branch for your Minecraft version, then run:

```bash
./gradlew build
```

The jars end up in each loader's `build/libs`. The build scripts and dependency versions come from
[ModResources](https://github.com/ixDarkLorD/ModResources), and each release is published to its Maven automatically.

## 🐞 Issues and ideas

Found a bug or have an idea? Open an [issue](https://github.com/ixDarkLorD/CoolCatLib/issues) with the bug report or
feature request template, or drop by the [Discord](https://discord.gg/ZapGEPqm2V).

## 📄 License

CoolCatLib is under the [MPL-2.0](LICENSE).
