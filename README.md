<h1 align="center">CoolCatLib</h1>

<p align="center">
  <img src="https://i.imgur.com/iQlGHUt.png" alt="CoolCatLib" width="160">
</p>

<p align="center">
  <b>Library mods for Minecraft: write it once, run it on every loader.</b>
</p>

<p align="center">
  Core: <a href="https://www.curseforge.com/minecraft/mc-mods/coolcatlib">CurseForge</a> / <a href="https://modrinth.com/mod/ASkaoGC8">Modrinth</a> ·
  Canvas: <a href="https://www.curseforge.com/minecraft/mc-mods/coolcatlib-canvas">CurseForge</a> / <a href="https://modrinth.com/mod/NtytwOvv">Modrinth</a> ·
  <a href="https://ixdarklord.github.io/CoolCatLib/">Documentation</a> ·
  <a href="https://github.com/ixDarkLorD/CoolCatLib/issues">Issues</a>
</p>

<hr>

## About

CoolCatLib is two mods. Mods built on them share one codebase across Fabric, NeoForge and Forge.

| Mod | Mod id | What it holds |
|---|---|---|
| **CoolCatLib: Core** | `coolcatcore` | Mod constructors, events, networking, registries, configs (with in-game config screens), attachments, containers, menus and utilities. |
| **CoolCatLib: Canvas** | `coolcatcanvas` | Screen effects, custom skies, GUI widgets and render utilities. Requires CoolCatLib: Core. |

Players only need them when another mod requires them. For mod developers, the
[documentation](https://ixdarklord.github.io/CoolCatLib/) covers the API.

## Downloads

| Mod | CurseForge | Modrinth |
|---|---|---|
| **CoolCatLib: Core** | [coolcatlib](https://www.curseforge.com/minecraft/mc-mods/coolcatlib) | [CoolCatLib: Core](https://modrinth.com/mod/ASkaoGC8) |
| **CoolCatLib: Canvas** | [coolcatlib-canvas](https://www.curseforge.com/minecraft/mc-mods/coolcatlib-canvas) | [CoolCatLib: Canvas](https://modrinth.com/mod/NtytwOvv) |

## Versions

Each Minecraft version lives on its own branch. Discontinued versions get no more updates or fixes.

| Minecraft | Loaders | Branch | Status | Mods |
|---|---|---|---|---|
| 26.1 – 26.1.2 | NeoForge, Fabric | [`26.1.2`](https://github.com/ixDarkLorD/CoolCatLib/tree/26.1.2) | Supported | Core, Canvas |
| 1.21.1 | NeoForge, Forge, Fabric | [`1.21`](https://github.com/ixDarkLorD/CoolCatLib/tree/1.21) | Supported | Core, Canvas |
| 1.20.1 | Forge, Fabric | [`1.20.1`](https://github.com/ixDarkLorD/CoolCatLib/tree/1.20.1) | Supported | Core, Canvas |
| 1.19.2 | Forge, Fabric | [`1.19.2`](https://github.com/ixDarkLorD/CoolCatLib/tree/1.19.2) | Discontinued | CoolCatLib (before the split) |
| 1.18.2 | Forge, Fabric | [`1.18.2`](https://github.com/ixDarkLorD/CoolCatLib/tree/1.18.2) | Discontinued | CoolCatLib (before the split) |

The Fabric versions also need Fabric API.

## Building

Check out the branch for your Minecraft version, then run:

```bash
./gradlew build
```

Each mod's jars end up in its loader folders, e.g. `core/fabric/build/libs` and `canvas/neoforge/build/libs`.

## Reporting issues

Found a bug or have an idea? Open an [issue](https://github.com/ixDarkLorD/CoolCatLib/issues) with the bug report or
feature request template.

## License

See [LICENSE](LICENSE).
