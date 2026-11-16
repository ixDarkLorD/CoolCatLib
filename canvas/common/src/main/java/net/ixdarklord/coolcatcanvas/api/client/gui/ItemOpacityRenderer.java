package net.ixdarklord.coolcatcanvas.api.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.ixdarklord.coolcatcanvas.api.utils.Easing;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Items drawn in a GUI at an opacity, fixed or animated: a HUD card fading in with its icon, a slot greying out.
 * <pre>{@code
 * ItemOpacityRenderer.render(graphics, stack, x, y, 0.5F);                      // half see-through
 * ItemOpacityRenderer.renderWithDecorations(graphics, font, stack, x, y, 0.5F); // with its count and bar
 *
 * var fade = ItemOpacityRenderer.OpacityTween.in(10).start(); // keep it, and draw each frame:
 * ItemOpacityRenderer.render(graphics, stack, x, y, fade);
 *
 * ItemOpacityRenderer.withOpacity(0.5F, () -> graphics.renderItem(stack, x, y)); // anything that draws items
 * }</pre>
 * The game draws most items solid (blocks, beds, anything with a block entity model), which an alpha can't see
 * through: it only darkens them. So while the shader color's alpha is under 1, every item a {@link GuiGraphics} draws
 * is drawn with a see-through version of its render type (the same texture, translucent), and fades as flat items do;
 * enchantment glints stay as they are. That holds for items the game itself draws too, so a mod that fades a part of
 * the HUD through the shader color fades its items with it.
 */
public final class ItemOpacityRenderer {
    // Below this, nothing would show.
    private static final float HIDDEN = 0.004F;

    private ItemOpacityRenderer() {}

    /** Draws an item as {@link GuiGraphics#renderFakeItem} does, at an opacity from 0 to 1. */
    public static void render(GuiGraphics graphics, ItemStack stack, int x, int y, float opacity) {
        withOpacity(opacity, () -> graphics.renderFakeItem(stack, x, y));
    }

    /** Draws an item at the opacity a fade is at now. */
    public static void render(GuiGraphics graphics, ItemStack stack, int x, int y, OpacityTween fade) {
        render(graphics, stack, x, y, fade.opacity());
    }

    /** Draws an item with its count and durability bar, as a slot shows it, at an opacity from 0 to 1. */
    public static void renderWithDecorations(GuiGraphics graphics, Font font, ItemStack stack, int x, int y, float opacity) {
        withOpacity(opacity, () -> {
            graphics.renderFakeItem(stack, x, y);
            graphics.renderItemDecorations(font, stack, x, y);
        });
    }

    /** Draws an item with its count and durability bar at the opacity a fade is at now. */
    public static void renderWithDecorations(GuiGraphics graphics, Font font, ItemStack stack, int x, int y, OpacityTween fade) {
        renderWithDecorations(graphics, font, stack, x, y, fade.opacity());
    }

    /**
     * Runs drawing at an opacity from 0 to 1: the shader color's alpha is scaled by it meanwhile, so the items drawn
     * (and the text and sprites, which already fade by it) are see-through. Nothing is drawn at 0. The shader color is
     * put back after.
     */
    public static void withOpacity(float opacity, Runnable draw) {
        float shown = Mth.clamp(opacity, 0.0F, 1.0F);
        if (shown < HIDDEN) return;
        if (shown >= 1.0F) {
            draw.run();
            return;
        }
        float[] color = RenderSystem.getShaderColor();
        float red = color[0], green = color[1], blue = color[2], alpha = color[3];
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(red, green, blue, alpha * shown);
        try {
            draw.run();
        } finally {
            RenderSystem.setShaderColor(red, green, blue, alpha);
        }
    }

    /**
     * An opacity eased from one value to another over a time, for an item fading in or out. Kept by whoever draws the
     * item and read each frame ({@link #opacity}); it runs on the clock, so it carries on smoothly whatever the frame
     * rate. Until {@link #start started}, it is at its first value.
     */
    public static final class OpacityTween {
        private final float from;
        private final float to;
        private final long millis;
        private final Easing easing;
        private long startedAt = Long.MIN_VALUE;

        private OpacityTween(float from, float to, int ticks, Easing easing) {
            this.from = Mth.clamp(from, 0.0F, 1.0F);
            this.to = Mth.clamp(to, 0.0F, 1.0F);
            this.millis = Math.max(1, ticks) * 50L;
            this.easing = easing;
        }

        /** From unseen to fully shown over so many ticks, easing out. */
        public static OpacityTween in(int ticks) {
            return new OpacityTween(0.0F, 1.0F, ticks, Easing.SINE_OUT);
        }

        /** From fully shown to unseen over so many ticks, easing in. */
        public static OpacityTween out(int ticks) {
            return new OpacityTween(1.0F, 0.0F, ticks, Easing.SINE_IN);
        }

        /** From one opacity to another over so many ticks, on an easing. */
        public static OpacityTween between(float from, float to, int ticks, Easing easing) {
            return new OpacityTween(from, to, ticks, easing);
        }

        /** Starts it now, from its first value (again, if it had run). Returns itself. */
        public OpacityTween start() {
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
