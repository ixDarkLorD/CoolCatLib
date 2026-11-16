---
icon: material/console-line
description: Server and client commands under your mod's names, and the config command
---

# Commands

A mod's commands, registered under its own names, with aliases that work the same, and client commands that can share those names. Every mod with CoolCatLib configs also gets a `config` command under them. Package: `net.ixdarklord.coolcatcore.api.command`.

!!! info "Since"
    CoolCatLib **9.0.0**, on every version. Before it, commands were registered through `CommandEvents.REGISTER` and `ClientCommandEvents.REGISTER`, which still work (see [Moving over](#moving-over)).

## Server commands

`ModCommands.register` takes your mod's id, the command's names (the first its own, the others aliases) and a builder that adds the command's parts to its root:

```java
// In ModConstructor#onConstructMod():
ModCommands.register(MyMod.MOD_ID, List.of("ultimine_addition", "ua"), (root, context, selection) -> root
        .then(Commands.literal("reload")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))   // 1.21.1, 1.20.1: source -> source.hasPermission(2)
                .executes(command -> reload(command.getSource())))
        .then(Commands.literal("stats")
                .executes(command -> showStats(command.getSource()))));
```

- Each name gets the whole command, not a redirect, so `/ua` alone runs like `/ultimine_addition` alone.
- A name another command already has (vanilla's `/tm`, another mod's) is skipped, with a warning in the log; the other names still work.
- The builder runs each time the server makes its commands (on start and on `/reload`), once for each name. `context` is for arguments that need registries, and `selection` says which server the commands are for.
- Register while your mod is constructed. A single name works too: `ModCommands.register(modId, "mymod", builder)`.

!!! tip "Permissions"
    Put permissions on the command's parts rather than on `root`. The [config command](#the-config-command) joins your root, and a permission there keeps it from players too.

## Client commands

`ModCommands.registerClient` registers a command that runs on the player's own game and never reaches the server, whatever the server has. It's safe to call on a dedicated server, where it does nothing.

```java
ModCommands.registerClient(MyMod.MOD_ID, "mymod_client", (root, context) -> root
        .then(LiteralArgumentBuilder.<SharedSuggestionProvider>literal("guide")
                .executes(command -> openGuide())));
```

The loaders' client command sources differ, so client commands are built on `SharedSuggestionProvider`. Reply with `ClientCommandEvents.sendFeedback(component)` and `sendError(component)`.

Instead of names of its own, a client command can take its server command's, with `ClientNames`:

| `ClientNames` | Names | For |
|---|---|---|
| `MERGED` | The server command's own: one `/folios` for players. | What the client's parts can run stays on the client (`/folios guide`), the rest goes to the server (`/folios config`). |
| `SUFFIXED` | Each with `_client` after it: `/ua_client` beside `/ua`. | A command of its own beside the server's. |

```java
ModCommands.registerClient("folios", ClientNames.MERGED, (root, context) -> root
        .then(LiteralArgumentBuilder.<SharedSuggestionProvider>literal("guide").executes(command -> openGuide())));
```

A mod without a server command uses its id. When a merged client command and the server command both have a part of the same name, the client's is the one that runs, so give the two different parts.

## The config command

Every mod with CoolCatLib [configs](configs.md) gets `config` under each name of its server command, with nothing to register:

| Command | Who | What it does |
|---|---|---|
| `/<command> config` | Anyone | Opens the mod's config screens. |
| `/<command> config <config>` | Anyone | Opens one config's screen: `/folios config client`. |
| `/<command> config reload [config]` | Operators | Reads the mod's config files again after they were edited by hand: the server's configs on the server, the player's client configs on their own game. |
| `/coolcatlib config [mod] [config]` | Anyone | The same for any mod, mods without commands of their own included. |
| `/coolcatlib config reload [mod] [config]` | Operators | The same. |

Anyone can open the screens; what they may change is still up to each config's edit permission. On a server without CoolCatLib, `/coolcatlib config` runs on the player's own game.

!!! note "Before 9.0.0"
    The config commands also got, set and reset values and applied presets from the chat (`/coolcatlib config <id> get|set|reset|preset`); that is done in the screens now. `/coolcatlib_client` is gone: its config command is part of `/coolcatlib`, and Canvas's development commands are `/coolcatcanvas effects` and `/coolcatcanvas cutscene ...`.

## Moving over

`CommandEvents.REGISTER` and `ClientCommandEvents.REGISTER` are deprecated. Commands registered through them still work and still get the config command: they're matched to the mod whose configs are built in the same packages. Moving to `ModCommands` names the commands as your mod's for certain, and gives you aliases:

```java
// Before:
CommandEvents.REGISTER.register((dispatcher, context, selection) ->
        dispatcher.register(Commands.literal("mymod").then(...)));

// Now:
ModCommands.register(MyMod.MOD_ID, "mymod", (root, context, selection) -> root.then(...));
```

## Reference

| Type | Description |
|---|---|
| `ModCommands` | `register(modId, names, builder)` (a list of names, or one), `registerClient(modId, names, clientBuilder)` (a list, one name, or `ClientNames`), `names(modId)`, `clientNames(modId)`. |
| `ModCommands.Builder` | `(LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext context, Commands.CommandSelection selection) -> ...` |
| `ModCommands.ClientBuilder` | `(LiteralArgumentBuilder<SharedSuggestionProvider> root, CommandBuildContext context) -> ...` |
| `ModCommands.ClientNames` | `MERGED`, `SUFFIXED`; `apply(serverName)`. |
| `api.core.commands.ArgumentTypeRegistry` | Custom argument types: see [Registration](registration.md#other-registration-helpers). |
