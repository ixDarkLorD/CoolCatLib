# Changelog
This file is for listing all the changes to this project

## v2100.1.0.0 Release | Sep 30, 2026 (Minecraft 1.21.1)
Backport of the 26.1.2 release: the same features, on Forge, NeoForge and Fabric, with the rendering rebuilt on 1.21.1's.
### ✨ New Features
- First release: the render and visuals half of what used to be CoolCatLib, split out and built on CoolCatLib: Core. Everything below was moved from CoolCatLib into the `net.ixdarklord.coolcatcanvas` package and `coolcatcanvas` namespace.
- Screen effects (`api.client.effect`): full-screen post-processing shaders whose uniforms can change every frame (vanilla's post chains bake them in at load).
  - `ScreenEffects.register(id, definition)` from a vanilla-format `post_effect` JSON (vanilla's own included) or a `ScreenEffectDefinition` built in code; any number stack, ordered by `priority`, drawn over the world (`WORLD`) or over everything including the HUD and menus (`SCREEN`).
  - Smooth fades (`fade`, `Easing`), timed pulses (`enableFor`), animated strength, conditions (`activeWhen`), strength functions of the frame (`strength`) and per-frame callbacks (`onFrame`).
  - Uniforms by name: set, animated (`animateTo`) or bound to a function of the frame; `autoBlend` fades shaders that ignore the effect's strength.
  - Shaders get `Strength`, `Time`, `Age` and `Seed` plus noise and luminance helpers from `#moj_import <coolcatcanvas:screen_effect.glsl>`; persistent targets allow feedback effects; reloaded with resource packs; a failing effect is logged and skipped.
  - No effects of its own: mods bring their own post effect JSON and shaders, or use vanilla's (`minecraft:invert`, ...).
  - `api.effect.ScreenEffectControl` drives a player's effects from the server.
  - Layers (`ScreenEffects.layers()`): the draw order of every effect, from priorities until rearranged (move up/down, to a layer, to the top or bottom, reset).
  - An effects screen (`ScreenEffects.openScreen()`, a key unbound by default, and `/coolcatcanvas_client effects` in development environments only): every selectable effect with a switch, the active ones as drag-and-drop layers grouped by stage with a strength slider each, a live unblurred preview and a Preview mode hiding the panels. Players' choices (on/off, strengths, order) are saved to `config/coolcatcanvas-screen-effects.json`. Effects have a `displayName`, `description` and `selectable` flag for it.
  - Events (`ScreenEffectEvents`): `BEFORE_TOGGLE` (cancellable) and `TOGGLED` with the cause (code, condition, timeout, player, server), `STRENGTH_CHANGED`, `UNIFORM_CHANGED`, `LAYERS_CHANGED` and `LOADED`.
- Custom skyboxes (`api.client.sky`): layers drawn inside vanilla's sky pass, around the sun, moon and stars.
  - `Skyboxes.register(id, definition)` from a `SkyboxDefinition` built in code or `assets/<namespace>/skybox/<path>.json`; resource packs add skyboxes with no code by listing `dimensions` (dimensions without a sky, like the Nether, get one while it shows).
  - Layer types: `cubemap` (six faces in a 3x2 image), `panorama` (equirectangular), `sprite` (an image placed in the sky, e.g. a planet), `gradient`, and `shader` (your own fragment shader, with `#moj_import <coolcatcanvas:sky.glsl>`); any type can swap in its own fragment shader.
  - Per layer: blend mode (alpha, additive, multiply, screen), tint, orientation, rotation over real time, game time, the day or vanilla's sun/moon/star angle, flipbook animation with optional interpolation, day-time fade windows, rain fade, horizon/underwater fog, and 4 shader params.
  - Hides vanilla's sky color, sunrise, sun, moon, stars, void, End sky and End flashes, fading them out as the skybox fades in.
  - Runtime control: fades, `enableFor`, conditions (`activeWhen`), per-frame callbacks, and per layer (`Skybox.layer(name)`) visibility, animated opacity and tint, and params set, animated or bound to a function of the frame. `api.sky.SkyboxControl` drives them from the server: enable, disable, `toggle`, `setEnabled`, timed pulses and layer changes, for one player or several (`to(players)`, `inLevel(level)`, `everyone(server)`).
  - Cheap: nothing runs while no skybox shows; a visible layer is one draw from a shared static mesh, all layers of a stage share one render pass and one uniform upload.
- `api.utils.Easing`: easing curves.
- GUI components and widgets (`api.client.gui.components`: panels, scroll and drag widgets, slide animations, `ColorableImageButton`), and `api.client.utils` `RenderUtils`, `NineSliceInfo` and `ScreenAnchor`.
  - `ViewportPanel` (pan and zoom) edge fades show where content continues past an edge, and shrink smoothly into their side as the end of the content on that side comes into view.
- Animated color gradients (`api.utils.ColorGradient`): color stops looping around (`ColorGradient.of(colors...)`) or the full rainbow (`RAINBOW`, `rainbow(saturation, brightness)`), scrolling over time (`withSpeed`) and along the text or outline (`withSpread`), with each letter colored (`perLetter(n)`) or the whole text in one cycling color (`wholeText()`, `RAINBOW_WHOLE`). Presets: `RAINBOW`, `RAINBOW_WHOLE`, `PASTEL_RAINBOW`, `FIRE`, `OCEAN`, `AURORA`.
- Gradient components: `gradient.literal("...")`, `gradient.apply(component)` or a style with `gradient.textColor()` make a component animate wherever it's drawn (chat, tooltips, item names, signs, widget labels). In JSON text and commands (`/tellraw`) the color is `"coolcatcanvas:rainbow"`, `"coolcatcanvas:rainbow_whole"`, `"coolcatcanvas:rainbow/<speed>/<spread>/<saturation>/<brightness>"` or `"coolcatcanvas:gradient/<speed>/<spread>/#RRGGBB/#RRGGBB..."`; players without Canvas can't read it.
- Gradient and outlined text (`api.client.utils.TextEffects`): `gradient(text, gradient)` and `rainbow(text)` recolor existing text and stay animated when kept; outlined text in a solid color or a gradient.
- Outlines (`api.client.utils.Outline`): solid or a gradient (`Outline.gradient`, `Outline.rainbow()`), with a thickness and padding, drawn around any rectangle; `ElementOutlines.set(widget, outline[, when])` attaches one to any widget (vanilla's too), optionally only while a condition holds (hovered, focused...).

### 🔧 Differences from the 26.1.2 release
- Screen effect and sky shaders are GLSL 150 with plain `uniform`s instead of uniform blocks; `post_effect` definitions and their JSON stay the same, and a vanilla `shaders/post` chain (e.g. `minecraft:creeper`) can be registered as an effect too.
- End flashes don't exist on 1.21.1, so `VanillaSky.END_FLASH` does nothing.
- GUI panel input methods take 1.21.1's (mouse x, y, button...) parameters, and their `extract*` hooks are `render*`.
