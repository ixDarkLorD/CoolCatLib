---
icon: material/blur
description: Post-processing shaders and screen tints
---

# Screen Effects

Full-screen post-processing effects (color grading, distortion, blur, anything a fragment shader can do to the finished frame) whose uniforms can change every frame. Package: `net.ixdarklord.coolcatcanvas.api.client.effect` (client) and `api.effect` (server).

An effect is a `ScreenEffect`: it fades in and out, can be pulsed, driven by a condition or by the server, and stacks with any number of others.

| You want | Use |
|---|---|
| A color wash over the screen (damage flash, frost, night vision...) | The built-in [tint](#tint-the-screen): one line, no assets. |
| A vanilla post effect (`minecraft:invert`, `minecraft:creeper`...) | [`ScreenEffects.register`](#register-and-control-it) with its id. |
| Your own shader | [Write the effect](#write-your-own-effect), then register it. |

## Tint the screen

*Since Canvas 26.1.2-4.* The tint needs no JSON, no shader and no registration. Call it where you need it:

```java
// A red flash when the player is hurt: fades in, stays 5 ticks, fades out.
ScreenEffects.tint(MyMod.id("hurt"), 0x59FF0000).enableFor(5);

// A blue wash that stays until it's disabled.
ScreenEffects.tint(MyMod.id("frost"), 0x593366FF).enable();
ScreenEffects.get(MyMod.id("frost")).ifPresent(ScreenEffect::disable);
```

<div class="ccl-shots">
  <figure><img src="../../assets/screen-effects/none.webp" alt="The game with no effect" loading="lazy"><figcaption>No effect</figcaption></figure>
  <figure><img src="../../assets/screen-effects/tint-hurt.webp" alt="A red tint over the world" loading="lazy"><figcaption><code>0x59FF0000</code>: the hurt flash</figcaption></figure>
  <figure><img src="../../assets/screen-effects/tint-frost.webp" alt="A blue tint over the world" loading="lazy"><figcaption><code>0x593366FF</code>: the frost wash</figcaption></figure>
</div>

The color is ARGB, and its alpha is how strong the tint is: `0x59` is about 35%, `0xFF` replaces the frame with the color.

The first call for an id makes the tint; later calls recolor it and return the same effect. Each id is its own tint, so two mods (or two tints of one mod) never share a color or state. What comes back is a normal `ScreenEffect`, so everything on this page applies to it:

```java
// Over the HUD and menus too, with a slow fade.
ScreenEffects.tint(MyMod.id("blackout"), 0xE6000000)
        .scope(ScreenEffectScope.SCREEN)
        .fade(40)
        .enable();

// Set up once (in your client setup), then it runs by itself: redder the lower the player's health.
ScreenEffects.tint(MyMod.id("low_health"), 0x66FF0000)
        .activeWhen(context -> context.inWorld() && context.player().getHealth() < 8.0F)
        .strength(context -> 1.0F - context.player().getHealth() / 8.0F);

// Change the color smoothly, over 20 ticks, instead of at once (RGBA, 0 to 1).
ScreenEffects.get(MyMod.id("frost")).ifPresent(frost ->
        frost.uniform(ScreenEffects.TINT_COLOR).animateTo(20, 1.0F, 1.0F, 1.0F, 0.5F));
```

<div class="ccl-shots ccl-shots--wide">
  <figure><img src="../../assets/screen-effects/tint-blackout.webp" alt="A near-black tint covering the HUD too" loading="lazy"><figcaption>The blackout: drawn over the HUD too</figcaption></figure>
  <figure><img src="../../assets/screen-effects/tint-low-health.webp" alt="A red tint growing as health drops" loading="lazy"><figcaption>Low health: the tint grows as the hearts drain</figcaption></figure>
</div>

From the server, with nothing registered on the client:

```java
ScreenEffectControl.tint(serverPlayer, MyMod.id("frost"), 0x593366FF);        // until disabled
ScreenEffectControl.tint(serverPlayer, MyMod.id("hurt"), 0x59FF0000, 10);     // a 10-tick flash
ScreenEffectControl.disable(serverPlayer, MyMod.id("frost"));
```

!!! note
    Tints are driven by code, so they start hidden from the [effects screen](#the-effects-screen); `.selectable(true)` lists one there. The tint's definition is `coolcatcanvas:tint` (`ScreenEffects.TINT`), with one `vec4` uniform, `Color` (`ScreenEffects.TINT_COLOR`).

## Write your own effect

Put a vanilla-format post effect at `assets/<modid>/post_effect/<name>.json` and its shader at `assets/<modid>/shaders/post/<name>.fsh`:

```json
{
  "targets": { "swap": {} },
  "passes": [
    {
      "vertex_shader": "minecraft:core/screenquad",
      "fragment_shader": "mymod:post/vignette",
      "inputs": [ { "sampler_name": "In", "target": "minecraft:main", "bilinear": false } ],
      "output": "swap",
      "uniforms": {
        "VignetteConfig": [
          { "name": "Color", "type": "vec4", "value": [0.0, 0.0, 0.0, 1.0] },
          { "name": "Radius", "type": "float", "value": 0.75 },
          { "name": "Softness", "type": "float", "value": 0.45 }
        ]
      }
    },
    {
      "vertex_shader": "minecraft:core/screenquad",
      "fragment_shader": "minecraft:core/blit_screen",
      "inputs": [ { "sampler_name": "In", "target": "swap" } ],
      "output": "minecraft:main"
    }
  ]
}
```

The first pass reads the frame (`minecraft:main`) and draws into `swap`; the second copies `swap` back. A pass can't read the target it draws into, which is why one-shader effects need both.

Differences from vanilla's format:

- Every uniform needs a `"name"`.
- Every pass also gets the `EffectInfo` and `Globals` uniform blocks.
- The only external target is `minecraft:main`; read its depth with `"use_depth_buffer": true`.
- A target declared as `{"persistent": true}` keeps its contents across frames, for feedback effects such as trails.

The shader imports Canvas's include to get the effect's strength and time:

```glsl
#version 330
#moj_import <coolcatcanvas:screen_effect.glsl>

uniform sampler2D InSampler;
layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; };
layout(std140) uniform VignetteConfig { vec4 Color; float Radius; float Softness; };

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;
    float edge = smoothstep(Radius - Softness, Radius, ce_edge(texCoord, OutSize));
    fragColor = vec4(mix(color, Color.rgb, clamp(edge * Color.a * Strength, 0.0, 1.0)), 1.0);
}
```

<div class="ccl-shots ccl-shots--wide">
  <figure><img src="../../assets/screen-effects/vignette.webp" alt="Dark corners closing in on the view" loading="lazy"><figcaption>The vignette above, at its default radius and softness</figcaption></figure>
</div>

`coolcatcanvas:screen_effect.glsl` provides:

| Name | What it is |
|---|---|
| `float Strength` | 0 to 1: the effect's fade × manual strength × strength function. Scale your work by it so the effect fades (or turn on `autoBlend`). |
| `float Time` | Real seconds since the game started (wraps every hour). |
| `float Age` | Seconds since the effect last became visible. |
| `float Seed` | A fresh random number in [0, 1) every frame. |
| `ce_luma(vec3)` | Rec. 709 luminance. |
| `ce_hash(vec2)`, `ce_noise(vec2)` | Hash and value noise. |
| `ce_edge(vec2 uv, vec2 size)` | 0 at the center, 1 at the corners, aspect-corrected. |

## Register and control it

Register once from client code (a `ClientModConstructor` is a good place). Effects start disabled:

```java
// assets/mymod/post_effect/vignette.json
ScreenEffect vignette = ScreenEffects.register(MyMod.id("vignette"), MyMod.id("vignette"));
```

Then turn it on and off as the game goes:

```java
vignette.enable();                  // fades in (10 ticks by default)
vignette.disable();                 // fades out
vignette.enableFor(40);             // fades in, stays 2 seconds, fades out
vignette.enableInstantly();         // no fade
vignette.toggle();
```

Its uniforms can be set, animated, or computed every frame:

```java
vignette.setUniform("Radius", 0.6F);
vignette.uniform("Color").setColor(0xFF200000);
vignette.uniform("Softness").animateTo(20, 0.8F);                                       // over 20 ticks
vignette.bindUniform("Radius", context -> 0.6F + 0.1F * Mth.sin(context.time()));      // every frame
```

### Effects that run themselves

`activeWhen` turns an effect on while a condition holds, and `strength` scales it by a function of the frame, so nothing else has to touch it:

```java
ScreenEffects.register(MyMod.id("insanity"), MyMod.id("desaturate"))
        .priority(10)
        .fade(40)
        .activeWhen(context -> context.inWorld() && Sanity.of(context.player()) > 0.4F)
        .strength(context -> Mth.inverseLerp(Sanity.of(context.player()), 0.4F, 0.8F))
        .displayName(Component.literal("Insanity"))
        .setUniform("Contrast", 1.4F);
```

<div class="ccl-shots ccl-shots--wide">
  <figure><img src="../../assets/screen-effects/desaturate.webp" alt="The world drained of color with raised contrast" loading="lazy"><figcaption>A desaturation shader at full strength, with <code>Contrast</code> raised</figcaption></figure>
</div>

The strength can be moved by hand too, on top of the fade:

```java
vignette.setStrength(0.5F);
vignette.animateStrength(1.0F, 20, Easing.SINE_IN_OUT);
```

### Vanilla effects

Vanilla's post effects register by their id. They ignore `Strength`, so they'd pop in and out; `autoBlend` fades them (and any other shader that doesn't scale by `Strength`) at the cost of three extra full-screen copies:

```java
ScreenEffects.register(MyMod.id("invert"), Identifier.withDefaultNamespace("invert")).autoBlend(true).enable();
```

<div class="ccl-shots ccl-shots--wide">
  <figure><img src="../../assets/screen-effects/invert.webp" alt="The game with its colors inverted" loading="lazy"><figcaption>Vanilla's <code>minecraft:invert</code></figcaption></figure>
</div>

### Definitions in code

The JSON can be skipped by building the definition in code; the shaders still come from resources. `simple` is the two-pass shape shown above:

```java
ScreenEffects.register(MyMod.id("vignette"),
        ScreenEffectDefinition.simple(MyMod.id("post/vignette"), "VignetteConfig",
                ScreenEffectDefinition.UniformSpec.ofVec4("Color", 0F, 0F, 0F, 1F),
                ScreenEffectDefinition.UniformSpec.ofFloat("Radius", 0.75F),
                ScreenEffectDefinition.UniformSpec.ofFloat("Softness", 0.45F)))
        .scope(ScreenEffectScope.SCREEN);
```

### Scopes and layers

An effect draws in one of two scopes, set with `scope(...)`:

| Scope | Draws over | Runs |
|---|---|---|
| `WORLD` (default) | The world and the held item, under the HUD and screens. The world's depth is still readable. | Only while a world is rendered. |
| `SCREEN` | Everything, HUD and menus included. | Always, with no world loaded too. |

!!! info "Renamed in Canvas 26.1.2-5"
    Before it, the scope was `ScreenEffectStage`, set with `stage(...)`.

Within a scope, effects draw in layer order, each one processing the output of those before it. The order starts from `priority` (lower first) and can be rearranged through `ScreenEffects.layers()`, by code or by players on the effects screen.

### From the server

`ScreenEffectControl` drives a player's effects by id. Clients without that effect ignore the command, and the server keeps no state, so resend what should persist when a player joins:

```java
ScreenEffectControl.enableFor(serverPlayer, MyMod.id("heartbeat"), 60);
ScreenEffectControl.setUniform(serverPlayer, MyMod.id("heartbeat"), "Rate", 20, 2.0F);
ScreenEffectControl.tint(serverPlayer, MyMod.id("hurt"), 0x59FF0000, 10);
```

### The effects screen

`ScreenEffects.openScreen()` (also a key, unbound by default) lists every selectable effect with a switch, and the active ones as drag-and-drop layers with a strength slider each, previewed live. Players' choices (on/off, strengths, order) are saved to `config/coolcatcanvas-screen-effects.json`. Give effects a `displayName` and `description` for it, or keep one out with `selectable(false)`.

<div class="ccl-shots ccl-shots--wide">
  <figure><img src="../../assets/screen-effects/effects-screen.webp" alt="The effects screen: a list of effects and the active layers" loading="lazy"><figcaption>The effects screen, previewing the three effects that are on</figcaption></figure>
</div>

### When an effect fails

A missing file or a shader that doesn't compile is logged, and that effect is skipped while the others keep running. `effect.isLoaded()` and `effect.error()` tell what happened; fixing the resources and reloading them (F3+T) loads it again.

## Reference

### `ScreenEffects` (static entry point)
| Method | Description |
|---|---|
| `tint(Identifier id, int argb)` | The built-in tint under `id`: made on the first call, recolored and returned on later ones. Throws if the id belongs to an effect that isn't a tint. |
| `TINT`, `TINT_COLOR` | The tint's definition (`coolcatcanvas:tint`) and its color uniform (`Color`). |
| `register(Identifier id, Identifier definition)` | Registers an effect from `assets/<ns>/post_effect/<path>.json` (vanilla ones included); reloaded with resources. Throws if the id is taken. |
| `register(Identifier id, ScreenEffectDefinition definition)` | Registers a definition built in code. |
| `get(id)`, `all()`, `unregister(id)` | Look up, list, or remove an effect (freeing its GPU resources). |
| `disableAllInstantly()` | Hides every effect at once. |
| `layers()` | The draw order (`ScreenEffectLayers`). |
| `createScreen(parent)`, `openScreen()` | The effects screen: every selectable effect with a switch and a strength slider, reorderable layers, live preview. Also on a key (unbound by default) and `/coolcatcanvas_client effects` in development. |

### `ScreenEffect` (the handle; render thread only)
The drawn strength is fade × manual strength × strength function, and a strength of 0 skips the effect. Durations are ticks of real time.

| Group | Methods |
|---|---|
| Presentation | `displayName(Component)`, `description(Component)`, `selectable(boolean)` |
| Configuration | `scope(ScreenEffectScope)` (default `WORLD`), `priority(int)` (lower draws first), `fade(int ticks)` / `fade(in, out, Easing)`, `autoBlend(boolean)`, `activeWhen(Predicate<EffectContext>)`, `strength(StrengthFunction)`, `onFrame(Consumer<EffectContext>)` |
| State | `enable()`, `disable()`, `toggle()`, `setEnabled(boolean)`, `enableFor(ticks)`, `enableInstantly()`, `disableInstantly()`, `isEnabled()`, `isVisible()`, `setStrength(float)`, `animateStrength(target, ticks, Easing)`, `strength()` |
| Uniforms | `uniform(name)`, `setUniform(name, float...)`, `setUniformInt(name, int...)`, `bindUniform(name, FloatBinding / VectorBinding)`, `resetUniforms()` |
| Loading | `isLoaded()`, `error()` |

### Other types
| Type | Description |
|---|---|
| `ScreenEffectDefinition` | The post-effect definition (targets and passes). `builder()` with `target`, `persistentTarget`, `pass(fragmentShader, pass -> ...)`, `blit`; `simple(fragmentShader, uniformBlock, UniformSpec...)` for one-pass effects; `UniformSpec.ofFloat/ofInt/ofVec2/ofVec3/ofVec4`. `PassBuilder`: `vertexShader`, `input`, `depthInput`, `textureInput` (reads `textures/effect/<path>.png`), `output`, `uniforms`. |
| `EffectContext` | The frame an effect is drawn in: `effect`, `player`, `level`, `partialTick`, `time`, `deltaTime`, `age`, `strength`, `width`, `height`, `minecraft()`, `inWorld()`. |
| `EffectUniform` | One named uniform: `set(...)` (floats, ints, JOML vectors and matrices), `setColor(argb)`, `animateTo(Easing, ticks, target...)`, `bind(...)`, `unbind()`, `reset()`, `get()`, `getAll()`. A binding beats an animation, which beats the last set value, which beats the definition's default. |
| `ScreenEffectScope` | `WORLD` (over the world and held item, under the HUD; depth available) or `SCREEN` (over everything, menus included). |
| `ScreenEffectLayers` | The draw order: `order()`, `visible(scope)`, `moveTo`, `moveUp`, `moveDown`, `bringToTop`, `sendToBottom`, `isCustomized()`, `resetOrder()`. Every `WORLD` effect draws before any `SCREEN` effect. |
| `api.effect.ScreenEffectControl` | Server side: `enable`, `disable`, `enableFor`, `enableInstantly`, `disableInstantly`, `setStrength`, `setUniform`, `resetUniforms` and `tint(player, id, argb[, ticks])` for a `ServerPlayer`. It keeps no state, so resend after a player joins. |
| `api.event.v2.client.ScreenEffectEvents` | `BEFORE_TOGGLE` (return `EventResult.INTERRUPT` to veto), `TOGGLED`, `STRENGTH_CHANGED`, `UNIFORM_CHANGED`, `LAYERS_CHANGED`, `LOADED`. The toggle cause is `CODE`, `CONDITION`, `TIMEOUT`, `PLAYER` or `SERVER`. |
