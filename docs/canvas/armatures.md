---
icon: material/human-handsup
description: Animated models from Blockbench for mobs, entities, blocks and items
---

# Armatures

An armature is a model with joints and animation clips, made in Blockbench and drawn by code: a mob that walks and pounces, a turret block that watches players, an item that idles in the hand. Clips blend into each other on layers, joints can look at things or swing on springs, and the server can play clips that every player sees. Packages: `net.ixdarklord.coolcatcanvas.api.client.armature` (client, with `render`, `vanilla` and `molang`) and `api.armature` (both sides).

!!! info "Since"
    On 26.1.2, 26.2 and 26.3 since **26.1.2-6** (26.2-6, 26.3-6). On 1.21.1 and 1.20.1 since **9.0.0**, with the same API; see [below](#on-1211-and-1201) for what differs.

| You want | Use |
|---|---|
| A mob drawn with an armature | [`ArmatureMobRenderer`](#mobs-and-entities) |
| A projectile, vehicle or prop | [`ArmatureEntityRenderer`](#mobs-and-entities) |
| A block entity | [`ArmatureBlockRenderer`](#blocks), a [`BlockRenderer`](block-entity-renderers.md) |
| An item | [`ArmatureItems`](#items) and the item's model file |
| Clips on vanilla mobs and players | [`VanillaModels`](#vanilla-models) |
| A clip every player sees | [`ArmatureControl`](#from-the-server), from the server |

## The files

`Armature.of("mymod", "spirit")` names an armature; keep it in a constant. It's read from `assets/mymod/armatures/`, the first time it's drawn and again when resource packs reload:

1. `spirit.armature.json`, if there is one: a definition that names the model (`"model"`), extra clip files (`"animations"`), textures and how the armature behaves (below).
2. `spirit.bbmodel`: a Blockbench project, with its animations and textures inside it.
3. `spirit.geo.json` with `spirit.animation.json` beside it (Bedrock exports), textured by `assets/mymod/textures/armatures/spirit.png`.

Sub-folders work: `Armature.of("mymod", "mobs/spirit")`. An `.animation.json` with no model is enough for clips meant for another model, such as emotes for the [vanilla player](#vanilla-models): its bones become joints.

Blockbench's groups are the joints, its locators the points things attach to, and its sound, particle and timeline keyframes [events](#events). The definition, next to the model, adds what Blockbench doesn't hold:

```json title="assets/mymod/armatures/spirit.armature.json"
{
  "materials": { "halo": "emissive" },
  "springs": {
    "tail": { "stiffness": 70, "damping": 5 },
    "tail_tip": { "stiffness": 55, "damping": 4, "gravity": 2 },
    "ear_left": { "stiffness": 260, "damping": 9, "max_angle": 30 }
  },
  "look_at": {
    "head": { "max_yaw": 60, "max_pitch": 35, "smoothing": 0.15 }
  },
  "idle": "idle",
  "props": [
    { "point": "halo", "model": "mymod:models/obj/gem.obj", "texture": "minecraft:textures/block/amethyst_block.png",
      "offset": [0, 3, 0], "material": "cutout" }
  ],
  "transition": { "duration": 0.25, "easing": "sine_in_out" },
  "transitions": [
    { "from": "walk", "to": "idle", "duration": 0.45, "easing": "cubic_out", "mode": "freeze" }
  ]
}
```

| Key | What it does |
|---|---|
| `model`, `animations`, `textures`, `glow_textures` | The model file, more clip files, and textures by slot (otherwise the model's own). |
| `materials` | How a joint is drawn, passed down to its children: `cutout` (the default), `cutout_cull`, `solid`, `translucent`, `emissive` or `glow`. One model can mix a solid body, see-through wings and a glowing halo. |
| `springs` | Joints that lag behind and swing back, as tails, ears and capes do: `stiffness`, `damping`, `gravity`, `max_angle`, and more for free parts and cloth. |
| `look_at` | Joints that turn to look: `max_yaw`, `max_pitch`, `smoothing`. A living entity's follow where it looks; anything else follows the nearest player in range. |
| `idle` | A clip looped on the base layer whenever nothing else plays. |
| `props` | `.obj` models carried on a joint or point (a sword in a hand, a gem over a head), for shapes cubes do badly: `point`, `model`, `texture`, `offset`, `rotation`, `scale`, `material`. |
| `transition`, `transitions` | How clips blend into each other: the default, and for pairs of clips (`from`, `to`, optionally `layer`). |
| `origin`, `scale` | `corner` or `center`, for models not made around the middle of a block; a size for the whole model. |

## Animators

Every entity, block entity and item stack drawn with an armature has its own `Animator`, set up by its renderer's `animate(...)`. It plays clips on **layers**, stacked in the order they're made, later ones on top:

```java
static final Motion IDLE = Motion.loop("idle").fade(0.3F);
static final Motion WALK = Motion.loop("walk").fade(0.2F);

renderer.animate(animator -> {
    animator.layer("base").drive(ctx -> ctx.moving() ? WALK : IDLE);                // asked every frame
    animator.layer("halo", layer -> layer.mask(JointMask.of("halo"))).play(Motion.loop("halo_spin"));
    animator.layer("action");                                                       // for one-off clips, on top
    animator.lookAt("head").maxYaw(70);
    animator.spring("tail").damping(6);
});
```

- **`drive`** gives a layer a function it asks every frame for the motion to play; it blends whenever the answer changes. `play` plays something regardless, and once that ends the driver takes over again.
- **`mask`** limits a layer to some joints: `JointMask.of("arm_left", "arm_right")` (with everything on them), `only("head")`, `all().except("tail")`. One layer waves the arms while another walks the legs.
- **`blend(LayerBlend.ADDITIVE)`** adds a layer's clip on top of the ones below instead of replacing them (breathing over any pose), and `weight` / `fadeWeight` how much of it shows.
- **Procedural motion:** `lookAt(joint)` (`range`, `smoothing`, `target`), `spring(joint)`, `trail(point)` (a glowing wake), `emitter(point, particleSpec)`, `modify(...)` for any change to the pose, and `hide`, `show`, `tint`, `glow` per joint.

`ArmatureContext`, which drivers and listeners get, holds the owner (`entity()`, `living()`, `blockEntity()`, `stack()`, or typed with `ctx.<Wisp>owner()`), its clock, and what it's doing: `moving()`, `groundSpeed()`, `onGround()`, `inWater()`, `headYaw()`, `health()` and more.

### Motions and transitions

A `Motion` says how to play one clip. It's an immutable value, known on both sides, so keep it in a constant:

```java
static final Motion RUN = Motion.loop("run").transition(Transition.crossfade(0.3F));
static final Motion ATTACK = Motion.once("attack").speed(1.5F).then(Motion.loop("idle"));
static final Motion SPIN = Motion.loop("spin").repeat(3).fade(0.1F).ease(Easing.BACK_OUT);
```

`of` plays the clip as its file says, `loop`, `once`, `hold` (stays on its last frame) and `pingPong` set how it ends. A `Transition` says how one clip gives way to the next: `crossfade(seconds)` (both move while they blend), `freeze(seconds)` (the old pose holds still while the new one blends in) or `CUT`. A motion that names none blends the way its layer says: `layer.transition(...)` for its default, `layer.transition(from, to, ...)` for a pair, or `layer.transitions((from, to, ctx) -> ...)` to pick each time.

### Events

Clips' sound, particle and timeline keyframes reach `animator.onEvent(event -> ...)`. A sound naming a sound event (`minecraft:entity.wolf.growl`) and a particle naming a simple particle type (`minecraft:flame`) play by themselves (`builtInEffects(false)` turns that off); a particle naming a [registered `VfxEffect`](world-effects.md#from-the-server) plays it at the keyframe's locator.

Clips can use Molang: variables (`v.pounces`, set with `animator.setVariable` or from the server) and queries (`query.ground_speed`, `query.is_on_ground` and the other common ones are built in). Add queries of your own with `ArmatureQueries.register("query.mymod_charge", ctx -> ...)` while the client sets up.

## Mobs and entities

Register them with Canvas's `EntityRendererRegistry`, from `ClientModConstructor#onConstructMod()`:

```java
static final Armature SPIRIT = Armature.of("mymod", "spirit");

EntityRendererRegistry.register(MyEntities.SPIRIT, ArmatureMobRenderer.<SpiritCat>of(SPIRIT, 0.4F, renderer -> renderer
        .animate(animator -> animator.layer("base").drive(ctx -> ctx.moving() ? WALK : IDLE))
        .holdItem("right_hand", EquipmentSlot.MAINHAND)));

EntityRendererRegistry.register(MyEntities.BOLT, ArmatureEntityRenderer.of(Armature.of("mymod", "bolt"), renderer -> renderer
        .pitch(true)
        .animate(animator -> animator.play(Motion.loop("spin")))));
```

`ArmatureMobRenderer` (the `0.4F` is the shadow's radius) keeps everything vanilla does for mobs: the hurt flash, the death topple (`deathFlip(false)` for a death clip of your own), sleeping, invisibility, the glowing outline, name tags, leashes, and any `RenderLayer`s you add. It also has `texture(...)` and `armature(...)` per entity (variants), `tint`, `translucent`, `glow`, `scale`, `attach(point, attachment)` to draw your own things at a point, and `holdItem` / `socket(ItemSocket)` for items at a point. Subclass it and override `setupAnimator` for typed access to the entity.

`ArmatureEntityRenderer` draws any other entity, turned with its yaw and, with `pitch(true)`, its pitch.

## Blocks

`ArmatureBlockRenderer` is a [`BlockRenderer`](block-entity-renderers.md#armatures), so culling, detail bands, facing and layers all work on it:

```java
BlockEntityRendererRegistry.register(MyBlockEntities.STATUE, ArmatureBlockRenderer.<StatueBlockEntity>of(SPIRIT, renderer -> renderer
        .detail(Detail.FULL, 24)
        .facing(Facing.HORIZONTAL)
        .animate(animator -> {
            animator.lookAt("head").range(12);
            animator.layer("action");
        })));
```

It stands on the bottom of its block, its front (north in Blockbench) turned to the block's facing, and Blockbench "Java Block" models, whose origin is a corner, are moved to fit by themselves. Give the block `RenderShape.INVISIBLE` so only the armature shows.

## Items

An item drawn with an armature names it in its item model, `assets/mymod/items/<item>.json`:

```json title="assets/mymod/items/spirit_figurine.json"
{
  "model": {
    "type": "minecraft:special",
    "base": "mymod:item/spirit_figurine",
    "model": { "type": "coolcatcanvas:armature", "armature": "mymod:spirit", "animation": "idle" }
  }
}
```

`base` is an ordinary item model, for its display transforms and particle texture. `animation` is a clip looped for every stack, and `texture` swaps the first texture. For more, register a setup: each stack gets its own animator, whose context has the stack.

```java
ArmatureItems.register(MyItems.WAND, animator -> animator.layer("base")
        .drive(ctx -> ctx.stack().has(MyComponents.CHARGED) ? GLOW : IDLE));
```

## Vanilla models

`VanillaModels` animates vanilla mobs and players without replacing their models: clips on their parts, changes to their parts, and armatures worn on them. It works on any living entity drawn by a vanilla-style renderer, other mods' mobs included, and several mods can add to the same one.

```java
// Emotes: clips named after the player's parts (head, body, right_arm, left_arm, right_leg, left_leg).
VanillaModels.player()
        .animations(Armature.of("mymod", "emotes"))
        .mirrorLeftHanded(true)
        .animate(animator -> animator.layer("emote").transition(Transition.freeze(0.25F, Easing.CUBIC_OUT)))
        .pose((pose, ctx) -> { if (ctx.living() != null && ctx.living().isCrouching()) pose.scale("head", 1.3F); });

// A cat spirit on every zombie's head, with its own animator.
VanillaModels.of(EntityType.ZOMBIE).attach("head", SPIRIT).offset(0, 8, 0).scale(0.45F);

// Villagers get big heads.
VanillaModels.of(EntityType.VILLAGER).scale("head", 1.4F);
```

`hide`, `scale`, `rotate` and `translate` change parts, `when(entity -> ...)` limits it to some of them, and `firstPersonArms(true)` shows the player's animated arms in first person. `VanillaModels.play(entity, layer, motion)` plays a clip on this client alone.

## From the server

`ArmatureControl` (`api.armature`) plays clips from the server for everyone who can see the entity, block or held item. Each call is one small packet to the players tracking it; called on the client, it acts on that client alone.

```java
ArmatureControl.play(spirit, "action", Motion.once("pounce").fade(0.12F));          // an entity, on a layer
ArmatureControl.play(player, "emote", Motion.of("wave"));                          // a player's vanilla model
ArmatureControl.play(level, pos, "action", POUNCE);                                // a block entity
ArmatureControl.playHeld(player, InteractionHand.MAIN_HAND, "base", Motion.once("swing"));
ArmatureControl.setVariable(golem, "v.rage", 0.8);                                 // read by Molang in the clips
ArmatureControl.stop(player, "emote");
```

These are moments, not state: a player who comes into range later doesn't see a clip that started before. Keep lasting state (a mob being angry, a chest being open) in synced data, and have a layer's driver play from it.

## On 1.21.1 and 1.20.1

The API and files are the same. What differs:

- Renderers draw into a `MultiBufferSource` instead of submitting, as on those versions' [block entity renderers](block-entity-renderers.md#on-1211-and-1201): an `ArmatureAttachment` is `(frame, poseStack, buffers, light) -> ...`, and the mob and entity renderers have no render states.
- Those versions have no item model types, so an armature item names its armature in its own model, `assets/mymod/models/item/<item>.json`, which hands the item to code to draw. `translation` (in blocks) and `scale` (one number or three) place the armature in the item's cube:

```json title="assets/mymod/models/item/spirit_figurine.json"
{
  "parent": "builtin/entity",
  "textures": { "particle": "minecraft:block/amethyst_block" },
  "display": { "gui": { "rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625] } },
  "coolcatcanvas:armature": { "armature": "mymod:spirit", "animation": "idle", "translation": [0.25, 0.08, 0.25], "scale": 0.5 }
}
```

- Ids are `ResourceLocation`s instead of `Identifier`s, as everywhere on those versions.

## Reference

| Type | Description |
|---|---|
| `Armature` | `of(namespace, path)`, `of(id)`, `id()`, `get()` (the loaded model, replaced on resource reload), `NONE`. |
| `Animator` | `layer(name[, setup])`, `findLayer`, `layers()`, `play([layer, ]motion)`, `stop(layer)`, `isPlaying(clip)`, `transition`, `transitions(boolean)`, `lookAt`, `spring`, `prop`, `trail`, `emitter`, `modify`, `onEvent`, `builtInEffects`, `hide`, `show`, `setVisible`, `tint`, `glow`, `variable`, `setVariable`, `context()`. |
| `AnimLayer` | `blend`, `mask`, `weight`, `fadeWeight`, `speed`, `drive`, `transition([from, to, ]transition)`, `transitions(rule)`, `play`, `stop([fadeSeconds])`, `isPlaying([clip])`, `clip()`, `time()`, `progress()`. |
| `ArmatureContext` | The owner (`owner()`, `entity()`, `living()`, `blockEntity()`, `stack()`), `level()`, `position()`, clocks (`time`, `deltaTime`, `animTime`, `lifeTime`), movement (`moving`, `groundSpeed`, `verticalSpeed`, `onGround`, `inWater`, `limbSwing`), looks (`bodyYaw`, `headYaw`, `headPitch`), `health`, `dead`, Molang variables. |
| `JointMask`, `LayerBlend` | Which joints a layer moves; `OVERRIDE` or `ADDITIVE`. |
| `LookAt`, `Spring`, `Fabric` | Joints that look at things, swing on springs, or hang as cloth. |
| `ArmatureTrail`, `ArmatureEmitter`, `ObjProp` | A glowing wake from a point, particles from a point, an `.obj` model on a joint. |
| `ArmatureEvent` | A clip's keyframe: `kind()`, `effect()`, `locator()`, `time()`, `clip()`, `context()`. |
| `ArmatureMaterial` | `CUTOUT`, `CUTOUT_CULL`, `SOLID`, `TRANSLUCENT`, `EMISSIVE`, `GLOW`. |
| `TransitionRule` | Picks the transition between two clips on a layer. |
| `molang.ArmatureQueries` | `register(name, ctx -> value)`: Molang queries of your own. |
| `render.ArmatureMobRenderer`, `render.ArmatureEntityRenderer`, `render.ArmatureBlockRenderer` | The renderers, each with `of(armature, ...)`, `animate`, `texture`, `scale`, `attach`. |
| `render.ArmatureItems` | `register(item, setup)`: an animator setup for an item's stacks. |
| `render.ArmatureAttachment`, `render.ItemSocket` | Your own drawing at a point; an item at a point of a mob. |
| `vanilla.VanillaModels`, `vanilla.VanillaModel` | `player()`, `of(type)`; `animations`, `animate`, `pose`, `hide`, `scale`, `rotate`, `translate`, `attach`, `when`, `mirrorLeftHanded`, `firstPersonArms`; `play`, `stop`, `animator(entity)`. |
| `api.armature.Motion` | `of`, `loop`, `once`, `hold`, `pingPong`; `repeat`, `speed`, `transition`, `fade`, `ease`, `fadeOut`, `startAt`, `restart`, `then`. |
| `api.armature.Transition` | `crossfade(seconds[, easing])`, `freeze(seconds[, easing])`, `CUT`, `DEFAULT`; `withDuration`, `withEasing`. |
| `api.armature.LoopMode` | `ONCE`, `LOOP`, `HOLD`, `PING_PONG`. |
| `api.armature.ArmatureControl` | `play`, `stop`, `setVariable` on entities and block entities; `playHeld`, `setHeldVariable` on held items. |
