# Text and Colors

Animated gradients and rainbows, gradient and outlined text, and outlines around widgets. Packages: `net.ixdarklord.coolcatcanvas.api.utils` (`ColorGradient`, `Easing`) and `api.client.utils` (`TextEffects`, `Outline`), plus `api.client.gui.components.ElementOutlines`.

## Gradient components

A `ColorGradient` can color any component, and it stays animated wherever the component is drawn: chat, tooltips, item names, signs and widget labels.

```java
Component title = ColorGradient.RAINBOW.literal("Rainbow!");
Component fire = ColorGradient.FIRE.withSpeed(1.0F).apply(Component.literal("Hot"));
Component custom = ColorGradient.of(0xFF4080, 0x40A0FF).translatable("mymod.title");
Component still = ColorGradient.AURORA.withSpeed(0.0F).perLetter(12).literal("still aurora");
```

In JSON text and commands, the color is a string:

| Color | Meaning |
|---|---|
| `coolcatcanvas:rainbow` | `ColorGradient.RAINBOW` |
| `coolcatcanvas:rainbow_whole` | The whole text in one cycling color |
| `coolcatcanvas:rainbow/<speed>/<spread>/<saturation>/<brightness>` | A tuned rainbow; trailing values are optional |
| `coolcatcanvas:gradient/<speed>/<spread>/#RRGGBB/#RRGGBB...` | Your own color stops |

```
/tellraw @a {"text":"Hello","color":"coolcatcanvas:gradient/0.5/0.05/#FF4000/#FFD000"}
```

Players without Canvas can't read these colors.

## Drawing text

```java
// Keep the sequence: it re-animates each time it's drawn.
FormattedCharSequence title = TextEffects.rainbow(Component.literal("Rainbow Text").withStyle(s -> s.withBold(true)));

TextEffects.drawCenteredOutlinedText(graphics, font, Component.literal("Outlined"), centerX, 40, 0xFFFFFFFF, 0xFF202080);
TextEffects.drawGradientOutlinedText(graphics, font, text, x, 80, 0xFF000000, ColorGradient.RAINBOW.withSpeed(1.0F));
TextEffects.drawGradientText(graphics, font, "Gradient", x, y, ColorGradient.OCEAN, true);
```

## Outlines around widgets

```java
ElementOutlines.set(button, Outline.rainbow().withThickness(2).withPadding(1));
ElementOutlines.set(button, Outline.solid(0xFFFFAA00).withPadding(1), AbstractWidget::isHoveredOrFocused);
ElementOutlines.set(editBox, Outline.gradient(ColorGradient.OCEAN.withSpread(2.0F).withSpeed(-1.0F)), AbstractWidget::isFocused);
```

This works on any `AbstractWidget`, vanilla's included. Widgets are held weakly, so the outline goes away with its widget.

## Reference

| Type | Description |
|---|---|
| `ColorGradient` | Presets: `RAINBOW`, `RAINBOW_WHOLE`, `PASTEL_RAINBOW`, `FIRE`, `OCEAN`, `AURORA`. Factories: `of(int... rgb)`, `rainbow()`, `rainbow(saturation, brightness)`. Modifiers, which return copies: `withSpeed` (loops per second), `withSpread` (how far the colors stretch along the text), `perLetter(n)`, `wholeText()`, `withSaturation`, `withBrightness`. Sampling: `color(position)`, `sample(progress)`. Components: `literal(text)`, `translatable(key, args...)`, `apply(component)`, `applyTo(style)`, `textColor()`, `fromTextColor(textColor)`. |
| `TextEffects` | Sequences: `rainbow(...)`, `gradient(..., gradient)`, `recolor(sequence, color)`. Drawing: `drawGradientText`, `drawCenteredGradientText`, `drawOutlinedText`, `drawCenteredOutlinedText`, `drawGradientOutlinedText`. The outline is 1 px around each glyph. |
| `Outline` | Immutable: `solid(argb)`, `rainbow()`, `gradient(gradient)`, then `withThickness`, `withPadding` (negative draws inside), `withSegmentLength`, `withColor`; `draw(graphics, rectangle)`. A gradient runs clockwise from the top-left, and a spread of 1 goes around once. |
| `ElementOutlines` | `set(widget, outline[, when])`, `remove(widget)`, `get(widget)`, `has(widget)`, `clear()`. Render thread only. |
| `Easing` | Curves for any animation: `LINEAR`, `STEP`, `SINE_IN`/`_OUT`/`_IN_OUT`, `QUAD_*`, `CUBIC_*`, `EXPO_IN`, `EXPO_OUT`, `BACK_OUT`; `apply(progress)`, `applyClamped`, `reversed()`. It's a functional interface, so a lambda is an easing too. |
