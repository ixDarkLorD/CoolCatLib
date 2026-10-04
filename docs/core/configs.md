# Configs

Typed configs saved as TOML (or JSON5), with server sync, hot reload, presets and migrations. Players edit them in game through [Glazed Menu](https://github.com/ixDarkLorD/GlazedMenu) or Configured (see [Editing in game](#editing-in-game)). Packages: `net.ixdarklord.coolcatcore.api.config` (with `annotation`, `type`, `format`) and `api.config.client` for opening the screens.

## Scopes

| `ConfigScope` | Where it lives | Synced |
|---|---|---|
| `CLIENT` | Client only | Never |
| `COMMON` | Both sides, each with its own values | No |
| `SERVER` | The server's file | Yes: sent on join and whenever it changes |
| `WORLD` | Per world, in `<world>/serverconfig` (seeded from `defaultconfigs`) | Yes |
| `STARTUP` | Read before registration; fixed until restart | Compared on join (see [Startup configs](#startup-configs)) |

Files are named `config/<modid>-<scope>.toml` by default.

## Builder style

Build the values as static fields; `push`/`pop` make groups:

```java
public final class MyServerConfig {
    private static final ConfigBuilder BUILDER = Config.builder(MyMod.MOD_ID, ConfigScope.SERVER)
            .comment("Server settings").version(2)
            .migration(1, root -> { /* edit the old JsonObject in place */ });

    public static final ConfigValue<Boolean> PVP = BUILDER.bool("pvp", true)
            .comment("Whether players can hurt each other.").build();

    static { BUILDER.push("players", "Per-player limits"); }
    public static final ConfigValue<Integer> MAX_HOMES = BUILDER.intValue("maxHomes", 3)
            .range(0, 64).slider().aliases("homeLimit").build();
    public static final ConfigValue<List<Identifier>> BANNED = BUILDER.list("bannedItems",
            ConfigTypes.identifier(Registries.ITEM), List.of(Identifier.withDefaultNamespace("tnt"))).maxSize(32).build();
    static { BUILDER.pop(); }

    static { BUILDER.preset("strict", preset -> preset.set(PVP, false).set(MAX_HOMES, 1)); }

    public static final Config CONFIG = BUILDER.build();   // registers and loads it

    public static void init() {}   // call from onConstructMod() to load the class
}
```

Read a value anywhere with `MyServerConfig.MAX_HOMES.get()`. Change it with `set(...)`, then `CONFIG.save()`. Server values that change are synced to every player right away.

Entry types:
- `bool`
- `intValue`, `longValue`, `floatValue`, `doubleValue` (with `range`, `min`, `max`, `slider`)
- `string` (with `maxLength`, `notEmpty`, `pattern`)
- `enumValue`
- `color` (`0xRRGGBB`) and `colorWithAlpha` (`0xAARRGGBB`)
- `identifier` (optionally checked against a registry)
- `list` (with `size`, `minSize`, `maxSize`) and `stringList`
- `value(key, ConfigType, default)` and `codec(key, Codec, default)` for anything else

Every entry also accepts:
- Display: `comment`, `translation`, `hidden`.
- Restarts: `requiresGameRestart`, `requiresWorldRestart`.
- Sync: `serverOnly`, `localOnly`, `useServerValue`.
- Old key names: `aliases`.
- Validation: `validator`.
- Dependencies: `enabledWhen(otherBooleanValue)` greys the entry out in the editor while the other value is off.
- Change callbacks: `listener`.

## Annotation style

```java
public static final ConfigObject<ClientSettings> CLIENT = ConfigObject.register(MyMod.MOD_ID, ConfigScope.CLIENT, new ClientSettings());

@ConfigEntry.Comment("Client settings")
public static final class ClientSettings {
    @ConfigEntry.Comment("Draws the overlay.")
    public boolean showHud = true;

    @ConfigEntry.Range(min = 0, max = 256) @ConfigEntry.Slider @ConfigEntry.EnabledWhen("showHud")
    public int particles = 32;

    @ConfigEntry.Color(alpha = true)
    public int tint = 0x80FF8800;
}

// Read: CLIENT.get().showHud
```

Annotations (in `ConfigEntry`):
- Naming and docs: `@Comment`, `@Key`, `@Aliases`, `@Translation`.
- Numbers: `@Range`, `@Slider`.
- Colors: `@Color`.
- Text and lists: `@MaxLength`, `@Pattern`, `@Size`.
- Restarts: `@RequiresRestart`.
- Sync: `@ServerOnly`, `@LocalOnly`, `@UseServerValue`.
- Editor: `@Hidden`, `@EnabledWhen`.

## Editing in game

Core loads, saves, syncs and hot reloads configs, and has commands to read and change them. It has no screens of its own: since **26.1.2-2** the editor is a separate, client-side mod, so players only install it if they want it.

| Installed | What players get |
|---|---|
| [Glazed Menu](https://github.com/ixDarkLorD/GlazedMenu) | Searchable editor screens with sliders, a color picker, undo/redo, presets and reset buttons, in your mod's theme. They open from its mod list, from `/glazedmenu [mod] [config/category]`, or from your code. |
| Configured (MrCrayfish) | Configured's own screens, from the NeoForge mod list and from Mod Menu on Fabric. Booleans, numbers, enums and text get its widgets; colors, ids and lists are edited as text. Synced configs are edited on the server by players allowed to. |
| Neither | No editor. The files and the `/coolcatcore config` commands (`list`, `get`, `set`, `reset`, `reload`, `preset`) still work. |

With both installed, Glazed Menu's screens show the configs.

!!! note "1.21.1 and 1.20.1"
    On the backports the screens are still part of Core, and `ConfigScreens` always opens them.

Open the screens from code through `ConfigScreens`. It is a bridge: with Glazed Menu installed it opens Glazed Menu's screens, and without it every method opens nothing and returns `null`, so check before you use the result:

```java
Screen screen = ConfigScreens.create(parent, MyMod.MOD_ID);         // the mod's configs, or null
if (screen != null) {
    Minecraft.getInstance().setScreen(screen);
} else {
    ConfigScreens.tellUnavailable(MyConfig.CONFIG);                  // tells the player how to change it without screens
}

ConfigScreens.open(MyMod.MOD_ID);                                   // over the current screen; nothing without screens
ConfigScreens.openCategory(MyMod.MOD_ID, "client/rendering");       // one group, as a popup
boolean hasScreens = ConfigScreens.isAvailable();
```

Names and tooltips are translated as `config.<modid>.<config>.<path>` (and `.tooltip`). Enums can implement `EnumType.Displayable` to give each constant a display name.

### Themes

Give your mod's screens in Glazed Menu their own look from `ClientModConstructor#onConstructMod()`:

```java
ConfigTheme.setForMod(MyMod.MOD_ID, ConfigTheme.builder()
        .colors(ConfigColorScheme.tinted(0xFFA77BFF))
        .background(MyMod.id("textures/gui/config_background.png"))
        .build());
```

`ConfigTheme.Builder` settings:
- Colors: `colors`, `lightColors`, `accent`.
- Pictures: `icon`, `background`, `mode` (`COVER`, `STRETCH`, `TILE`), `tiled`.
- Opacity: `backgroundOpacity`, `textureOpacity`, `backgroundInWorld`.
- `popupSprite`: a nine-slice sprite for category popups.
- `effects`: animated effects by id. They are registered with CoolCatLib: Canvas's `ConfigEffects.register(id, effect)` (`net.ixdarklord.coolcatcanvas.api.client.gui.theme`).

## Startup configs

A `STARTUP` config is read before content is registered, so it can decide which items or recipes exist. When a client joins, its values are compared with the server's:
- `REQUIRE_MATCH`, the default: a client that doesn't match is stopped before it enters the world, and offered the server's values for its next start.
- `useServerValue()` entries (`StartupSync.USE_SERVER`) take the server's value instead.
- `localOnly()` entries are never compared.

## Reference

| Type | Description |
|---|---|
| `Config` | `builder(modId, scope)`, `get(id)`, `all()`, `forMod(modId)`; `save()`, `reload()`, `resetAll()`, `find(path)`, `values()`, `presets()`, `applyPreset(...)`, `addListener(...)`; `isLoaded()`, `isRemote()`, `isRestartPending()`, `filePath()`. |
| `ConfigBuilder` | `name`, `fileName`, `format(ConfigFormats.TOML / JSON5)`, `comment`, `version`, `migration`, `editPermission`, `hotReload`, `theme`, `background`, `effects`, `push`, `pop`, `group`, the entry methods above, `preset`, `build()`. |
| `ConfigValue<T>` | `get()`, `set(T)`, `getStored()`, `getDefault()`, `reset()`, `isDefault()`, `validate(T)`, `isActive()`, `isRestartPending()`, `addListener((old, new) -> ...)`. |
| `ConfigGroup`, `ConfigNode` | The tree: `children()`, `child(key)`, `values()`, `key()`, `path()`, `displayName()`. |
| `ConfigScope`, `StartupSync`, `RestartRequirement` | The enums described above. |
| `ConfigPreset` | Named value sets: `builder.preset(name, preset -> preset.set(value, x))`. |
| `ConfigEvents` | `LOADED`, `RELOADED`, `UNLOADING`, `VALUE_CHANGED`, `SAVED`, `CHANGED`, `SYNCED` (client). |
| `ConfigTheme`, `ConfigColorScheme` | The look of the mod's screens in Glazed Menu: `ConfigColorScheme.DARK`, `LIGHT`, `tinted(accent)`, `tintedLight(accent)`, or a builder over every color. |
| `annotation.ConfigObject`, `annotation.ConfigEntry` | The annotation style. |
| `type.ConfigTypes` | `BOOLEAN`, `INT`, `LONG`, `FLOAT`, `DOUBLE`, `STRING`, `COLOR`, `COLOR_ALPHA`, `IDENTIFIER`; `intRange`, `doubleRange`, `string(maxLength)`, `pattern`, `enumOf`, `identifier(registry)`, `listOf`, `codec`. |
| `type.EnumType.Displayable` | Implement on an enum to name its constants in the editor. |
| `format.ConfigFormats` | `TOML` (default), `JSON5`. |
| `client.ConfigScreens` | The bridge to Glazed Menu's screens (each returns `null` or does nothing without it): `isAvailable()`, `create(parent, modId)`, `create(parent, config)`, `createModList(parent)`, `categoryPopup(parent, config, path[, theme])`, `open(modId)`, `openCategory(modId, path)`, `colorPicker(...)`, `hasConfigs(modId)`; `unavailableMessage(config)`, `tellUnavailable(config)`. |
| Canvas: `api.client.gui.theme.ConfigEffects`, `ConfigEffect` | Animated screen backgrounds and widget effects (moved from Core's `api.config.client` in 26.1.2-2). |
| Glazed Menu: `net.ixdarklord.glazedmenu.api.editor.ConfigEditors`, `ValueEditor`, `EditSlot` | Custom editor widgets for your own config types (moved from Core's `api.config.client` in 26.1.2-2). |
