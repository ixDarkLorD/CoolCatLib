---
icon: material/movie-open-outline
description: Camera paths, letterbox bars, fades, subtitles and an in-game editor
---

# Cutscenes

A camera flown along keyframes while the player watches, with letterbox bars, fades, subtitles and markers in time with it. Package: `net.ixdarklord.coolcatcanvas.api.cutscene` (both sides) and `api.client.cutscene` (client).

!!! info "Since"
    CoolCatLib **26.1.2-5** (Canvas 26.1.2-7 before the merge), on Minecraft 26.1 – 26.1.2. Not in the 1.21.1 and 1.20.1 versions.

A `Cutscene` is plain data, the same on both sides. It can be built in code, read from a resource pack, made in the [in-game editor](#the-in-game-editor), or sent whole by the server.

## Build one

```java
Cutscene intro = Cutscene.builder()
        .keyframe(0, new Vec3(10, 80, 10), 135, 20)                       // time, position, yaw, pitch
        .keyframe(100, new Vec3(-4, 72, 30), 200, 5).easing(Easing.SINE_IN_OUT).fov(45)
        .keyframe(160, new Vec3(-6, 70, 42), 180, 0)
        .letterbox()                                                      // cinema bars, sliding in and out
        .fadeIn(15).fadeOut(15)
        .subtitle(20, 70, Component.literal("Long ago..."))
        .marker(100, MyMod.id("thunder"))
        .build();
```

Times are ticks from the start, and fractions are allowed. Calls that shape a keyframe act on the one added last:

| Keyframe call | What it does |
|---|---|
| `keyframe(time, position, yaw, pitch)` | Adds a pose the camera passes through. Yaw is Minecraft's (0 looks south, 90 west), pitch is degrees down. The camera always turns the short way round. |
| `easing(Easing)` | How the move into this keyframe is paced. |
| `interpolation(Interpolation)` | Its shape: `SMOOTH` (a curve through the neighbors, the default), `LINEAR`, or `CUT`. `cut()` is a shortcut for a cut. |
| `roll(degrees)` | A tilt around the line of sight: a dutch angle. |
| `fov(degrees)` | The field of view: narrow to zoom in. Without one, the player's own setting is used. |

And for the whole cutscene:

| Call | What it does |
|---|---|
| `duration(ticks)` | How long it lasts; the last keyframe's time unless set. Longer holds the last pose. |
| `anchor(CutsceneAnchor)` | Where positions are measured from (see [Where it plays](#where-it-plays)). |
| `letterbox()`, `letterbox(aspect)`, `bars(ValueTrack)` | Bars framing an aspect ratio (`Cutscene.CINEMATIC_ASPECT`, 2.39, by default), or a track of how far they are in. |
| `fadeIn(ticks)`, `fadeOut(ticks)`, `fade(ValueTrack)`, `fadeColor(rgb)` | Fades from and to a color, or a track for fades in the middle. |
| `focus(CameraFocus)`, `lookAt(point)` | Keep the camera looking at something (see [Looking at things](#looking-at-things)). |
| `subtitle(start, duration, text)` | A line of text at the foot of the screen. |
| `marker(time, id)` | A named moment that fires an event on a beat: a sound, an effect, a shake. |
| `blendIn(ticks)`, `blendOut(ticks)` | Glide from the player's own view into the first keyframe, and back at the end, instead of cutting. |
| `hideHud(bool)`, `lockInput(bool)`, `skippable(bool)` | Hide the HUD and hand, hold the player still, let them skip. All on unless set. |
| `editable(false)` | Keep it out of the in-game editor, so players can't load a copy of it. |

`ValueTrack` is a number over time, as keys: `ValueTrack.of(0, 0.0F, 15, 1.0F)` is 0 at the start and 1 from tick 15 on; `ValueTrack.EMPTY.then(0, 1.0F).then(20, 0.0F, Easing.SINE_OUT)` eases from 1 down to 0.

!!! warning "Stay near the player"
    The camera only shows what the client has loaded: the chunks around the player, not around the camera. Keep a path within the player's view distance of where they stand.

## Play it

On the client, register a cutscene under an id and play it, or play one directly:

```java
Cutscenes.register(MyMod.id("intro"), intro);
CutscenePlayback playback = Cutscenes.play(MyMod.id("intro"));

Cutscenes.play(Cutscene.builder()...build());       // no id needed
if (Cutscenes.isPlaying()) Cutscenes.stop();
```

One cutscene plays at a time; starting another ends the first. While it plays, the camera follows it and, by its own settings, the HUD and hand are hidden and the player can't move, look or act. Pausing the game pauses it, and players skip a skippable one by holding the skip key (Enter by default).

`play` returns a `CutscenePlayback` (null if there's no such cutscene or the player isn't in a world):

```java
CutscenePlayback playback = Cutscenes.play(MyMod.id("intro"));
if (playback != null) {
    playback.setSpeed(0.5F);                                         // slow motion
    playback.onEnd(reason -> { if (reason.completed()) showTitle(); });
}
```

| `CutscenePlayback` | |
|---|---|
| `time()`, `progress()`, `isActive()` | How far in it is, in ticks and as 0 to 1, and whether it's still on screen. |
| `pause()`, `resume()`, `seek(ticks)`, `setSpeed(f)` | Control it. Markers jumped over by `seek` don't fire. |
| `stop()`, `skip()` | End it, as stopped or as if skipped. |
| `onEnd(reason -> ...)` | Runs once it ends: `FINISHED`, `SKIPPED`, `STOPPED`, `REPLACED` or `LEFT_WORLD`. |
| `pose()` | The camera this frame, in the world. |

### Where it plays

A cutscene's `CutsceneAnchor` says what its positions mean:

| Anchor | Positions are |
|---|---|
| `WORLD` (default) | World coordinates: the cutscene belongs to one place. |
| `PLAYER` | Offsets from the player's feet as it starts: it plays wherever they stand. |
| `PLAYER_FACING` | Offsets turned with the way the player faces: +Z ahead of them, +X to their left, and a yaw of 0 looks the way they look. |

`Cutscenes.playAt(id, origin, yaw)` plays any cutscene measured from a point of your choosing, turned by a yaw, whatever its own anchor says: one orbit, played around any altar.

### Looking at things

A `CameraFocus` keeps the camera looking at something for a stretch, whatever its keyframes' angles say. The camera still follows its path; only where it looks changes.

```java
.focus(CameraFocus.onPoint(0, 200, altar))                  // the whole shot looks at the altar
.focus(CameraFocus.onEntity(40, 120, dragon).blend(15))     // turns to the dragon over 15 ticks, and back after
.focus(CameraFocus.onPlayer(0, 60))                         // looks at the player watching
```

## Resource packs

Cutscenes can also be files at `assets/<namespace>/cutscene/<path>.json`, played by the id `<namespace>:<path>`. Resource packs can add and replace them, and they reload with resources. A cutscene registered from code wins over a file with the same id.

```json title="assets/mymod/cutscene/intro.json"
{
  "keyframes": [
    { "time": 0, "pos": [10, 80, 10], "yaw": 135, "pitch": 20 },
    { "time": 100, "pos": [-4, 72, 30], "yaw": 200, "pitch": 5, "fov": 45, "easing": "sine_in_out" },
    { "time": 160, "pos": [-6, 70, 42], "yaw": 180, "pitch": 0, "interpolation": "linear" }
  ],
  "anchor": "world",
  "letterbox": 2.39,
  "fade": [ { "time": 0, "value": 1 }, { "time": 15, "value": 0 }, { "time": 145, "value": 0 }, { "time": 160, "value": 1 } ],
  "fade_color": "#000000",
  "subtitles": [ { "start": 20, "duration": 70, "text": "Long ago..." } ],
  "markers": [ { "time": 100, "id": "mymod:thunder" } ],
  "blend_in": 0,
  "blend_out": 20,
  "hide_hud": true,
  "lock_input": true,
  "skippable": true
}
```

Keyframes also take `roll`; a `focus` list takes entries with `start`, `end`, `blend` and a `point` or an `entity` UUID. Easings are written by name, the `Easing` constants in lower case; a mod's own curve gets a name with `Easings.register("mymod:wobble", ...)`. A cutscene whose easings have no name can still play where it was built, but can't be saved or sent.

## From the server

`CutsceneControl` plays cutscenes on a player's client:

```java
CutsceneControl.play(player, MyMod.id("intro"));                  // one the client knows by id
CutsceneControl.play(player, MyMod.id("reveal"), cutscene);       // one only the server knows: sent whole
CutsceneControl.playAt(player, MyMod.id("orbit"), altar, 0.0F);   // measured from the altar
CutsceneControl.stop(player);
Identifier watching = CutsceneControl.watching(player);           // null when they aren't watching one
```

`play` returns false when the player's client can't be told (no CoolCatLib, or a fake player).

!!! warning "The server does nothing to the player"
    The client holds the player still and hides their HUD, but on the server they can still be hurt, pushed and targeted while they watch. Keep them safe yourself if the scene calls for it.

## Events

On the client, `CutsceneEvents`:

| Event | When |
|---|---|
| `BEFORE_START` | Before any cutscene starts. Return `EventResult.INTERRUPT` to keep it from playing. |
| `STARTED`, `TICK`, `ENDED` | It starts, every tick it plays, and when it ends (with the reason). |
| `KEYFRAME`, `MARKER`, `SUBTITLE` | The camera reaches a keyframe, passes a marker, shows a subtitle. |
| `PAUSED` | It's paused or resumed. |
| `CAMERA` | Every frame, with the camera pose; may hand back a changed one. |
| `BEFORE_SKIP` | Before the player skips it. Return `EventResult.INTERRUPT` to refuse. |

```java
CutsceneEvents.MARKER.register((playback, marker) -> {
    if (marker.equals(MyMod.id("thunder"))) CameraShake.shake(2.5F, 20);
});
```

On the server, `ServerCutsceneEvents` reports what each player's client reaches: `STARTED`, `TICK`, `KEYFRAME`, `MARKER` and `ENDED`, each with the player and the cutscene's id. `ENDED` says how it ended:

```java
ServerCutsceneEvents.ENDED.register((player, cutscene, reason) -> {
    if (cutscene.equals(MyMod.id("intro")) && reason.completed()) giveStarterKit(player);
});
```

## Letterbox bars on their own

For a boss entrance or a scripted moment the player still controls, `Letterbox` shows the bars without a cutscene:

```java
Letterbox.show(20);              // cinema bars, in over a second
Letterbox.show(1.85F, 10);       // another aspect ratio
Letterbox.hide(20);
```

## The in-game editor

`Cutscenes.openEditor()` opens the editor, which players also reach with a key (unbound by default) or, in development, `/coolcatcanvas_client cutscene editor`. Fly to a spot and press K to drop a keyframe; the path is drawn in the world, one dot a tick, so spacing shows speed. Its screen has a timeline to scrub and drag keyframes on, the selected keyframe's numbers, easing and path, the cutscene's letterbox, blends, fades and end hold, a live preview through the camera, and Play.

It saves JSON to `config/coolcatcanvas/cutscenes/`, playable as `coolcatcanvas:saved/<name>` and ready to copy into a resource pack. Focuses, subtitles and markers are kept when a cutscene is loaded but are edited in the file.
