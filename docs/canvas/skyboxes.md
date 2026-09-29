# Skyboxes

Custom skies drawn inside vanilla's sky pass, around the sun, moon and stars. Package: `net.ixdarklord.coolcatcanvas.api.client.sky` (client) and `api.sky` (server).

A skybox is a stack of **layers**. Any number of skyboxes can show at once, drawn by ascending priority. Nothing runs while no skybox is visible, and each visible layer is one draw call.

## No code: a skybox JSON

Put a file at `assets/<namespace>/skybox/<name>.json`. With `dimensions` set, it turns itself on in those dimensions, so a resource pack can add skies without any code:

```json
{
  "dimensions": ["minecraft:the_nether"],
  "hide": ["sky", "sunrise"],
  "fade_in": 40,
  "fade_out": 40,
  "layers": [
    { "type": "gradient", "params": ["#FF12040A", "#FF5A1208", "#FF12040A", [3]], "fog": 0 },
    {
      "type": "panorama",
      "texture": "mymod:textures/sky/stars_panorama.png",
      "blend": "additive",
      "tint": "#FFFF9070",
      "rotation": { "axis": [0, 1, 0], "speed": 0.5 },
      "fog": 0
    }
  ]
}
```

Top-level fields:

| Field | Default | Description |
|---|---|---|
| `layers` | `[]` | The layers, bottom to top. |
| `hide` | `[]` | Vanilla sky parts to hide: `sky`, `sunrise`, `sun`, `moon`, `stars`, `void`, `end_sky`, `end_flash`. They fade out as the skybox fades in. |
| `dimensions` | `[]` | Dimension ids the skybox shows in by itself. Dimensions without a sky, like the Nether, get one while it shows. |
| `priority` | `0` | Draw order among skyboxes. |
| `fade_in`, `fade_out` | `20` | Fade durations in ticks. |

Layer fields (only `type` and what that type needs are required):

| Field | Default | Description |
|---|---|---|
| `name` | `layer_<index>` | Name for controlling the layer from code. |
| `type` | (required) | `cubemap` (six faces in a 3×2 image: top row north, east, south; bottom row west, up, down), `panorama` (equirectangular 2:1, north in the middle), `sprite` (an image placed in the sky, such as a planet), `gradient`, or `shader`. |
| `texture` | | Full texture path, such as `mymod:textures/sky/x.png`. Needed by `cubemap`, `panorama`, `sprite` and animated layers. |
| `fragment_shader` | | `assets/<ns>/shaders/<path>.fsh`. Required for `shader`; any other type can swap in its own. |
| `blend` | `alpha` | `alpha`, `additive`, `multiply` or `screen`. |
| `tint` | `#FFFFFFFF` | `#AARRGGBB` or `#RRGGBB`; its alpha is the layer's opacity. |
| `stage` | `behind_celestials` | `behind_celestials` or `above_celestials`. |
| `orientation` | `[0, 0, 0]` | Degrees, applied as yaw (y), pitch (x), then roll (z). |
| `rotation` | | `{"axis": [x, y, z], "speed": degrees per clock unit, "clock": ...}`. Clocks: `real_time` (default), `game_time`, `day_time`, `sun_angle`, `moon_angle`, `star_angle`. |
| `animation` | | Flipbook: `{"frames": n, "frame_time": ticks, "interpolate": false, "clock": "game_time"}`. |
| `day_fade` | | `{"fade_in": [a, b], "fade_out": [c, d]}` in day ticks (0 to 24000; ranges may wrap). |
| `rain_fade` | `0` | How much rain hides the layer (0 to 1). |
| `fog` | `1` | How much horizon and underwater fog covers it (0 to 1). |
| `blur` | `true` | Linear texture filtering; `false` keeps pixels sharp. |
| `size` | `20` | Sprite width in degrees. |
| `params` | | Up to 4 values for the shader (`SkyParams[i]`), each 1 to 4 numbers or a color string. For `gradient`: zenith, horizon and nadir colors, then sharpness. |

Register a JSON skybox from code to control it: `Skyboxes.register(MyMod.id("night"), MyMod.id("night_sky"))`.

## In code

```java
Skybox bloodMoon = Skyboxes.register(MyMod.id("blood_moon"), SkyboxDefinition.builder()
        .hide(VanillaSky.MOON)
        .layer(SkyLayerDefinition.gradient(0xFFFF8080, 0xFFFF4040, 0xFF801010, 2.0F).blend(SkyBlend.MULTIPLY))
        .layer(SkyLayerDefinition.sprite(MyMod.id("textures/sky/planet.png"), 25.0F)
                .name("moon").tint(0xFFFF6060).animation(4, 40, true)
                .rotation(SkyLayerDefinition.Rotation.withMoon())
                .stage(SkyLayerStage.ABOVE_CELESTIALS).fog(0.0F))
        .build()).fade(60);

bloodMoon.enableFor(1200);
bloodMoon.layer("moon").animateTint(0xFFFFFFFF, 40, Easing.SINE_IN_OUT);
```

A custom-shader layer with a parameter bound to the weather:

