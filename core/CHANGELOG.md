# Changelog
This file is for listing all the changes to this project

## v26.1.2-1 Release | Unreleased (Minecraft 26.1.2 port)
### ✨ New Features
- CoolCatLib is split in two mods: **CoolCat Core** (`coolcatcore`, package `net.ixdarklord.coolcatcore`), this one, and **CoolCat Canvas** (`coolcatcanvas`), which now holds screen effects, skyboxes, GUI widgets, `RenderUtils` and `Easing`. The mod id, package, asset namespace, Fabric entrypoints (`coolcatcore:common`, ...) and commands (`/coolcatcore`, `/coolcatcore_client`) all changed from `coolcatlib`.
- A cross-loader platform layer, so mods built on CoolCat Core no longer need Architectury API:
  - Events (`api.event.v2`): `EventInvoker` keeps listeners in common code, ordered by `EventPhase`, and is found by its listener type (`EventInvoker.lookup(ServerTickEvents.End.class)`) or its constant (`ServerTickEvents.END`). `EventInvoker.create(Type.class)` makes a mod's own event, combining listeners by return type (`void`, `EventResult`, `EventResultHolder`, `boolean`).
  - Built-in events: server lifecycle and ticks, player join/leave/tick, entity load, block break/place, commands; client ticks, player join/leave, HUD rendering, item tooltips, client commands.
  - `api.registry`: `DeferredRegister` / `RegistryEntry`, `CreativeTabs`, `ReloadListeners`.
  - `api.menu.ExtendedMenus`: menu types that open with extra data.
  - `api.network.Network`: serverbound/clientbound payloads and sending.
    - Record payloads without a `StreamCodec`: `Network.registerServerbound(MyPayload.class, receiver)` (or with an explicit `Type`) derives the codec from the record's components through `PayloadCodecs`, which knows primitives, strings, common Minecraft types, enums, nested records, arrays, `Optional`, `List`, `Set`, `Map`, `Either` and `ResourceKey`. Other types are added with `PayloadCodecs.register` / `registerFactory`; registering a payload with a component type that has no codec throws right away, naming the component.
  - `api.client.registry`: key mappings, menu screens, tooltip components.
  - `api.platform.Platform`: loader, environment, mod checks, folders, the running server, fake players.
  - Mod constructors (`api.core`): `ModConstructor` (both sides), `ClientModConstructor`, `ServerModConstructor` (dedicated server) and `DataGenerationConstructor` are written once in common code, each with a construct stage and a setup stage (`DataGenerationContext.addProvider` for data providers).
    - Fabric needs no Java entry class: list them under the `coolcatcore:common`, `coolcatcore:client`, `coolcatcore:server` and `coolcatcore:datagen` entrypoints, plus `api.core.fabric.FabricDataGenerationEntrypoint` under `fabric-datagen`.
    - NeoForge: extend `api.core.neoforge.NeoForgeModEntrypoint` in the `@Mod` class and call `common(...)`, `client(() -> ...)`, `server(() -> ...)`, `dataGeneration(...)`; the mod id comes from the container.
