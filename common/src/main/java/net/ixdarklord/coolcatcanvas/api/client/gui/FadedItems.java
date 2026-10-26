package net.ixdarklord.coolcatcanvas.api.client.gui;

import net.ixdarklord.coolcatcanvas.api.utils.Easing;
import net.ixdarklord.coolcatcanvas.internal.client.gui.GuiOpacity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * Items drawn in a GUI at an opacity, fixed or animated: a HUD card fading in with its icon, a slot greying out.
 * <pre>{@code
 * FadedItems.render(graphics, stack, x, y, 0.5F);                       // half see-through
 * FadedItems.renderWithDecorations(graphics, font, stack, x, y, 0.5F);  // with its count and bar
 *
 * FadedItems.Fade fade = FadedItems.Fade.in(10).start();              // keep it, and draw each frame:
 * FadedItems.render(graphics, stack, x, y, fade);
 *
 * FadedItems.withOpacity(0.5F, () -> graphics.item(stack, x, y));       // anything that draws items
 * }</pre>
 * The game draws GUI items from an atlas at full opacity, with no way to ask for less: each item drawn here remembers
 * the opacity it was asked for, and is blitted from the atlas with it, so solid and flat items fade alike. Inside
 * {@link #withOpacity} the text and filled rectangles drawn (an item's count, its durability bar) fade too.
 */
public final class FadedItems {
    // Below this, nothing would show.
    private static final float HIDDEN = 0.004F;

    private FadedItems() {}

    /** Draws an item as {@link GuiGraphicsExtractor#fakeItem} does, at an opacity from 0 to 1. */
    public static void render(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, float opacity) {
        withOpacity(opacity, () -> graphics.fakeItem(stack, x, y));
    }

    /** Draws an item at the opacity a fade is at now. */
    public static void render(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, Fade fade) {
        render(graphics, stack, x, y, fade.opacity());
    }

    /** Draws an item with its count and durability bar, as a slot shows it, at an opacity from 0 to 1. */
    public static void renderWithDecorations(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y, float opacity) {
        withOpacity(opacity, () -> {
            graphics.fakeItem(stack, x, y);
            graphics.itemDecorations(font, stack, x, y);
        });
    }

    /** Draws an item with its count and durability bar at the opacity a fade is at now. */
    public static void renderWithDecorations(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y, Fade fade) {
        renderWithDecorations(graphics, font, stack, x, y, fade.opacity());
    }

    /**
     * Runs drawing at an opacity from 0 to 1: the items, text and filled rectangles it draws are that much see-through
     * (on top of any opacity already in force). Nothing is drawn at 0.
     */
    public static void withOpacity(float opacity, Runnable draw) {
        float shown = Mth.clamp(opacity, 0.0F, 1.0F);
        if (shown < HIDDEN) return;
        if (shown >= 1.0F) {
            draw.run();
            return;
        }
        GuiOpacity.push(shown, false);
        try {
            draw.run();
        } finally {
            GuiOpacity.pop();
        }
    }

    /**
     * Fades the items drawn from now until {@link #popItemOpacity}, and only the items: for a caller that fades the
     * rest of what it draws itself (a whole HUD faded between two hooks, say). Calls nest; each push needs its pop.
     */
    public static void pushItemOpacity(float opacity) {
        GuiOpacity.push(Mth.clamp(opacity, 0.0F, 1.0F), true);
    }

    public static void popItemOpacity() {
        GuiOpacity.pop();
    }

    /**
     * An opacity eased from one value to another over a time, for an item fading in or out. Kept by whoever draws the
     * item and read each frame ({@link #opacity}); it runs on the clock, so it carries on smoothly whatever the frame
     * rate. Until {@link #start started}, it is at its first value.
     */
    public static final class Fade {
        private final float from;
        private final float to;
        private final long millis;
        private final Easing easing;
        private long startedAt = Long.MIN_VALUE;

        private Fade(float from, float to, int ticks, Easing easing) {
            this.from = Mth.clamp(from, 0.0F, 1.0F);
            this.to = Mth.clamp(to, 0.0F, 1.0F);
            this.millis = Math.max(1, ticks) * 50L;
            this.easing = easing;
        }

        /** From unseen to fully shown over so many ticks, easing out. */
        public static Fade in(int ticks) {
            return new Fade(0.0F, 1.0F, ticks, Easing.SINE_OUT);
        }

        /** From fully shown to unseen over so many ticks, easing in. */
        public static Fade out(int ticks) {
            return new Fade(1.0F, 0.0F, ticks, Easing.SINE_IN);
        }

        /** From one opacity to another over so many ticks, on an easing. */
        public static Fade between(float from, float to, int ticks, Easing easing) {
            return new Fade(from, to, ticks, easing);
        }

        /** Starts it now, from its first value (again, if it had run). Returns itself. */
        public Fade start() {
            this.startedAt = Util.getMillis();
            return this;
        }

        public boolean started() {
            return this.startedAt != Long.MIN_VALUE;
        }

        /** Whether it has reached its last value. */
        public boolean finished() {
            return this.started() && Util.getMillis() - this.startedAt >= this.millis;
        }

        /** The opacity now, 0 to 1. */
        public float opacity() {
            if (!this.started()) return this.from;
            float progress = (Util.getMillis() - this.startedAt) / (float) this.millis;
            return Mth.lerp(this.easing.applyClamped(progress), this.from, this.to);
        }
    }
}