```java
Skybox aurora = Skyboxes.register(MyMod.id("aurora"), SkyboxDefinition.builder()
        .layer(SkyLayerDefinition.shader(MyMod.id("sky/aurora")).name("aurora")
                .blend(SkyBlend.ADDITIVE).stage(SkyLayerStage.ABOVE_CELESTIALS)
                .paramColor(0, 0xFF20FF80).paramColor(1, 0xFF6040FF).fog(0.4F))
        .build());
aurora.activeWhen(context -> context.inWorld() && context.isNight());
aurora.layer("aurora").bindParam(0, (context, out) -> {
    out[0] = Mth.lerp(context.thunder(), 0.12F, 1.0F);
    out[1] = Mth.lerp(context.thunder(), 1.0F, 0.25F);
    out[2] = Mth.lerp(context.thunder(), 0.5F, 0.6F);
});
```

Its shader, `assets/mymod/shaders/sky/aurora.fsh`, imports `coolcatcanvas:sky.glsl` and ends with `sky_finish`:

```glsl
#version 330
#moj_import <coolcatcanvas:sky.glsl>

in vec3 skyLocal;
in vec3 skyWorld;
out vec4 fragColor;

void main() {
    vec3 d = normalize(skyLocal);
    vec3 color = mix(SkyParams[0].rgb, SkyParams[1].rgb, clamp(d.y * 1.5, 0.0, 1.0));
    fragColor = sky_finish(vec4(color, 1.0), skyWorld);
}
```

`coolcatcanvas:sky.glsl` gives each layer's shader:
- `skyLocal` (the direction in the layer's orientation; for sprites, -1 to 1 across x and z) and `skyWorld` (y up); textured layers also get `Sampler0`.
- The `SkyLayerInfo` block:
  - `SkyColor`: the tint, with every fade applied to its alpha;
  - `SkyFrame`: frame, next frame, blend and frame count;
  - `SkyTime`: real seconds, game seconds, day fraction and sun angle;
  - `SkyEnv`: rain, thunder, fog and skybox visibility;
  - `SkyParams[4]`;
  - `SkyViewMat` and `SkyModelMat`.
- Helpers:
  - `sky_texture` (flipbook-aware sampling);
  - `sky_frame_uv`;
  - `sky_panorama_uv`;
  - `sky_fog_value`;
  - `sky_finish(color, world)`, which applies the tint, opacity, fog and blend mode;
  - the constant `SKY_PI`.

## From the server

`SkyboxControl` drives registered skyboxes on clients (clients without the skybox ignore it; no state is kept):

```java
SkyboxControl.toggle(player, MyMod.id("blood_moon"));
SkyboxControl.to(players).enableFor(MyMod.id("blood_moon"), 1200);
SkyboxControl.inLevel(serverLevel).setEnabled(MyMod.id("aurora"), true);
SkyboxControl.everyone(server).setLayerAlpha(MyMod.id("aurora"), "aurora", 0.5F, 20);
```

## Reference

| Type | Description |
|---|---|
| `Skyboxes` | `register(id, SkyboxDefinition)`, `register(id, Identifier json)`, `get(id)` (resource-pack skyboxes too), `all()`, `unregister(id)`, `disableAllInstantly()`. |
| `Skybox` | `priority(int)`, `fade(ticks)` / `fade(in, out, Easing)`, `activeWhen(Predicate<SkyContext>)` (replaces `dimensions`), `onFrame(...)`, `hide(VanillaSky...)`, `enable()`, `disable()`, `toggle()`, `setEnabled(boolean)`, `enableFor(ticks)`, `enableInstantly()`, `disableInstantly()`, `isEnabled()`, `isVisible()`, `visibility()`, `layer(name)`, `layers()`, `isLoaded()`, `error()`. Settings made here override the definition and survive resource reloads. |
| `SkyLayer` | Runtime control of one layer: `setVisible`, `setAlpha`, `animateAlpha`, `fadeIn`, `fadeOut`, `setTint`, `animateTint`, `resetTint`, `setParam`, `setParamColor`, `animateParam`, `bindParam(index, (context, out) -> ...)`, `resetParams`, `param(index)`. |
| `SkyboxDefinition` | `builder()` with `layer(...)`, `hide(...)`, `hideAll()`, `dimensions(...)`, `priority(int)`, `fade(in, out)`. |
| `SkyLayerDefinition` | Layer factories `cubemap(texture)`, `panorama(texture)`, `sprite(texture, size)`, `gradient(zenith, horizon, nadir, sharpness)`, `shader(fragmentShader)`; builder methods matching the JSON fields. `Rotation.withSun()`, `withMoon()`, `withStars()`; `DayFade.DAY`, `DayFade.NIGHT`. |
| `SkyLayerType`, `SkyBlend`, `SkyLayerStage`, `SkyClock`, `VanillaSky` | The enums behind the JSON values. |
| `SkyContext` | The frame: `skybox`, `player`, `level`, `partialTick`, `time`, `deltaTime`, `age`, `visibility`, `dayTime`, `rain`, `thunder`, `inWorld()`, `inDimension(key)`, `isNight()`. |
| `api.sky.SkyboxControl` | Server side. Per player: `enable`, `disable`, `toggle`, `setEnabled`, `enableFor`, `enableInstantly`, `disableInstantly`, `setLayerVisible`, `setLayerAlpha`, `setLayerParam`, `resetLayer`. The same methods on a `Target` from `to(players)`, `inLevel(level)` or `everyone(server)`. |
