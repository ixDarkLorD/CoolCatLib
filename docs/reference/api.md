# API Reference

Every public API package and class of CoolCatLib: Core and CoolCatLib: Canvas (26.1.2), with what it's for. Anything under an `internal` package is not API and may change without notice. The guide pages go into detail and have examples.

# CoolCatLib: Core

Package prefix: `net.ixdarklord.coolcatcore.api`.

### `core`: entrypoints ([Getting Started](../getting-started.md))
| Class | Functionality |
|---|---|
| `ModConstructor` | Common entrypoint: `onConstructMod()`, `onCommonSetup()`. |
| `ClientModConstructor` | Client entrypoint: `onConstructMod()`, `onClientSetup()`. |
| `ServerModConstructor` | Dedicated-server entrypoint: `onConstructMod()`, `onServerSetup()`. |
| `DataGenerationConstructor` | Datagen entrypoint: `onGatherData(DataGenerationContext)`. |
| `DataGenerationContext` | `getModId()`, `getRegistries()`, `addProvider(...)`. |
| `core.fabric.FabricDataGenerationEntrypoint` | Fabric `fabric-datagen` entrypoint that runs the `coolcatcore:datagen` constructors. |
| `core.neoforge.NeoForgeModEntrypoint` | Base class for a NeoForge `@Mod` class: `common(...)`, `client(...)`, `server(...)`, `dataGeneration(...)`. |
| `core.forge.ForgeModEntrypoint` | The same for Forge (1.21.1 and 1.20.1). |
| `core.commands.ArgumentTypeRegistry` | Registers custom command argument types. |

### `event.v2.core`: the event system ([Events](../core/events.md))
| Class | Functionality |
|---|---|
| `EventInvoker<T>` | One event: `register`, `unregister`, `invoker()`; `lookup(Class)`, `create(Class)`. |
| `EventPhase` | Listener ordering: `FIRST`, `EARLY`, `DEFAULT`, `LATE`, `LAST`. |
| `EventResult` | `PASS`, `INTERRUPT`, `ALLOW`, `DENY`. |
| `EventResultHolder<T>` | A result carrying a value. |

### `event.v2.common`: server and common events ([Events](../core/events.md))
| Class | Events |
|---|---|
| `ServerLifecycleEvents` | `STARTING`, `STARTED`, `STOPPING`, `STOPPED`, `SYNC_DATA_PACK_CONTENTS` |
| `ServerTickEvents` | `START`, `END`, `START_LEVEL`, `END_LEVEL` |
| `PlayerEvents` | `JOIN`, `LEAVE`, `START_TICK`, `END_TICK` |
| `EntityEvents` | `LOAD` |
| `BlockEvents` | `BREAK` (cancellable), `PLACED` |
| `CommandEvents` | `REGISTER` |
| `AttachmentEvents` | `CHANGING`, `ADDED`, `CHANGED`, `REMOVED`, `START_TRACKING`, `STOP_TRACKING`, `RECEIVED` |

### `event.v2.client`: client events ([Events](../core/events.md))
| Class | Events |
|---|---|
| `ClientTickEvents` | `START`, `END` |
| `ClientPlayerEvents` | `JOIN`, `LEAVE` |
| `ClientGuiEvents` | `RENDER_HUD` |
| `ItemTooltipEvents` | `MODIFY` |
| `ClientCommandEvents` | `REGISTER`; `sendFeedback`, `sendError` |

### `event.v1`: legacy events
| Class | Functionality |
|---|---|
| `Event<T>` | A simple consumer event. |
| `server.RegisterBrewingRecipesEvent` | Adds brewing recipes through a `BrewingBuilder`. |

### `registry` ([Registration](../core/registration.md))
| Class | Functionality |
|---|---|
| `DeferredRegister<T>` | Cross-loader deferred registration. |
| `RegistryEntry<T>` | A registered object's supplier, id and holder. |
| `CreativeTabs` | A creative tab builder the loader places. |
| `ReloadListeners` | Server data reload listeners. |
| `BlockEntityTypes` | Creates `BlockEntityType`s. |

