<h1 align="center">CoolCat Core & CoolCat Canvas</h1>
<p align="center"><b>Minecraft library mods. Downloads can be found on <a href="https://www.curseforge.com/minecraft/mc-mods/coolcatlib">Curseforge!</a></b></p>
<hr>

| Mod | Folder | Mod id | What it holds |
|---|---|---|---|
| **CoolCat Core** | `core/` | `coolcatcore` | Mod constructors, events, networking, registries, configs (and their screens), attachments, containers, menus and utilities. |
| **CoolCat Canvas** | `canvas/` | `coolcatcanvas` | Screen effects, skyboxes, GUI widgets and render utilities. Requires CoolCat Core. |

Each mod has `common`, `fabric` and `neoforge` modules and its own `CHANGELOG.md`; `testmods/` holds the Fabric and
NeoForge test mods, which run with both. Both mods' attributes are in `gradle.properties` (Canvas's with a `canvas_`
prefix), and each folder's shared build logic is in `core/core.gradle`, `canvas/canvas.gradle` and
`testmods/testmod.gradle`.

<p align="center"> <img src="https://i.imgur.com/iQlGHUt.png" alt="">
