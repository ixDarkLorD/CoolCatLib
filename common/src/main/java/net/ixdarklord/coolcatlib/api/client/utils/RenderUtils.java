package net.ixdarklord.coolcatlib.api.client.utils;

import it.unimi.dsi.fastutil.ints.IntIterator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

@Environment(EnvType.CLIENT)
public final class RenderUtils {

    private static final Minecraft MC = Minecraft.getInstance();
    public static Rect2i EMPTY_RECT2I = new Rect2i(0, 0, 0, 0);
    public static final RenderStateShard.TransparencyStateShard TRANSLUCENT_TRANSPARENCY = RenderStateShard.TRANSLUCENT_TRANSPARENCY;

    // ------------------------------------------------------------------------
    // BASIC SHAPES
    // ------------------------------------------------------------------------

    public static Rect2i createRect2i(GuiEventListener listener) {
        if (listener == null) return EMPTY_RECT2I;
        return new Rect2i(listener.getRectangle().left(), listener.getRectangle().top(), listener.getRectangle().width(), listener.getRectangle().height());
    }

    public static void fillRect(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
    }

    public static void drawHollowRect(GuiGraphics graphics, ScreenRectangle rectangle, int thickness, int color) {
        drawHollowRect(graphics, rectangle.left(), rectangle.top(), rectangle.width(), rectangle.height(), thickness, color);
    }

    public static void drawHollowRect(GuiGraphics graphics, int x, int y, int width, int height, int thickness, int color) {
        // Top
        graphics.fill(x, y, x + width, y + thickness, color);
        // Bottom
        graphics.fill(x, y + height - thickness, x + width, y + height, color);
        // Left
        graphics.fill(x, y, x + thickness, y + height, color);
        // Right
        graphics.fill(x + width - thickness, y, x + width, y + height, color);
    }

