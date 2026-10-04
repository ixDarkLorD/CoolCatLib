<h1 align="center">CoolCatLib: Core & CoolCatLib: Canvas</h1>
<p align="center"><b>Minecraft library mods. Downloads can be found on <a href="https://www.curseforge.com/minecraft/mc-mods/coolcatlib">Curseforge!</a></b></p>
<hr>

| Mod | Folder | Mod id | What it holds |
|---|---|---|---|
| **CoolCatLib: Core** | `core/` | `coolcatcore` | Mod constructors, events, networking, registries, configs, attachments, containers, menus and utilities. Its configs show in [Glazed Menu](https://github.com/ixDarkLorD/GlazedMenu)'s screens, or in Configured's. |
| **CoolCatLib: Canvas** | `canvas/` | `coolcatcanvas` | Screen effects, skyboxes, GUI widgets (the styled widgets and theme effects config screens use) and render utilities. Requires CoolCatLib: Core. |

Each mod has `common`, `fabric` and `neoforge` modules and its own `CHANGELOG.md`; `testmods/` holds the Fabric and
NeoForge test mods, which run with both and with Glazed Menu (its own project, taken from Maven Local). The mods'
attributes are in `gradle.properties` (Canvas's with a `canvas_` prefix). `addon.gradle` sets up Canvas's projects and
`testmods/testmod.gradle` the test mods'.

<p align="center"> <img src="https://i.imgur.com/iQlGHUt.png" alt="">