- `EventResult.pass()`/`interrupt()`/`allow()`/`deny()`, and `EventResultHolder.result()`/`getValue()`.
- A config system (`api.config`):
  - Declare configs with `Config.builder(modId, scope)` (typed values, nested groups, presets) or from an annotated class with `ConfigObject.register`.
  - Scopes: `CLIENT`, `COMMON`, `SERVER` (synced to players) and `WORLD` (stored per world, seeded from `defaultconfigs`, synced).
  - Types: booleans, ranged numbers (optionally sliders), strings (length/pattern), enums, colors, ids, lists, and any `Codec`; custom `ConfigType`s with their own editors.
  - TOML (default) and JSON5 files with generated comments; a config whose format changes converts its old file once (kept as `.bak`); broken files are backed up; `version`/`migration` and `aliases` handle renamed or moved settings.
  - Hot reloading of edited files, change listeners and `ConfigEvents`: `LOADED` (first read), `RELOADED` (hot reload, the reload command or `Config.reload()`), `UNLOADING` (a world config before its server's values are dropped), `VALUE_CHANGED` (each value, with its old and new value), `CHANGED` (once per batch), `SAVED` and `SYNCED`.
  - `serverOnly()` values that never leave the server; operators edit synced configs in-game with permission checks.
  - Generated config screen: search, undo/redo, reset, presets, validation, dependencies (`enabledWhen`), restart notices and list editing. Linked from the NeoForge mod list and Mod Menu.
  - `/coolcatcore config` (server) and `/coolcatcore_client config` (client) commands: list, get, set, reset, reload, preset, open.
  - `STARTUP` configs, read before content is registered (for item properties, which items exist...). Their values stay fixed until a restart; edits are saved for the next start. Joining a server compares them before entering the world: values must match by default (the client is offered to adopt the server's), `useServerValue()` values are taken from the server while connected, and `localOnly()` values stay per side.
  - Category popups: one category of a config in a small window with Save and Cancel, floating over the current screen or the game, e.g. `ConfigScreens.openCategory("mymod", "client/rendering")`, `ConfigScreens.categoryPopup(parent, config, path)`, or `/coolcatcore_client config open <mod> <category>` (with path suggestions). A single setting's path shows just that setting.
  - A color picker for color values (a popup with saturation/brightness, hue and alpha, hex input and dye colors), also available as `ConfigScreens.colorPicker`.
  - The main config screen: a header with the mod's icon (a "?" when it has none; the list of every mod's configs has its own icon), and each config as a portrait navigation card with its own artwork (faint, brightening on hover; CoolCat Core's per scope, or the mod's own at `assets/<modid>/textures/gui/config/cards/<config name>.png`), its scope icon and a short label like "Server" (`config.<modid>.<name>.label`). Cards are centered, grow when hovered, and show a tooltip with the config's scope, contents, file and access. Mod Menu's CoolCat Core button opens CoolCat Core's own settings.
  - Page transitions: config pages slide in from the right when opened, from the left when going back, and rise into place when opened from elsewhere, fading in from the backdrop.
  - Redesigned config screens: a top bar (title, scope badges, search), a category sidebar, settings with descriptions and nested sections, and a bottom bar with the unsaved-changes status and actions; toggle switches, sliders, selectors and flat buttons; presets, confirmations and the color picker as popups. Icons are 64x64 textures in `assets/coolcatcore/textures/gui/config/icons/` (smoothly filtered), replaceable by resource packs.
  - `ConfigTheme`: each mod's own look, set per mod (`ConfigTheme.setForMod`) or per config (`ConfigBuilder.theme` / `background`): a full `ConfigColorScheme` (presets `DARK`, `LIGHT`, and `tinted(accent)` for the dark scheme shaded toward the mod's color), a background texture by its path (cover, stretch or tile), the texture's opacity (`textureOpacity`, letting the panorama or world show through) and the backdrop color's opacity over the background (`backgroundOpacity`). Players can scale both for every mod in CoolCat Core's client config (Background > Backdrop Opacity / Texture Opacity). By default the screens are see-through to the title panorama or the world. Resource packs can restyle a mod with `assets/<modid>/coolcatcore/config_theme.json`.
  - The config list shows each mod's icon (a theme can set another); config pages and their cards show an icon of their kind (client, common, server, world, startup).
  - `Platform.getModName`.
  - Dark and light mode: a sun/moon switch in every config screen's top bar, remembered in CoolCat Core's own client config (`themeMode`). Each theme has a light scheme too (`ConfigTheme.Builder.lightColors`, by default derived from the mod's accent; `light_base`/`light_colors` in `config_theme.json`).
  - The main config screen's search bar searches every config it lists (names, keys, comments, group names, values), with results grouped by config; opening a result shows that config filtered to it. A config's own search covers only that config.
- Attachments, containers and handlers for items, blocks and entities:
  - `api.attachment`: typed data any entity (players included), block entity or item stack can carry. Each mod creates its own `AttachmentRegistry.create(modId)` (and calls `register()` while it initialises); its values are stored under its own id, in the `<modid>:attachments` item component and a `<modid>:attachments` tag in entity and block entity data. Declared once (`ATTACHMENTS.builder(name, codec, default)`), then used as `MADNESS.get(player)`, `set`, `update`, `modify`, `reset`, or through `AttachmentHolder.of(...)`.
    - Persistent attachments save with their holder; `transientBuilder` ones never do. `copyOnDeath()` keeps a player's value through death (always kept through dimension changes and the End exit); `keepOnDrop()` carries a block entity's value into its dropped item and back when placed.
    - `sync(SyncPolicy)`: `SELF` (the player it belongs to), `TRACKING`, `ALL`, or any predicate. Only changed values are sent, at most once per tick; everything when a player starts seeing the holder, receives its chunk, joins, respawns or changes dimension.
    - On item stacks, defaults aren't stored, so stacks still stack.
  - `AttachmentEvents`: `CHANGING` (cancel a change or replace the value), `ADDED`, `CHANGED` (in-place changes included), `REMOVED`, `START_TRACKING`/`STOP_TRACKING` (a player starts or stops receiving a holder's data) and `RECEIVED` (synced values arrived on the client), plus typed per-attachment helpers (`onChanged(MADNESS, ...)`).
  - `api.container`: `ContainerLayout` (slot filters, limits, roles `STORAGE`/`INPUT`/`OUTPUT`/`INTERNAL`, which slots each face reaches) makes `SlotContainer`s, vanilla `Container`s with insert/extract, listeners and layout-aware menu slots. `AttachmentRegistry.container` stores one on any entity or block entity (saved and synced with the rest); `ItemContainers` keeps one in a stack (`ContainerItem` for items that have one); `ItemTransfer` moves items between any containers through faces.
  - `api.block`: `ExtendedBlockEntity` (attachment shortcuts, handlers, `serverTick`/`clientTick` with `ExtendedBlockEntity.ticker`) and `ExtendedContainerBlockEntity` (an inventory hoppers and comparators use; contents spill or, with `keepContentsOnBreak`, stay in the item).
  - `api.handler`: `HandlerType` for anything holders hand out (inventories, energy...), from the holder itself (`HandlerProvider`, `HandlerMap`) or registered providers and fallbacks for blocks, block entities, entities and items. `HandlerTypes.CONTAINER` also finds vanilla containers (double chests as one). `BlockHandlerCache` keeps a lookup until the block changes, its block entity goes, or `HandlerType.invalidate` is called.
  - Other mods reach these containers through each loader's own system, following the layout's filters, roles and limits: NeoForge item handler capabilities (blocks, entities and `ContainerItem` stacks) and Fabric's Transfer API (`ItemStorage.SIDED` for blocks, `ItemStorage.ITEM` for `ContainerItem` stacks).
  - `api.registry.BlockEntityTypes.create`: block entity types from common code.
  - Storage menus (`api.menu.StorageMenuDefinition`): a menu for a block's or an item's inventory, declared once (`StorageMenuDefinition.grid(columns, rows)` or placed slot by slot), registered with `createMenuType` and opened with `open(player, blockEntity, title)` or `open(player, hand, title)`. Slots follow the container's layout and shift-click; the menu closes when the block is removed, the player walks away, or the item leaves its slot (which stays locked meanwhile). `sync(attachment)` and `syncValue(...)` send extra values to the screen while it's open, and `button(id, action)` runs screen buttons on the server. `api.client.gui.screens.StorageScreen` draws any of these menus with no texture, and can be extended.

### ⚙️ Refactoring
- Rewrote the v2 event system (the lookup-based skeleton had no events); `EventInvokerRegistry` was removed.

## v2001.1.0.0 Release | Oct 30, 2025
### ⚙️ Refactoring
- Renamed and reorganized several classes.
- Updated version formatting from X.X.X to MCVR.X.X.X to clearly distinguish between Minecraft release versions.

## v1.1.2 Release | April 11, 2025
- Added new methods to MouseHelper and fixed an issue in RenderUtils

## v1.1.1 (Fabric Hotfix) | March 20, 2025
- Correct implementation of Potion Brewing and Potion Brewing Builder extension interfaces

## v1.1.0 Release | March 19, 2025
- Revamped the Brewing Recipe API
- Added two versions of Events (second version is still unstable)
- Introduced new abstract classes for widgets

## v1.0.1 Release | December 30, 2024
- Changing and Organizing the packages

## v1.0.0 Release | September 21, 2024
- Port to 1.21