    public static void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        // horizontal or vertical shortcuts
        if (y1 == y2) {
            graphics.hLine(Math.min(x1, x2), Math.max(x1, x2), y1, color);
        } else if (x1 == x2) {
            graphics.vLine(x1, Math.min(y1, y2), Math.max(y1, y2), color);
        } else {
            int dx = x2 - x1;
            int dy = y2 - y1;
            int steps = Math.max(Math.abs(dx), Math.abs(dy));
            for (int i = 0; i <= steps; i++) {
                int xi = x1 + (dx * i) / (steps == 0 ? 1 : steps);
                int yi = y1 + (dy * i) / (steps == 0 ? 1 : steps);
                graphics.fill(xi, yi, xi + 1, yi + 1, color);
            }
        }
    }

    public static void drawInsideRect(GuiGraphics guiGraphics, @NotNull ScreenRectangle rectangle, boolean shouldScissor, Runnable render) {
        if (shouldScissor) {
            guiGraphics.enableScissor(rectangle.left(), rectangle.top(), rectangle.right(), rectangle.bottom());
            render.run();
            guiGraphics.disableScissor();
        } else render.run();
    }

    // ------------------------------------------------------------------------
    // GRADIENTS
    // ------------------------------------------------------------------------

    public static void fillVerticalGradient(GuiGraphics graphics, int x, int y, int width, int height, int topColor, int bottomColor) {
        graphics.fillGradient(x, y, x + width, y + height, topColor, bottomColor);
    }

    public static void fillHorizontalGradient(GuiGraphics graphics, int x, int y, int width, int height, int leftColor, int rightColor) {
        graphics.fillGradient(x, y, x + width, y + height, leftColor, rightColor);
    }

    /**
     * Render a diagonal gradient by interpolating color for each pixel.
     * This is more expensive than a simple vertical/horizontal gradient.
     */
    public static void drawDiagonalGradient(GuiGraphics guiGraphics, int x, int y, int width, int height, int color1, int color2) {
        if (width <= 0 || height <= 0) return;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                float factor = Mth.clamp((i + j) / (float) (width + height), 0.0F, 1.0F);
                int color = FastColor.ARGB32.lerp(factor, color1, color2);
                guiGraphics.fill(x + i, y + j, x + i + 1, y + j + 1, color);
            }
        }
    }

    public static void drawDebugGradient(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        drawDiagonalGradient(guiGraphics, x, y, width, height, Color.RED.getRGB(), Color.GREEN.getRGB());
    }

    // ------------------------------------------------------------------------
    // TEXT
    // ------------------------------------------------------------------------

    public static void drawCenteredString(GuiGraphics graphics, String text, int x, int y, int color) {
        Font font = MC.font;
        graphics.drawString(font, text, x - font.width(text) / 2, y, color, false);
    }

    public static void drawString(GuiGraphics graphics, String text, int x, int y, int color) {
        graphics.drawString(MC.font, text, x, y, color, false);
    }

    public static void drawScrollingString(GuiGraphics guiGraphics, int currentTick, Font font, Component component,
                                           boolean center, net.minecraft.client.gui.navigation.ScreenRectangle bounds, int padding,
                                           int color, boolean shadow) {
        drawScrollingString(guiGraphics, currentTick, font, component.getVisualOrderText(),
                center, bounds, padding, color, shadow);
    }

    public static void drawScrollingString(GuiGraphics guiGraphics, int currentTick, Font font,
                                           FormattedCharSequence sequence, boolean center,
                                           ScreenRectangle bounds, int padding,
                                           int color, boolean shadow) {
        int minX = bounds.left() + padding;
        int maxX = bounds.right() - padding;
        int minY = bounds.top();
        int maxY = bounds.bottom();
        drawScrollingStringInternal(guiGraphics, currentTick, font, sequence, center,
                minX, minY, maxX, maxY, null, color, shadow);
    }

    public static void drawScrollingString(GuiGraphics guiGraphics, int currentTick, Font font, Component component,
                                           boolean center, ScreenRectangle bounds, ScreenRectangle scissor,
                                           int color, boolean shadow) {
        drawScrollingString(guiGraphics, currentTick, font, component.getVisualOrderText(),
                center, bounds, scissor, color, shadow);
    }

    public static void drawScrollingString(GuiGraphics guiGraphics, int currentTick, Font font,
                                           FormattedCharSequence sequence, boolean center,
                                           ScreenRectangle bounds, ScreenRectangle scissor,
                                           int color, boolean shadow) {
        drawScrollingStringInternal(guiGraphics, currentTick, font, sequence, center,
                bounds.left(), bounds.top(), bounds.right(), bounds.bottom(),
                scissor, color, shadow);
    }

    private static void drawScrollingStringInternal(GuiGraphics guiGraphics, int currentTick, Font font,
                                                    FormattedCharSequence sequence, boolean center,
                                                    int minX, int minY, int maxX, int maxY,
                                                    ScreenRectangle scissor, int color, boolean shadow) {
        int textWidth = font.width(sequence);
        int yPos = (minY + maxY - font.lineHeight) / 2;
        int availableWidth = maxX - minX;

        if (textWidth > availableWidth) {
            int overflow = textWidth - availableWidth;
            double d0 = (double) currentTick / 70.0;
            double d1 = Math.max((double) overflow * 0.5, 3.0);
            double d2 = Math.sin(1.57 * Math.cos(6.28 * d0 / d1)) / 2.0 + 0.5;
            double d3 = Mth.lerp(d2, 0.0, overflow);

            int left = scissor != null ? scissor.left() : minX;
            int top = scissor != null ? scissor.top() : minY;
            int right = scissor != null ? scissor.right() : maxX;
            int bottom = scissor != null ? scissor.bottom() : maxY;

            guiGraphics.enableScissor(left, top, right, bottom);
            guiGraphics.drawString(font, sequence, minX - (int) d3, yPos, color, shadow);
            guiGraphics.disableScissor();
        } else {
            int drawX = center ? (minX + maxX - textWidth) / 2 : minX;
            guiGraphics.drawString(font, sequence, drawX, yPos, color, shadow);
        }
    }

    // ------------------------------------------------------------------------
    // TEXTURE: NINE-SLICE RENDERING
    // ------------------------------------------------------------------------

    public static void blitNineSliced(GuiGraphics gfx, ResourceLocation texture,
                                      int x, int y, int width, int height,
                                      int sliceSize,
                                      int textureWidth, int textureHeight) {
        blitNineSliced(gfx,
                NineSliceInfo.TextureInfo.of(texture, textureWidth, textureHeight),
                x, y, width, height,
                NineSliceInfo.SliceBounds.uniform(sliceSize),
                NineSliceInfo.TextureRegion.region(textureWidth, textureHeight));
    }

    public static void blitNineSliced(GuiGraphics gfx, ResourceLocation texture,
                                      int x, int y, int width, int height,
                                      int sliceWidth, int sliceHeight,
                                      int textureWidth, int textureHeight) {
        blitNineSliced(gfx,
                NineSliceInfo.TextureInfo.of(texture, textureWidth, textureHeight),
                x, y, width, height,
                NineSliceInfo.SliceBounds.size(sliceWidth, sliceHeight),
                NineSliceInfo.TextureRegion.region(textureWidth, textureHeight));
    }

    public static void blitNineSliced(GuiGraphics gfx, ResourceLocation texture,
                                      int x, int y, int width, int height,
                                      int sliceLeft, int sliceTop, int sliceRight, int sliceBottom,
                                      int textureWidth, int textureHeight) {
        blitNineSliced(gfx,
                NineSliceInfo.TextureInfo.of(texture, textureWidth, textureHeight),
                x, y, width, height,
                NineSliceInfo.SliceBounds.of(sliceLeft, sliceTop, sliceRight, sliceBottom),
                NineSliceInfo.TextureRegion.region(textureWidth, textureHeight));
    }

    public static void blitNineSliced(GuiGraphics gfx, NineSliceInfo.TextureInfo texture,
                                      int x, int y, int width, int height,
                                      NineSliceInfo.SliceBounds slices,
                                      NineSliceInfo.TextureRegion region) {

        int textureWidth = texture.width();
        int textureHeight = texture.height();

        int uWidth = region.uWidth() > 0 ? region.uWidth() : width;
        int vHeight = region.vHeight() > 0 ? region.vHeight() : height;
        int uOffset = region.uOffset();
        int vOffset = region.vOffset();

        int sliceLeft = Math.min(slices.left(), width / 2);
        int sliceRight = Math.min(slices.right(), width / 2);
        int sliceTop = Math.min(slices.top(), height / 2);
        int sliceDown = Math.min(slices.bottom(), height / 2);

        if (width == uWidth && height == vHeight) {
            gfx.blit(texture.texture(), x, y, uOffset, vOffset, width, height, textureWidth, textureHeight);
            return;
        }

        if (height == vHeight) {
            gfx.blit(texture.texture(), x, y, uOffset, vOffset, sliceLeft, height, textureWidth, textureHeight);
            blitRepeating(gfx, texture.texture(),
                    x + sliceLeft, y, width - sliceLeft - sliceRight, height,
                    uOffset + sliceLeft, vOffset, uWidth - sliceLeft - sliceRight, vHeight,
                    textureWidth, textureHeight);
            gfx.blit(texture.texture(), x + width - sliceRight, y,
                    uOffset + uWidth - sliceRight, vOffset, sliceRight, height, textureWidth, textureHeight);
            return;
        }

        if (width == uWidth) {
            gfx.blit(texture.texture(), x, y, uOffset, vOffset, width, sliceTop, textureWidth, textureHeight);
            blitRepeating(gfx, texture.texture(),
                    x, y + sliceTop, width, height - sliceTop - sliceDown,
                    uOffset, vOffset + sliceTop, uWidth, vHeight - sliceTop - sliceDown,
                    textureWidth, textureHeight);
            gfx.blit(texture.texture(), x, y + height - sliceDown,
                    uOffset, vOffset + vHeight - sliceDown, width, sliceDown, textureWidth, textureHeight);
            return;
        }

        gfx.blit(texture.texture(), x, y, uOffset, vOffset, sliceLeft, sliceTop, textureWidth, textureHeight);
        blitRepeating(gfx, texture.texture(),
                x + sliceLeft, y, width - sliceLeft - sliceRight, sliceTop,
                uOffset + sliceLeft, vOffset, uWidth - sliceLeft - sliceRight, sliceTop,
                textureWidth, textureHeight);
        gfx.blit(texture.texture(), x + width - sliceRight, y,
                uOffset + uWidth - sliceRight, vOffset, sliceRight, sliceTop, textureWidth, textureHeight);

        blitRepeating(gfx, texture.texture(),
                x, y + sliceTop, sliceLeft, height - sliceTop - sliceDown,
                uOffset, vOffset + sliceTop, sliceLeft, vHeight - sliceTop - sliceDown,
                textureWidth, textureHeight);
        blitRepeating(gfx, texture.texture(),
                x + sliceLeft, y + sliceTop, width - sliceLeft - sliceRight, height - sliceTop - sliceDown,
                uOffset + sliceLeft, vOffset + sliceTop, uWidth - sliceLeft - sliceRight, vHeight - sliceTop - sliceDown,
                textureWidth, textureHeight);
        blitRepeating(gfx, texture.texture(),
                x + width - sliceRight, y + sliceTop, sliceRight, height - sliceTop - sliceDown,
                uOffset + uWidth - sliceRight, vOffset + sliceTop, sliceRight, vHeight - sliceTop - sliceDown,
                textureWidth, textureHeight);

        gfx.blit(texture.texture(), x, y + height - sliceDown,
                uOffset, vOffset + vHeight - sliceDown, sliceLeft, sliceDown, textureWidth, textureHeight);
        blitRepeating(gfx, texture.texture(),
                x + sliceLeft, y + height - sliceDown, width - sliceLeft - sliceRight, sliceDown,
                uOffset + sliceLeft, vOffset + vHeight - sliceDown, uWidth - sliceLeft - sliceRight, sliceDown,
                textureWidth, textureHeight);
        gfx.blit(texture.texture(), x + width - sliceRight, y + height - sliceDown,
                uOffset + uWidth - sliceRight, vOffset + vHeight - sliceDown, sliceRight, sliceDown, textureWidth, textureHeight);
    }

    /**
     * Draws a texture section repeatedly to fill a rectangular area.
     */
    public static void blitRepeating(GuiGraphics graphics, ResourceLocation atlas, int x, int y, int width, int height,
                                     int u, int v, int texW, int texH, int fullTexW, int fullTexH) {
        int i = x;
        int j;

        for (IntIterator itW = slices(width, texW); itW.hasNext(); i += j) {
            j = itW.nextInt();
            int k = (texW - j) / 2;
            int l = y;
            int i1;

            for (IntIterator itH = slices(height, texH); itH.hasNext(); l += i1) {
                i1 = itH.nextInt();
                int j1 = (texH - i1) / 2;
                graphics.blit(atlas, i, l, (float) (u + k), (float) (v + j1), j, i1, fullTexW, fullTexH);
            }
        }
    }

    /**
     * Helper for slicing the texture evenly when repeating.
     */
    private static IntIterator slices(int total, int slice) {
        return new IntIterator() {
            private int offset = 0;

            @Override
            public boolean hasNext() {
                return offset < total;
            }

            @Override
            public int nextInt() {
                int size = Math.min(slice, total - offset);
                offset += size;
                return size;
            }
        };
    }

    // ------------------------------------------------------------------------
    // TEXTURE: BORDERS AND ASPECT-FIT RENDERING
    // ------------------------------------------------------------------------

    public static void blitWithBorder(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x, int y, int u, int v,
            int width, int height,
            int textureWidth, int textureHeight,
            int border
    ) {
        blitWithBorder(graphics, texture, x, y, u, v, width, height, textureWidth, textureHeight,
                border, border, border, border);
    }

    public static void blitWithBorder(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x, int y, int u, int v,
            int width, int height,
            int textureWidth, int textureHeight,
            int topBorder, int bottomBorder, int leftBorder, int rightBorder
    ) {
        int fillerWidth = textureWidth - leftBorder - rightBorder;
        int fillerHeight = textureHeight - topBorder - bottomBorder;
        int canvasWidth = width - leftBorder - rightBorder;
        int canvasHeight = height - topBorder - bottomBorder;
        int xPasses = canvasWidth / fillerWidth;
        int remainderWidth = canvasWidth % fillerWidth;
        int yPasses = canvasHeight / fillerHeight;
        int remainderHeight = canvasHeight % fillerHeight;

        // Corners
        graphics.blit(texture, x, y, u, v, leftBorder, topBorder);
        graphics.blit(texture, x + leftBorder + canvasWidth, y, u + leftBorder + fillerWidth, v, rightBorder, topBorder);
        graphics.blit(texture, x, y + topBorder + canvasHeight, u, v + topBorder + fillerHeight, leftBorder, bottomBorder);
        graphics.blit(texture, x + leftBorder + canvasWidth, y + topBorder + canvasHeight, u + leftBorder + fillerWidth, v + topBorder + fillerHeight, rightBorder, bottomBorder);

        // Horizontal and filler
        for (int i = 0; i < xPasses + (remainderWidth > 0 ? 1 : 0); i++) {
            int w = (i == xPasses ? remainderWidth : fillerWidth);
            graphics.blit(texture, x + leftBorder + (i * fillerWidth), y, u + leftBorder, v, w, topBorder);
            graphics.blit(texture, x + leftBorder + (i * fillerWidth), y + topBorder + canvasHeight, u + leftBorder, v + topBorder + fillerHeight, w, bottomBorder);
            for (int j = 0; j < yPasses + (remainderHeight > 0 ? 1 : 0); j++) {
                int h = (j == yPasses ? remainderHeight : fillerHeight);
                graphics.blit(texture, x + leftBorder + (i * fillerWidth), y + topBorder + (j * fillerHeight), u + leftBorder, v + topBorder, w, h);
            }
        }

        // Vertical borders
        for (int j = 0; j < yPasses + (remainderHeight > 0 ? 1 : 0); j++) {
            int h = (j == yPasses ? remainderHeight : fillerHeight);
            graphics.blit(texture, x, y + topBorder + (j * fillerHeight), u, v + topBorder, leftBorder, h);
            graphics.blit(texture, x + leftBorder + canvasWidth, y + topBorder + (j * fillerHeight), u + leftBorder + fillerWidth, v + topBorder, rightBorder, h);
        }
    }

    // ------------------------------------------------------------------------
    // INSCRIBED (ASPECT-FIT)
    // ------------------------------------------------------------------------

    public static void blitInscribed(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x, int y,
            int boundsWidth, int boundsHeight,
            int rectWidth, int rectHeight,
            boolean centerX, boolean centerY
    ) {
        if (rectWidth * boundsHeight > rectHeight * boundsWidth) {
            int h = boundsHeight;
            boundsHeight = (int) (boundsWidth * ((double) rectHeight / rectWidth));
            if (centerY) y += (h - boundsHeight) / 2;
        } else {
            int w = boundsWidth;
            boundsWidth = (int) (boundsHeight * ((double) rectWidth / rectHeight));
            if (centerX) x += (w - boundsWidth) / 2;
        }

        graphics.blit(texture, x, y, boundsWidth, boundsHeight, 0.0f, 0.0f, rectWidth, rectHeight, rectWidth, rectHeight);
    }

    public static void blitInscribed(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x, int y,
            int boundsWidth, int boundsHeight,
            int rectWidth, int rectHeight
    ) {
        blitInscribed(graphics, texture, x, y, boundsWidth, boundsHeight, rectWidth, rectHeight, true, true);
    }
}