### `client.registry` ([Registration](../core/registration.md))
| Class | Functionality |
|---|---|
| `KeyMappingRegistry` | Registers key mappings. |
| `MenuScreenRegistry` | Binds screens to menu types. |
| `TooltipComponentRegistry` | Binds client tooltip components to tooltip data. |

### `item` and `client.gui` ([Item Decorators](../core/item-decorators.md))
| Class | Functionality |
|---|---|
| `item.DecoratedItem` | Implement on an item's class to draw over it in GUIs; Core registers its decorators by itself. |
| `client.gui.ItemDecorator` | Draws over an item in a slot, after vanilla's bar, cooldown and count. |

### `network` ([Networking](../core/networking.md))
| Class | Functionality |
|---|---|
| `Network` | Registers payloads (with or without a codec), sends them, and checks `canPlayerReceive`. |
| `PayloadReceiver<T>` | A payload handler. |
| `PacketContext` | The sending player, `queue(...)`, the side. |
| `PayloadCodecs` | Derives stream codecs for record payloads; register your own types. |

### `config` ([Configs](../core/configs.md))
| Class | Functionality |
|---|---|
| `Config` | A loaded config: save, reload, reset, find values, presets, listeners. |
| `ConfigBuilder` | Builds a config: groups, entries, presets, migrations, format, theme. |
| `AbstractEntryBuilder`, `EntryBuilder`, `NumberEntryBuilder`, `StringEntryBuilder`, `ListEntryBuilder` | Entry options: comments, ranges, sliders, validation, sync, restart rules, dependencies. |
| `ConfigValue<T>` | One value: `get`, `set`, `reset`, listeners. |
| `ConfigNode`, `ConfigGroup` | The config tree. |
| `ConfigScope` | `CLIENT`, `COMMON`, `SERVER`, `WORLD`, `STARTUP`. |
| `StartupSync` | `REQUIRE_MATCH`, `USE_SERVER`. |
| `RestartRequirement` | `NONE`, `WORLD`, `GAME`. |
| `ConfigPreset` | Named sets of values. |
| `ConfigMigration` | Upgrades an old file's JSON. |
| `ConfigDependency` | "Enabled when" rules between values. |
| `ConfigEvents` | `LOADED`, `RELOADED`, `UNLOADING`, `VALUE_CHANGED`, `SAVED`, `CHANGED`, `SYNCED`. |
| `ConfigTheme` | The look of the mod's screens in Glazed Menu: colors, background, icon, popup sprite, effects. |
| `ConfigColorScheme` | Screen color palettes: `DARK`, `LIGHT`, `tinted(accent)`. |
| `annotation.ConfigObject`, `annotation.ConfigEntry` | Annotation-based configs. |
| `type.ConfigType`, `type.ConfigTypes` | Value types: numbers, strings, colors, identifiers, enums, lists, codecs. |
| `type.BooleanType`, `NumberType`, `StringType`, `EnumType`, `ColorType`, `IdentifierType`, `ListType`, `CodecType` | The concrete types. `EnumType.Displayable` names enum constants in the editor. |
| `type.ValidationResult` | Ok, corrected or error. |
| `format.ConfigFormat`, `format.ConfigFormats`, `format.ConfigDocument`, `format.ConfigFormatException` | File formats: `TOML` (default), `JSON5`. |
| `client.ConfigScreens` | The bridge to Glazed Menu's config screens, category popups and color picker; `isAvailable()`, `tellUnavailable(config)` without it. |

### `attachment` ([Attachments](../core/attachments.md))
| Class | Functionality |
|---|---|
| `AttachmentRegistry` | A mod's attachments: builders and registration. |
| `Attachment<T>` | One attachment: get, set, update, modify, reset on entities, block entities and stacks. |
| `Attachment.Builder<T>` | Sync, network codec, copy on death, keep on drop, change listeners. |
| `AttachmentHolder` | Uniform access to any holder. |
| `SyncPolicy` | `NONE`, `SELF`, `TRACKING`, `ALL`, or your own. |
| `Trackable` | Mutable values that report their own changes. |

