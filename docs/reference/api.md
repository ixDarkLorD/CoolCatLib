---
icon: material/book-open-variant-outline
description: Every public package and class
---

# API Reference

Every public API package and class of CoolCatLib (26.1.2), in its two packages: Core, in the `coolcatlib` mod, and Canvas, in the `coolcatcanvas` mod that comes inside it (artifacts `coolcatlib-<module>` and `coolcatlib-canvas-<module>`), with what it's for. Anything under an `internal` package is not API and may change without notice. The guide pages go into detail and have examples.

# Core

Package prefix: `net.ixdarklord.coolcatcore.api`.

### `core`: entrypoints ([Getting Started](../getting-started.md))
| Class | Functionality |
|---|---|
| `ModConstructor` | Common entrypoint: `onConstructMod()`, `onCommonSetup()`. |
| `ClientModConstructor` | Client entrypoint: `onConstructMod()`, `onClientSetup()`. |
| `ServerModConstructor` | Dedicated-server entrypoint: `onConstructMod()`, `onServerSetup()`. |
| `DataGenerationConstructor` | Datagen entrypoint: `onGatherData(DataGenerationContext)`. |
| `DataGenerationContext` | `getModId()`, `getRegistries()`, `addProvider(...)`. |
| `core.fabric.FabricDataGenerationEntrypoint` | Fabric `fabric-datagen` entrypoint that runs the `coolcatlib:datagen` constructors. |
| `core.neoforge.NeoForgeModEntrypoint` | Base class for a NeoForge `@Mod` class: `common(...)`, `client(...)`, `server(...)`, `dataGeneration(...)`. |
| `core.forge.ForgeModEntrypoint` | The same for Forge (1.21.1 and 1.20.1). |
| `core.commands.ArgumentTypeRegistry` | Registers custom command argument types. |

### `command` ([Commands](../core/commands.md))
| Class | Functionality |
|---|---|
| `ModCommands` | Registers a mod's server and client commands under its names and aliases, which get the config command. |
| `ModCommands.ClientNames` | `MERGED`, `SUFFIXED`: a client command named after its server command. |

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
| `PlayerEvents` | `JOIN`, `LEAVE`, `START_TICK`, `END_TICK`, `CLONE`, `RESPAWN`, `TRAVEL_TO_DIMENSION` (cancellable), `CHANGE_DIMENSION`, `PICKUP_ITEM` (cancellable), `CRAFTED`, `SMELTED`, `ADVANCEMENT_EARNED`, `ITEM_FISHED` (change the catch) |
| `LivingEvents` | `DAMAGE`, `DEATH` (cancellable), `EQUIPMENT_CHANGE` |
| `EntityEvents` | `LOAD`, `UNLOAD` |
| `BlockEvents` | `BREAK`, `PLACE` (cancellable), `PLACED`, `DROPS`, `TRAMPLE` (cancellable) |
| `InteractionEvents` | `USE_BLOCK`, `USE_ITEM`, `USE_ENTITY` (answer with an `InteractionResult`), `ATTACK_BLOCK`, `ATTACK_ENTITY` (cancellable) |
| `RecipeEvents` | `CRAFT_RESULT` (change or empty a crafting result) |
| `CommandEvents` | `REGISTER` (deprecated: `ModCommands`), `EXECUTE` |
| `LootTableEvents` | `MODIFY` (add pools to a loot table as it loads) |
| `AttachmentEvents` | `CHANGING`, `ADDED`, `CHANGED`, `REMOVED`, `START_TRACKING`, `STOP_TRACKING`, `RECEIVED` |

