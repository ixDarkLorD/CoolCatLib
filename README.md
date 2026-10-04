<h1 align="center">CoolCatLib: Core & CoolCatLib: Canvas</h1>
<p align="center"><b>Minecraft library mods.</b></p>
<p align="center">
  Core: <a href="https://www.curseforge.com/minecraft/mc-mods/coolcatlib">CurseForge</a> / <a href="https://modrinth.com/mod/ASkaoGC8">Modrinth</a> ·
  Canvas: <a href="https://www.curseforge.com/minecraft/mc-mods/coolcatlib-canvas">CurseForge</a> / <a href="https://modrinth.com/mod/NtytwOvv">Modrinth</a> ·
  <a href="https://ixdarklord.github.io/CoolCatLib/">Documentation</a>
</p>
<hr>

| Mod | Folder | Mod id | What it holds |
|---|---|---|---|
| **CoolCatLib: Core** | `core/` | `coolcatcore` | Mod constructors, events, networking, registries, configs (and their screens), attachments, containers, menus and utilities. |
| **CoolCatLib: Canvas** | `canvas/` | `coolcatcanvas` | Screen effects, skyboxes, GUI widgets and render utilities. Requires CoolCatLib: Core. |

Each mod has `common`, `fabric` and `forge` modules and its own `CHANGELOG.md`; `testmods/` holds the Fabric test mod,
which runs with both. Both mods' attributes are in `gradle.properties` (Canvas's with a `canvas_`
prefix). `canvas/canvas.gradle` sets up Canvas's
projects and `testmods/testmod.gradle` the test mods'.

<p align="center"> <img src="https://i.imgur.com/iQlGHUt.png" alt="">