### `container` ([Containers and Menus](../core/containers-and-menus.md))
| Class | Functionality |
|---|---|
| `ContainerLayout` | Slot count, filters, limits, roles and faces. |
| `SlotRole` | `STORAGE`, `INPUT`, `OUTPUT`, `INTERNAL`. |
| `SlotContainer` | A rule-aware `Container` with insert/extract. |
| `ContainerSlot` | A menu slot enforcing the layout. |
| `ContainerItem`, `ItemContainers` | Items that hold an inventory. |
| `ItemTransfer` | Automation-style insert, extract and move. |
| `SidedContainerView` | A container seen from one face. |

### `handler` ([Containers and Menus](../core/containers-and-menus.md))
| Class | Functionality |
|---|---|
| `HandlerType<T>` | Lookups of a capability-like handler on blocks, entities and items. |
| `HandlerTypes` | Built-in `CONTAINER` lookup. |
| `HandlerProvider`, `ItemHandlerProvider` | Expose handlers from your own objects. |
| `HandlerMap` | Stores a holder's handlers, per side. |
| `BlockHandlerCache<T>` | A cached lookup. |

### `block` ([Containers and Menus](../core/containers-and-menus.md))
| Class | Functionality |
|---|---|
| `ExtendedBlockEntity` | Block entity with attachment shortcuts, handlers and ticking. |
| `ExtendedContainerBlockEntity` | Block entity holding a `SlotContainer` as a `WorldlyContainer`. |

### `menu` and `client.gui.screens` ([Containers and Menus](../core/containers-and-menus.md))
| Class | Functionality |
|---|---|
| `ExtendedMenus` | Menu types that open with extra data. |
| `StorageMenuDefinition` | Declarative storage menus: slots, synced values, buttons. |
| `StorageMenu` | The menu built from a definition. |
| `client.gui.screens.StorageScreen` | A texture-free screen for storage menus. |

### `platform` and `hooks` ([Platform and Utilities](../core/platform-and-utilities.md))
| Class | Functionality |
|---|---|
| `platform.Platform` | Loader, environment, mods, folders, server, fake players. |
| `platform.Env` | `CLIENT`, `SERVER`. |
| `hooks.ServerLifecycleHooks` | The current server. |

### `brewing` ([Platform and Utilities](../core/platform-and-utilities.md))
| Class | Functionality |
|---|---|
| `IBrewingRecipe`, `BrewingRecipe` | Custom brewing recipes. |
| `BrewingBuilder` | Adds recipes, containers and mixes. |
| `BrewingRecipeRegistry` | The registered brewing recipes. |

### `datagen.language` and `datagen` ([Platform and Utilities](../core/platform-and-utilities.md))
| Class | Functionality |
|---|---|
| `LanguageProvider` | Language files from common code. |
| `AbstractLanguageWrapper` | Base for the loader wrappers. |
| `FabricLanguageWrapper` / `NeoForgeLanguageWrapper` | Runs a `LanguageProvider` on each loader. |

### `item` and `data` ([Platform and Utilities](../core/platform-and-utilities.md))
| Class | Functionality |
|---|---|
| `item.ComponentItem` | Items with a category line and Shift tooltips. |
| `data.DataComponent`, `data.ItemDataComponent` | Base classes for data-component values. |

### `utils` and `client.utils` ([Platform and Utilities](../core/platform-and-utilities.md))
| Class | Functionality |
|---|---|
| `CodecUtils` | Codec and NBT helpers. |
| `ColorUtils` | RGB color math. |
| `ComponentHelper` | Component conversion, limiting and splitting. |
| `ContainerHelper` | Search containers and inventories. |
| `SlotReference` | A reference to one inventory slot. |
| `ChatFormattingUtils`, `ChatFormattingMapping` | Colors by progress. |
| `JsonUtils` | Deep-merges JSON. |
| `MathUtils` | Cycling values. |
| `ParticleTypes` | Creates particle types. |
| `RecipeHelper` | Recipe matching helpers. |
| `ValueConverter` | String parsing and formatting. |
| `KeysUtils` | Key-combination checks. |
| `client.utils.MouseHelper` | Mouse position checks. |

# CoolCatLib: Canvas

Package prefix: `net.ixdarklord.coolcatcanvas.api`.

