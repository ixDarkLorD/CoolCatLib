# Screen Effects

Full-screen post-processing effects (color grading, distortion, blur, anything a fragment shader can do to the finished frame) whose uniforms can change every frame. Package: `net.ixdarklord.coolcatcanvas.api.client.effect` (client) and `api.effect` (server).

!!! info
    Canvas ships **no effects of its own**. Your mod brings its `post_effect` JSON and shaders, or registers a vanilla one such as `minecraft:invert`.

## 1. Write the effect

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

`coolcatcanvas:screen_effect.glsl` provides:

| Name | What it is |
|---|---|
| `float Strength` | 0 to 1: the effect's fade × manual strength × strength function. Scale your work by it so the effect fades. |
| `float Time` | Real seconds since the game started (wraps every hour). |
| `float Age` | Seconds since the effect last became visible. |
| `float Seed` | A fresh random number in [0, 1) every frame. |
| `ce_luma(vec3)` | Rec. 709 luminance. |
| `ce_hash(vec2)`, `ce_noise(vec2)` | Hash and value noise. |
| `ce_edge(vec2 uv, vec2 size)` | 0 at the center, 1 at the corners, aspect-corrected. |

## 2. Register and control it

Register once from client code (a `ClientModConstructor` is a good place):

```java
ScreenEffect insanity = ScreenEffects.register(MyMod.id("insanity"), MyMod.id("desaturate"))
        .priority(10)
        .fade(40)
        .activeWhen(context -> context.inWorld() && Sanity.of(context.player()) > 0.4F)
        .strength(context -> Mth.inverseLerp(Sanity.of(context.player()), 0.4F, 0.8F))
        .displayName(Component.literal("Insanity"))
        .setUniform("Contrast", 1.4F);

ScreenEffect tint = ScreenEffects.register(MyMod.id("tint"), MyMod.id("tint")).fade(15);
tint.uniform("Color").setColor(0x5933CC66);
tint.enableFor(40);                                   // a 2-second pulse
tint.animateStrength(0.5F, 20, Easing.SINE_IN_OUT);

// A vanilla effect that ignores Strength: autoBlend fades it anyway.
ScreenEffects.register(MyMod.id("invert"), Identifier.withDefaultNamespace("invert")).autoBlend(true).enable();
```

Or build the definition in code (its shaders still come from resources):

```java
ScreenEffects.register(MyMod.id("vignette"),
        ScreenEffectDefinition.simple(MyMod.id("post/vignette"), "VignetteConfig",
                ScreenEffectDefinition.UniformSpec.ofVec4("Color", 0F, 0F, 0F, 1F),
                ScreenEffectDefinition.UniformSpec.ofFloat("Radius", 0.75F),
                ScreenEffectDefinition.UniformSpec.ofFloat("Softness", 0.45F)))
        .stage(ScreenEffectStage.SCREEN)
        .bindUniform("Radius", context -> 0.6F + 0.1F * Mth.sin(context.time()));
```

From the server, drive a player's registered effects with `ScreenEffectControl` (clients without the effect ignore it):

```java
ScreenEffectControl.enableFor(serverPlayer, MyMod.id("heartbeat"), 60);
ScreenEffectControl.setUniform(serverPlayer, MyMod.id("tint"), "Color", 20, 1F, 0F, 0F, 0.35F);
```

## Reference

### `ScreenEffects` (static entry point)
| Method | Description |
|---|---|
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
| Configuration | `stage(ScreenEffectStage)` (default `WORLD`), `priority(int)` (lower draws first), `fade(int ticks)` / `fade(in, out, Easing)`, `autoBlend(boolean)`, `activeWhen(Predicate<EffectContext>)`, `strength(StrengthFunction)`, `onFrame(Consumer<EffectContext>)` |
| State | `enable()`, `disable()`, `toggle()`, `setEnabled(boolean)`, `enableFor(ticks)`, `enableInstantly()`, `disableInstantly()`, `isEnabled()`, `isVisible()`, `setStrength(float)`, `animateStrength(target, ticks, Easing)`, `strength()` |
| Uniforms | `uniform(name)`, `setUniform(name, float...)`, `setUniformInt(name, int...)`, `bindUniform(name, FloatBinding / VectorBinding)`, `resetUniforms()` |
| Loading | `isLoaded()`, `error()` |

### Other types
| Type | Description |
|---|---|
| `ScreenEffectDefinition` | The post-effect definition (targets and passes). `builder()` with `target`, `persistentTarget`, `pass(fragmentShader, pass -> ...)`, `blit`; `simple(fragmentShader, uniformBlock, UniformSpec...)` for one-pass effects; `UniformSpec.ofFloat/ofInt/ofVec2/ofVec3/ofVec4`. `PassBuilder`: `vertexShader`, `input`, `depthInput`, `textureInput` (reads `textures/effect/<path>.png`), `output`, `uniforms`. |
| `EffectContext` | The frame an effect is drawn in: `effect`, `player`, `level`, `partialTick`, `time`, `deltaTime`, `age`, `strength`, `width`, `height`, `minecraft()`, `inWorld()`. |
| `EffectUniform` | One named uniform: `set(...)` (floats, ints, JOML vectors and matrices), `setColor(argb)`, `animateTo(Easing, ticks, target...)`, `bind(...)`, `unbind()`, `reset()`, `get()`, `getAll()`. A binding beats an animation, which beats the last set value, which beats the definition's default. |
| `ScreenEffectStage` | `WORLD` (over the world and held item, under the HUD; depth available) or `SCREEN` (over everything, menus included). |
| `ScreenEffectLayers` | The draw order: `order()`, `visible(stage)`, `moveTo`, `moveUp`, `moveDown`, `bringToTop`, `sendToBottom`, `isCustomized()`, `resetOrder()`. Every `WORLD` effect draws before any `SCREEN` effect. |
| `api.effect.ScreenEffectControl` | Server side: `enable`, `disable`, `enableFor`, `enableInstantly`, `disableInstantly`, `setStrength`, `setUniform`, `resetUniforms` for a `ServerPlayer`. It keeps no state, so resend after a player joins. |
| `api.event.v2.client.ScreenEffectEvents` | `BEFORE_TOGGLE` (return `EventResult.INTERRUPT` to veto), `TOGGLED`, `STRENGTH_CHANGED`, `UNIFORM_CHANGED`, `LAYERS_CHANGED`, `LOADED`. The toggle cause is `CODE`, `CONDITION`, `TIMEOUT`, `PLAYER` or `SERVER`. |

Players' choices on the effects screen (on/off, strengths, order) are saved to `config/coolcatcanvas-screen-effects.json`.
