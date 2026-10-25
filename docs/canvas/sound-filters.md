---
icon: material/waveform
description: Muffle, roughen and echo the game's sounds
---

# Sound Filters

Muffle, roughen and echo the game's sounds: a player hiding in a chest hears the world through its walls, one under a spell hears it from far away, a cave rings. Package: `net.ixdarklord.coolcatcanvas.api.client.sound` (client) and `api.sound` (server).

!!! info "Since"
    Coming in **8.0.0**, on Minecraft 26.1 – 26.1.2. Not yet on 26.2, 26.3, 1.21.1 and 1.20.1.

Every sound gets the filters below combined: a low-pass filter on it, and as much of it as they say sent, filtered the same, through a distortion and into a reverb. It's done with OpenAL's EFX extension, which the game's own OpenAL has; without it, sounds are left as they are.

## A filter

A `SoundFilter` is four parts, each 0 to 1:

| Part | What it does |
|---|---|
| `gain` | How loud the sound stays; 1 is as loud as it was. |
| `gainHF` | How much of its high end it keeps. Losing the highs is what makes a sound muffled, as through a wall. |
| `distortion` | How much of it goes through a distortion, which makes it rough. |
| `reverb` | How much of it goes into a reverb, which makes it echo. |

```java
SoundFilter.muffled(0.8F)                            // through a thick wall
new SoundFilter(0.6F, 0.1F, 0.3F)                    // muffled and rough, as under water
SoundFilter.echoing(0.5F).then(SoundFilter.quieter(0.8F))
SoundFilter.NONE.lerp(underwater, fade)              // part of the way there, for a fade of your own
```

## Named filters

The simplest way: a filter on every sound at once (music included, but not the menus' clicks), which fades in and out on its own.

```java
SoundFilters.set(MyMod.id("underwater"), new SoundFilter(0.6F, 0.1F), 20);   // fades in over a second
SoundFilters.clear(MyMod.id("underwater"), 40);                             // and out over two
boolean on = SoundFilters.isSet(MyMod.id("underwater"));
```

Setting a name that's already on fades from where it is to the new filter. They're cleared when the player leaves the world.

From the server, `SoundFilterControl` does the same for one player, as one small packet (players without CoolCatLib are skipped):

```java
SoundFilterControl.set(player, MyMod.id("dazed"), 0.7F, 0.15F, 0.2F, 0.0F, 20);   // gain, highs, distortion, reverb, fade
SoundFilterControl.clear(player, MyMod.id("dazed"), 60);
```

## Providers

For filters that depend on each sound, register a `SoundFilterProvider`. It's asked about every sound as it starts and as it moves, with a `SoundContext`: where it plays, which sound event it is, its category, and whether it plays relative to the listener (music, the menus) rather than at a place in the world.

```java
// Only hostile mobs, muffled:
SoundFilters.register(sound -> sound.source() == SoundSource.HOSTILE ? SoundFilter.muffled(0.5F) : SoundFilter.NONE);

// By place, for the sounds in the world only:
SoundFilters.Registration walls = SoundFilters.register(SoundFilterProvider.inWorld(position -> hiding.filter(position)));

SoundFilters.refresh();   // after your provider's answers change: the sounds already playing are filtered again
walls.remove();           // stop asking it
```

!!! warning "The sound thread"
    Providers are asked on the sound thread, so they must be quick and read only state that is safe to read from there: an immutable snapshot the game thread swaps in, for example. Named filters need none of this.

## Reference

| Type | Description |
|---|---|
| `SoundFilter` | Record `(gain, gainHF, distortion, reverb)`, also made with `(gain, gainHF[, distortion])`. `NONE`, `muffled(amount)`, `quieter(gain)`, `distorted(amount)`, `echoing(amount)`; `then(other)`, `lerp(to, delta)`, `withDistortion`, `withReverb`, `isNone()`. |
| `SoundFilters` | `set(id, filter[, fadeTicks])`, `clear(id[, fadeTicks])`, `isSet(id)`; `register(provider)` (returns a `Registration` to `remove()`), `refresh()`. |
| `SoundFilterProvider` | `filter(SoundContext)`; `inWorld(position -> filter)`. |
| `SoundContext` | `position()`, `sound()` (the sound event's id, may be null), `source()`, `relative()`, `inWorld()`. |
| `api.sound.SoundFilterControl` | `set(player, id, gain, gainHF, distortion, reverb, fadeTicks)`, `clear(player, id, fadeTicks)`. |