### `event.v2.client`: client events ([Events](../core/events.md))
| Class | Events |
|---|---|
| `ClientTickEvents` | `START`, `END` |
| `ClientPlayerEvents` | `JOIN`, `LEAVE` |
| `ClientGuiEvents` | `RENDER_HUD` |
| `ItemTooltipEvents` | `MODIFY`, `APPEND` |
| `ClientCommandEvents` | `REGISTER` (deprecated: `ModCommands`); `sendFeedback`, `sendError` |

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
| `EntityRendererRegistry`, `BlockEntityRendererRegistry`, `SpecialModelRendererRegistry` | Deprecated: moved to [Canvas's `client.registry`](#clientregistry-renderers-registration). |
| `BlockRenderLayers` | Draws a block's model in the cutout or translucent layer (1.21.1 and 1.20.1). |

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
| `client.ConfigEffects`, `client.ConfigEffect` | Animated config screen backgrounds and widget effects, by id. |

### `attachment` ([Attachments](../core/attachments.md))
| Class | Functionality |
|---|---|
| `AttachmentRegistry` | A mod's attachments: builders and registration. |
| `Attachment<T>` | One attachment: get, set, update, modify, reset on entities, block entities, stacks, levels and the server; the server's value on either side (`getGlobal`); offline players' (`getOffline`, `setOffline`, `updateOffline`, `resetOffline`). |
| `Attachment.Builder<T>` | Sync, network codec, copy on death, keep on drop, change listeners. |
| `AttachmentHolder` | Uniform access to any holder. |
| `SyncPolicy` | `NONE`, `SELF`, `TRACKING`, `ALL`, or your own. |
| `Trackable` | Mutable values that report their own changes. |

### `gametest` ([Game Tests](../core/game-tests.md))
| Class | Functionality |
|---|---|
| `GameTests` | Registers game tests from common code: a class's `@GameTest` methods, or one test by hand. |
| `GameTest` | Marks a method as a game test, with its options. |
| `GameTestOptions` | How a test runs: structure, time limit, rotation, retries and more; `PLATFORM`, CoolCatLib's test structure. |

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

# Canvas

Package prefix: `net.ixdarklord.coolcatcanvas.api`.

### `client.registry`: renderers ([Registration](../core/registration.md#renderers))
| Class | Functionality |
|---|---|
| `BlockEntityRendererRegistry` | Registers block entity renderers. |
| `EntityRendererRegistry` | Registers entity renderers. |
| `ItemRendererRegistry` | Items drawn by code: special item model renderer types on 26.x, `builtin/entity` items on 1.21.1 and 1.20.1. |

### `client.effect` ([Canvas Screen Effects](../canvas/screen-effects.md))
| Class | Functionality |
|---|---|
| `ScreenEffects` | Registers, finds and removes screen effects, makes tints (`tint(id, argb)`), and opens the effects screen. |
| `ScreenEffect` | One effect: fades, conditions, strength, uniforms, layers. |
| `ScreenEffectDefinition` | Post-effect definitions in code or JSON. |
| `EffectContext` | The frame an effect draws in. |
| `EffectUniform` | One uniform: set, animate, bind. |
| `ScreenEffectScope` | `WORLD`, `SCREEN`. |
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

### `client.blockentity` ([Canvas Block Entity Renderers](../canvas/block-entity-renderers.md))
| Class | Functionality |
|---|---|
| `BlockRenderer` | Base block entity renderer: culling by distance and view, detail bands, facing, reused states and layers. |
| `BlockRenderState` | Its state: vanilla's, plus detail, distance, time, facing and looked-at. |
| `BlockRenderContext` | What `extract` can use: the camera, model resolvers and eased transitions. |
| `Detail`, `Facing`, `RenderBounds` | Detail levels, facing modes, and the box a renderer is culled by. |
| `BlockRenderLayer` | A layer drawn with a renderer's block entities, with a typed state of its own. |
| `ItemLayer`, `LabelLayer`, `ChildLayer` | Built-in layers: a floating item, text over the block, another block entity's renderer. |

### `client.armature` and `armature` ([Canvas Armatures](../canvas/armatures.md))
| Class | Functionality |
|---|---|
| `client.armature.Armature` | An armature by name, read from Blockbench or Bedrock files. |
| `client.armature.Animator`, `AnimLayer`, `ArmatureContext` | One thing's animation: layers that play and blend clips, and what it's doing. |
| `client.armature.JointMask`, `LayerBlend`, `TransitionRule` | Which joints a layer moves, how it combines, and how its clips meet. |
| `client.armature.LookAt`, `Spring`, `Fabric`, `JointModifier` | Procedural motion: looking, springs, cloth and changes of your own. |
| `client.armature.ArmatureTrail`, `ArmatureEmitter`, `ObjProp` | Wakes, particles and `.obj` models at points of the armature. |
| `client.armature.ArmatureEvent`, `ArmatureMaterial` | A clip's keyframe events; how a joint is drawn. |
| `client.armature.molang.ArmatureQueries` | Molang queries of your own. |
| `client.armature.render.ArmatureMobRenderer`, `ArmatureEntityRenderer`, `ArmatureBlockRenderer` | Renderers for mobs, other entities and block entities. |
| `client.armature.render.ArmatureItems`, `ArmatureAttachment`, `ItemSocket` | Armature items; drawing and items at points. |
| `client.armature.vanilla.VanillaModels`, `VanillaModel` | Clips, part changes and worn armatures on vanilla mobs and players. |
| `armature.Motion`, `Transition`, `LoopMode` | How to play a clip and blend into it, on both sides. |
| `armature.ArmatureControl` | Plays clips and sets variables from the server, seen by every player. |

### `client.model`
| Class | Functionality |
|---|---|
| `ObjModel`, `ObjModels` | Wavefront `.obj` models, read from resource packs. |

### `client.vfx` and `vfx` ([Canvas World Effects](../canvas/world-effects.md))
| Class | Functionality |
|---|---|
| `client.vfx.WorldVfx` | Renderers called every frame (`register`), and one-line `beam`, `flash` and `groundFlash` effects. |
| `client.vfx.VfxMaterial`, `VfxBlend` | What a world effect is drawn with: a texture (built-ins `GLOW`, `SPARKLE`, `STAR`, `RING`, `SMOKE`, `STREAK`), a blend, a softness and an optional fragment shader. |
| `client.vfx.VfxRenderer`, `VfxFrame`, `VfxDraw` | Drawing every frame: billboards, beams, flat squares, quads and trails. |
| `client.vfx.ParticleSpec`, `WorldParticles` | Effect particles: what they look like and how they live, and spawning them (`spawn`, `burst`, `line`). |
| `client.vfx.Curve`, `Trail` | A three-point eased value over a lifetime, and the path something moved along. |
| `client.vfx.VfxEffect`, `VfxEffects` | Named client effects the server can start. |
| `client.vfx.WorldRifts`, `Rift`, `RiftSpec` | Rifts: tears in the air that open, hang and close. |
| `vfx.VfxControl`, `VfxParams` | Plays a named effect on players' clients from the server. |
| `vfx.RiftControl` | Opens and closes registered rifts from the server. |
| `client.vfx.GhostBlocks` | Blocks drawn as see-through ghosts of their real models: where they show (`Show`), how they're lit (`Light`), in groups, or with a shader of your own (`drawEffect`). |

### `client.viewport` ([Canvas GUI](../canvas/gui.md#scene-viewports))
| Class | Functionality |
|---|---|
| `SceneViewport` | A small 3D scene drawn into a texture: blocks, block entities, entities, items, glowing sprites and text, with a camera that circles them. |
| `client.gui.widget.SceneViewportWidget` | A scene viewport in a screen: drag to turn, scroll to zoom. |

### `client.sound` and `sound` ([Canvas Sound Filters](../canvas/sound-filters.md))
| Class | Functionality |
|---|---|
| `client.sound.SoundFilter` | How a sound comes through: gain, highs, distortion and reverb. |
| `client.sound.SoundFilters` | Named filters on every sound, fading in and out, and providers asked sound by sound. |
| `client.sound.SoundFilterProvider`, `SoundContext` | A filter per sound, by where it is, what it is or its category. |
| `sound.SoundFilterControl` | Sets and clears a player's named filters from the server. |

### `cutscene`, `client.cutscene` and the cutscene events ([Canvas Cutscenes](../canvas/cutscenes.md))
| Class | Functionality |
|---|---|
| `cutscene.Cutscene` | A cutscene as data: keyframes, letterbox, fades, focuses, subtitles, markers and settings; `builder()` and a JSON codec. |
| `cutscene.CameraKeyframe`, `Interpolation`, `CameraPose` | A pose the camera passes through and how it gets there, and a camera at a moment. |
| `cutscene.CameraFocus`, `CutsceneAnchor`, `ValueTrack` | Looking at a point, entity or the player; what positions are measured from; a number over time. |
| `cutscene.CutsceneControl` | Plays and stops cutscenes on a player's client from the server. |
| `cutscene.CutsceneEndReason` | Why a cutscene ended. |
| `client.cutscene.Cutscenes`, `CutscenePlayback` | Registering, playing and controlling cutscenes on the client, and the in-game editor. |
| `client.cutscene.Letterbox` | Letterbox bars without a cutscene. |
| `event.v2.client.CutsceneEvents`, `event.v2.common.ServerCutsceneEvents` | What a cutscene does as it plays, on the client and as each player's client reports it to the server. |

### `client.render` and `client.text` ([Canvas Text and Colors](../canvas/text-and-colors.md), [Canvas GUI](../canvas/gui.md#drawing-helpers))
| Class | Functionality |
|---|---|
| `text.TextEffects` | Gradient, rainbow and outlined text. |
| `render.Outline` | Solid or gradient outlines. |
| `render.RenderUtils` | Shapes, gradients, text and nine-slice blits. |
| `render.NineSliceInfo` | Nine-slice parameters. |

Before 9.0.0, these were in `client.utils`.

### `utils` ([Canvas Text and Colors](../canvas/text-and-colors.md))
| Class | Functionality |
|---|---|
| `ColorGradient` | Animated gradients and rainbows for text and outlines. |
| `Easing` | Easing curves. |

### `client.gui` ([Canvas GUI](../canvas/gui.md#items-at-an-opacity))
| Class | Functionality |
|---|---|
| `ItemOpacityRenderer` | Items drawn in a GUI at a fixed or fading opacity; `OpacityTween` eases one in or out. |
| `HudTakeover` | Hides the HUD while a condition holds and draws a view of your own in its place: a scope, a lens, a visor. |

### `client.gui.widget`, `panel`, `animation` and `placement` ([Canvas GUI](../canvas/gui.md), [Canvas Text and Colors](../canvas/text-and-colors.md))
| Class | Functionality |
|---|---|
| `widget.ColorableImageButton` | A tintable image button. |
| `widget.ElementOutlines` | Outlines attached to any widget. |
| `widget.MovableElement` | Anything that can be moved. |
| `widget.AbstractDraggableWidget` | A draggable window with its own children. |
| `widget.AbstractScrollableWidget` | A draggable window with scrolling. |
| `widget.AbstractMultiPanelWidget` | A window of stacked panels (experimental). |
| `panel.Panel`, `ScrollPanel`, `ViewportPanel` | Panel types: plain, scrolling, pan and zoom (experimental). |
| `animation.AnimatedComponent`, `animation.SlideAnimation` | Timeline and slide-in animations. |
| `placement.ScreenAnchor` | Screen anchoring. |
| `placement.GuiPlacement`, `GuiAreas`, `GuiArea` | Placing your own elements out of the way of what the HUD and screen drew. |

Before 9.0.0, these were in `client.gui.components` (with `widgets`, `widgets.panel` and `animations`) and `client.utils`.

### Shader includes ([Canvas Screen Effects](../canvas/screen-effects.md), [Canvas Skyboxes](../canvas/skyboxes.md), [Canvas World Effects](../canvas/world-effects.md#with-a-shader))
| Include | Functionality |
|---|---|
| `coolcatcanvas:screen_effect.glsl` | `Strength`, `Time`, `Age` and `Seed` for screen effect shaders, plus luminance, noise and edge helpers. |
| `coolcatcanvas:sky.glsl` | Layer inputs, the `SkyLayerInfo` block and `sky_finish` for sky layer shaders. |
| `coolcatcanvas:vfx.glsl` | The camera, the time and depth-fade helpers for `VfxMaterial.withShader` shaders. |
| `coolcatcanvas:ghost_effect.glsl` | `ghost_depth`, `ghost_world_position`, `ghost_color`, `EffectSampler` and `GhostParams0` to `GhostParams3` for `GhostBlocks.drawEffect` shaders. |
