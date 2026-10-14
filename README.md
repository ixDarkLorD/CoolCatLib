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
| `net.ixdarklord.coolcatcore` | Mod constructors, events, networking, registries, configs (and their screens), attachments, containers, menus and utilities. |
| `net.ixdarklord.coolcatcanvas` | Screen effects, skyboxes, GUI widgets and render utilities. |

The second package was its own mod, CoolCatLib: Canvas (its releases are in `CHANGELOG-canvas.md`), and the first was
CoolCatLib: Core. Canvas's asset namespace and commands are unchanged; the old
`coolcatcore` and `coolcatcanvas` mod ids are gone.

The `common`, `fabric` and `forge` modules are in the root folder, with the mod's attributes in `gradle.properties`.
`testmods/` holds the Fabric test mod; `testmods/testmod.gradle` sets them up.

<p align="center"> <img src="https://i.imgur.com/iQlGHUt.png" alt="">
