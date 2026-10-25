---
icon: material/shimmer
description: Glowing particles, beams, trails and rifts in the world
---

# World Effects

Glowing shapes drawn in the world: particles that swell and fade, beams, flashes, trails and shapes of your own, and rifts that tear open in the air. Package: `net.ixdarklord.coolcatcanvas.api.client.vfx` (client) and `api.vfx` (server).

!!! info "Since"
    CoolCatLib **26.1.2-5** (Canvas 26.1.2-6 before the merge), on Minecraft 26.1 – 26.1.2, and in 26.2 and 26.3; **2100.4.0.0** (1.21.1) and **2001.4.0.0** (1.20.1). On 1.20.1, shader materials use GLSL 150.

World effects are drawn after the whole level and before the player's hand, lit by nothing. They hide behind solid things and fade softly where they meet them, so a glow never ends in a hard line at a wall or the ground. Everything drawn with the same material in a frame goes out in one draw call.

| You want | Use |
|---|---|
| Sparks, embers, smoke that live on their own | [Effect particles](#effect-particles): a `ParticleSpec`, spawned with `WorldParticles`. |
| A beam or a flash, once | [One-liners](#one-liners): `WorldVfx.beam`, `flash`, `groundFlash`. |
| Shapes that follow something every frame | [A renderer](#draw-every-frame): `WorldVfx.register` with a `VfxRenderer`. |
| An effect the server starts | [Named effects](#from-the-server): `VfxEffects.register`, then `VfxControl.play`. |
| A tear in the air | [Rifts](#rifts): `WorldRifts`, or `RiftControl` from the server. |

## Materials

A `VfxMaterial` is what a shape is drawn with: a texture, a blend, and a softness.

```java
VfxMaterial glow  = VfxMaterial.additive(VfxMaterial.GLOW);        // light: adds to the scene
VfxMaterial smoke = VfxMaterial.alpha(VfxMaterial.SMOKE);          // painted over, can darken
VfxMaterial mine  = VfxMaterial.luminous(MyMod.id("textures/vfx/flare.png")).withSoftness(0.3F);
```

| Blend | For |
|---|---|
| `VfxBlend.ADDITIVE` | Light, fire, magic. Overlaps brighten each other and black adds nothing. Washes out against a bright sky. |
| `VfxBlend.ALPHA` | Smoke and dust: painted over by alpha, so it can darken as well as lighten. |
| `VfxBlend.LUMINOUS` | Glow textures drawn on black with no alpha channel: painted over, with the alpha taken from each texel's brightness. |

`softness` is over how many blocks an effect fades out as it nears whatever is behind it (`VfxMaterial.DEFAULT_SOFTNESS` is 0.6; 0 is a hard edge).

Built-in textures, white with their brightness in alpha so they take any color and work with either blend: `GLOW`, `SPARKLE`, `STAR`, `RING`, `SMOKE` and `STREAK`.

## Effect particles

A `ParticleSpec` describes a kind of particle once; `WorldParticles` spawns it as often as you like. Size, opacity and spin follow a `Curve` over each particle's life, so particles swell and fade instead of popping in and out.

```java
static final ParticleSpec EMBER = ParticleSpec.builder(VfxMaterial.additive(VfxMaterial.GLOW))
        .lifetime(20, 35)                                   // each lives a random 20 to 35 ticks
        .size(Curve.of(0.12F, 0.2F, 0.0F).easing(Easing.EXPO_OUT, Easing.SINE_IN))
        .opacity(Curve.FADE)                                // quickly in, slowly out
        .color(0xFFD27A, 0xFF4A1C)                          // shifts around the color wheel
        .gravity(-0.004F)                                   // negative: drifts up
        .drag(0.94F)
        .build();

WorldParticles.spawn(EMBER, x, y, z, 0.0, 0.05, 0.0);
WorldParticles.burst(EMBER, pos, 24, 0.2, 0.12);           // 24 of them, flying apart
WorldParticles.line(EMBER, from, to, 30, 0.01);            // strung along a line
```

| Builder call | What it sets |
|---|---|
| `shape(Shape.SPARK)` | A streak along the motion, longer the faster it moves (`stretch(ticks)` sets how many ticks of motion it covers). The default is `BILLBOARD`, always facing the camera. |
| `lifetime(ticks)`, `lifetime(min, max)` | How long it lives. A range keeps a burst from vanishing all at once. |
| `size(Curve)`, `opacity(Curve)` | Half its width in blocks, and its opacity, over its life. |
| `color(rgb)`, `color(start, end)`, `colorEasing` | Its color, or a shift that stays vivid instead of passing through grey. |
| `spin(Curve)`, `randomRoll()` | Turning in radians a tick (either way, at random), and a random starting angle. |
| `gravity(f)`, `drag(f)` | Blocks a tick it speeds up downwards, and what its speed is multiplied by each tick (0 to 1). |

A `Curve` has three points: it eases from `start` to `middle` over the first half of the life, and from `middle` to `end` over the second.

```java
Curve.constant(0.25F)                                              // always 0.25
Curve.of(0.0F, 1.0F)                                               // grows, linearly
Curve.of(0.0F, 1.0F, 0.0F)                                         // in, then out again
Curve.of(0.2F, 0.6F, 0.0F).easing(Easing.EXPO_OUT, Easing.SINE_IN) // bursts open, then drifts shut
```

!!! note "Not vanilla particles"
    Effect particles aren't registered, don't exist on the server and ignore the Particles video setting. They are drawn after the whole world, so glows show through water and clouds. At most `WorldParticles.MAX` (16384) live at once, and they are dropped when the player leaves the world. Spawn them on the client thread.

## One-liners

Shapes that appear, draw themselves and fade, with nothing to keep track of:

```java
VfxMaterial glow = VfxMaterial.additive(VfxMaterial.GLOW);

WorldVfx.beam(VfxMaterial.additive(VfxMaterial.STREAK), from, to, 0.4F, 0xFF9AD8FF, 10);   // narrows as it fades
WorldVfx.flash(glow, pos, Curve.of(0.2F, 1.5F), 0xFFFFC870, 8);                           // swells and fades
WorldVfx.groundFlash(VfxMaterial.additive(VfxMaterial.RING), pos, Curve.of(0.5F, 4.0F), 0xFF7AD8FF, 12);  // a shockwave on the ground
```

Colors here are `0xAARRGGBB`; sizes are half widths in blocks.

## Draw every frame

For shapes that follow something (a tether between two entities, a glowing sword, a rune under a player), register a `VfxRenderer`. It's called every frame a world is drawn, until you unregister it; registering an id again replaces it.

```java
WorldVfx.register(MyMod.id("tether"), frame -> {
    Vec3 a = first.getPosition(frame.partialTick());
    Vec3 b = second.getPosition(frame.partialTick());
    frame.draw(VfxMaterial.additive(VfxMaterial.STREAK)).beam(a, b, 0.25F, 0xFFB070FF, 0xFF70D0FF);
});

WorldVfx.unregister(MyMod.id("tether"));
```

`VfxFrame` gives the `level()`, `partialTick()`, `time()` (seconds, stopped while the game is paused), `cameraPosition()`, and `draw(material)`, which returns a `VfxDraw`:

| `VfxDraw` method | Draws |
|---|---|
| `billboard(position, halfSize, argb)` (or with `x, y, z` and a `roll`) | A square facing the camera. |
| `beam(from, to, width, argb)`, `beam(from, to, width, fromArgb, toArgb)` | A ribbon between two points, turned to the camera. |
| `flat(x, y, z, halfSize, yaw, argb)` | A square lying flat, facing up. |
| `quad(a, b, c, d, argb)` | A flat quad through four corners, seen from both sides. |
| `quad(a, b, c, d, u0, v0, u1, v1, argb)` | The same with UVs of your choosing, for [shader materials](#shader-materials). |
| `trail(trail, width, headArgb, tailArgb)` | The ribbon a `Trail` leaves. |

Nothing drawn lasts: draw again every frame.

### Trails

A `Trail` remembers where something was over its last few ticks. Feed it a position every client tick and draw it every frame:

```java
Trail trail = new Trail(12);                 // twelve ticks of movement

// every client tick
trail.add(bolt.position());                  // or trail.shrink() once it stops, to let the tail catch up

// every frame, in a VfxRenderer
frame.draw(VfxMaterial.additive(VfxMaterial.STREAK)).trail(trail, Curve.of(0.3F, 0.0F), 0xFFFFE9A0, 0x00FF7A2A);
```

The width curve runs from the newest point to the oldest.

## From the server

The server can't draw, but it can ask clients to play an effect they know by name. Register effects on the client, from a `ClientModConstructor`:

```java
VfxEffects.register(MyMod.id("impact"), (level, params) -> {
    WorldParticles.burst(EMBER, params.position(), Math.round(20 * params.scale()), 0.1, 0.15);
    WorldVfx.flash(VfxMaterial.additive(VfxMaterial.GLOW), params.position(), Curve.of(0.2F, 1.2F), 0xFF000000 | params.color(), 6);
});
```

Then play them from the server:

```java
VfxControl.play(level, MyMod.id("impact"), VfxParams.at(hit, 0xFF7A2A));             // everyone within 64 blocks
VfxControl.play(level, MyMod.id("bolt"), VfxParams.between(from, to, 0x9AD8FF), 128); // a range of your own
VfxControl.play(player, MyMod.id("impact"), VfxParams.at(hit).withScale(2.0F));      // one player
```

`VfxParams` carries a `position`, a `target` (where a beam ends, or a direction), a `color` (`0xRRGGBB`) and a `scale`; what they mean is up to the effect. One call is one small packet to each player in range, in place of the dozens a burst of vanilla particles takes. Clients without the effect ignore it, and players who can't receive it (no CoolCatLib, fake players) are skipped. `VfxEffects.play(id, params)` plays one on the local client alone.

## Rifts

A rift is a tear in the air, drawn by a built-in shader and run through three acts: it **opens** (a flash, a jagged slit that widens, its opening sound, a shake of the camera nearby), stays **open** (its edge crawls, motes are drawn into it, its ambient sound loops) and **closes** (it narrows to a line and snaps shut with a last flash and its closing sound). It always turns to face whoever looks at it.

```java
Rift rift = WorldRifts.open(RiftSpec.DEFAULT, pos);         // stays until closed
rift.close();

WorldRifts.open(RiftSpec.DEFAULT, pos, 100);                // closes by itself after five seconds open
```

A `RiftSpec` sets how it looks and sounds:

```java
RiftSpec ember = RiftSpec.builder()
        .size(3, 5)                                          // width and height, in blocks
        .color(0xFF7030)                                     // edge and glow; the inside is a dark shade of it
        .timing(30, 12)                                      // ticks to open, ticks to close
        .sounds(MySounds.TEAR, MySounds.HUM, MySounds.SNAP)  // open, ambient (looped), close; any may be null
        .particles(EMBER, 2.0F)                              // motes drawn in while open, per tick
        .shake(1.5F)                                         // camera shake nearby, in degrees
        .build();
```

`RiftSpec.DEFAULT` is a violet rift two blocks wide and four tall with vanilla's portal sounds. `Rift` reports its `phase()` (`OPENING`, `OPEN`, `CLOSING`, `GONE`) and `openness(partialTick)`; `remove()` drops it with no closing act, and `WorldRifts.clear()` drops them all.

From the server, open a rift the clients registered by name:

```java
// client, in a ClientModConstructor
WorldRifts.register(MyMod.id("ember_rift"), ember);

// server
UUID id = RiftControl.open(level, MyMod.id("ember_rift"), pos);   // stays...
RiftControl.close(level, id);                                      // ...until closed
RiftControl.open(level, RiftControl.DEFAULT, pos, 100);            // the default rift, closing by itself
```

!!! warning "Rifts aren't saved"
    A rift is not an entity: players who come into range (96 blocks) or log in after it opened don't see it. For one that must last, open it again for them, or attach it to something that already syncs.

## Shader materials

`VfxMaterial.withShader(id)` draws a material with a fragment shader of your own instead of the textured one, for what a texture can't do: portals, heat haze, anything procedural. The id `mymod:vfx/portal` reads `assets/mymod/shaders/vfx/portal.fsh`.

```glsl title="assets/mymod/shaders/vfx/portal.fsh"
#version 330
#moj_import <coolcatcanvas:vfx.glsl>      // VfxTime.x (seconds), vfx_depth_fade(...)

uniform sampler2D Sampler0;               // the material's texture
uniform sampler2D DepthSampler;           // the scene's depth

in vec2 texCoord;                         // the quad's UVs, as given to VfxDraw
in vec4 vertexColor;                      // the color given to VfxDraw

out vec4 fragColor;

void main() {
    float swirl = 0.5 + 0.5 * sin(length(texCoord - 0.5) * 30.0 - VfxTime.x * 4.0);
    fragColor = vec4(vertexColor.rgb * swirl, vertexColor.a);
    // Hide behind solid things, fading softly where they meet.
    fragColor.a *= vfx_depth_fade(texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r, gl_FragCoord.z);
}
```

A shader has no uniforms of its own: what changes from one shape to the next travels in the color and the UVs, which is why `VfxDraw.quad` takes any UV range. `VfxMaterial.RIFT_SHADER` is the shader rifts are drawn with.