### `client.effect` ([Canvas Screen Effects](../canvas/screen-effects.md))
| Class | Functionality |
|---|---|
| `ScreenEffects` | Registers, finds and removes screen effects, makes tints (`tint(id, argb)`), and opens the effects screen. |
| `ScreenEffect` | One effect: fades, conditions, strength, uniforms, layers. |
| `ScreenEffectDefinition` | Post-effect definitions in code or JSON. |
| `EffectContext` | The frame an effect draws in. |
| `EffectUniform` | One uniform: set, animate, bind. |
| `ScreenEffectStage` | `WORLD`, `SCREEN`. |
| `ScreenEffectLayers` | The draw order. |

### `effect` and `event.v2.client` ([Canvas Screen Effects](../canvas/screen-effects.md))
| Class | Functionality |
|---|---|
| `effect.ScreenEffectControl` | Drives a player's screen effects from the server, tints included. |
| `event.v2.client.ScreenEffectEvents` | `BEFORE_TOGGLE`, `TOGGLED`, `STRENGTH_CHANGED`, `UNIFORM_CHANGED`, `LAYERS_CHANGED`, `LOADED`. |

### `client.sky` and `sky` ([Canvas Skyboxes](../canvas/skyboxes.md))
| Class | Functionality |
|---|---|
| `Skyboxes` | Registers and finds skyboxes. |
| `Skybox` | One skybox: fades, conditions, hidden vanilla parts, layers. |
| `SkyLayer` | Runtime control of one layer: visibility, alpha, tint, params. |
| `SkyboxDefinition` | A skybox in code or JSON. |
| `SkyLayerDefinition` | A layer: type, texture, shader, blend, rotation, animation, fades. |
| `SkyLayerType` | `CUBEMAP`, `PANORAMA`, `SPRITE`, `GRADIENT`, `SHADER`. |
| `SkyBlend` | `ALPHA`, `ADDITIVE`, `MULTIPLY`, `SCREEN`. |
| `SkyLayerStage` | `BEHIND_CELESTIALS`, `ABOVE_CELESTIALS`. |
| `SkyClock` | Rotation and animation clocks. |
| `VanillaSky` | Vanilla sky parts that can be hidden. |
| `SkyContext` | The frame a skybox draws in. |
| `sky.SkyboxControl` | Drives players' skyboxes from the server. |

### `client.utils` ([Canvas Text and Colors](../canvas/text-and-colors.md), [Canvas GUI](../canvas/gui.md))
| Class | Functionality |
|---|---|
| `TextEffects` | Gradient, rainbow and outlined text. |
| `Outline` | Solid or gradient outlines. |
| `RenderUtils` | Shapes, gradients, text and nine-slice blits. |
| `NineSliceInfo` | Nine-slice parameters. |
| `ScreenAnchor` | Screen anchoring. |

### `utils` ([Canvas Text and Colors](../canvas/text-and-colors.md))
| Class | Functionality |
|---|---|
| `ColorGradient` | Animated gradients and rainbows for text and outlines. |
| `Easing` | Easing curves. |

### `client.gui.components` ([Canvas GUI](../canvas/gui.md), [Canvas Text and Colors](../canvas/text-and-colors.md))
| Class | Functionality |
|---|---|
| `ColorableImageButton` | A tintable image button. |
| `ElementOutlines` | Outlines attached to any widget. |
| `animations.AnimatedComponent`, `animations.SlideAnimation` | Timeline and slide-in animations. |
| `widgets.MovableElement` | Anything that can be moved. |
| `widgets.AbstractDraggableWidget` | A draggable window with its own children. |
| `widgets.AbstractScrollableWidget` | A draggable window with scrolling. |
| `widgets.AbstractMultiPanelWidget` | A window of stacked panels (experimental). |
| `widgets.panel.Panel`, `ScrollPanel`, `ViewportPanel` | Panel types: plain, scrolling, pan and zoom (experimental). |

### Shader includes ([Canvas Screen Effects](../canvas/screen-effects.md), [Canvas Skyboxes](../canvas/skyboxes.md))
| Include | Functionality |
|---|---|
| `coolcatcanvas:screen_effect.glsl` | `Strength`, `Time`, `Age` and `Seed` for screen effect shaders, plus luminance, noise and edge helpers. |
| `coolcatcanvas:sky.glsl` | Layer inputs, the `SkyLayerInfo` block and `sky_finish` for sky layer shaders. |
