package net.ixdarklord.coolcatlib.api.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

@Environment(EnvType.CLIENT)
public class RenderUtils {
    private static final String RECTANGLE_THROW = "Border must not fill the entire rectangle. [B: %s * 2 = %s || Rect: w:%s, h:%s]";
    public static Rect2i EMPTY_RECT2I = new Rect2i(0, 0, 0, 0);

    public static Rect2i createRect2i(GuiEventListener listener) {
        if (listener == null) return EMPTY_RECT2I;
        return new Rect2i(listener.getRectangle().left(), listener.getRectangle().top(), listener.getRectangle().width(), listener.getRectangle().height());
    }

    public static void renderInRectangle(GuiGraphics guiGraphics, @NotNull ScreenRectangle rectangle, boolean shouldScissor, Runnable render) {
        if (shouldScissor) {
            guiGraphics.enableScissor(rectangle.left(), rectangle.top(), rectangle.right(), rectangle.bottom());
            render.run();
            guiGraphics.disableScissor();
        } else render.run();
    }

    public static void renderHollowRectangleOrThrow(GuiGraphics guiGraphics, ScreenRectangle rectangle, int border, int color) {
        if (!renderHollowRectangle(guiGraphics, rectangle, border, color))
            throw new IllegalArgumentException(RECTANGLE_THROW.formatted(border, 2 * border, rectangle.width(), rectangle.height()));
    }

    public static void renderHollowRectangleOrThrow(GuiGraphics guiGraphics, ScreenRectangle rectangle, int border, boolean outer, int color) {
        if (!renderHollowRectangle(guiGraphics, rectangle, border, outer, color))
            throw new IllegalArgumentException(RECTANGLE_THROW.formatted(border, 2 * border, rectangle.width(), rectangle.height()));
    }

    public static boolean renderHollowRectangle(GuiGraphics guiGraphics, ScreenRectangle rectangle, int border, int color) {
        return renderHollowRectangle(guiGraphics, rectangle, border, false, color);
    }

    public static boolean renderHollowRectangle(GuiGraphics guiGraphics, ScreenRectangle rectangle, int border, boolean outer, int color) throws IllegalArgumentException {
        ScreenRectangle rect = outer ? new ScreenRectangle(
                rectangle.left() - border,
                rectangle.top() - border,
                rectangle.width() + border * 2,
                rectangle.height() + border * 2
        ) : rectangle;

        if (outer || (rect.width() - 2 * border > 0 && rect.height() - 2 * border > 0)) {
            // Draw left border
            guiGraphics.fill(rect.left(), rect.top(), rect.left() + border, rect.bottom(), color);
            // Draw right border
            guiGraphics.fill(rect.right() - border, rect.top(), rect.right(), rect.bottom(), color);
            // Draw top border
            guiGraphics.fill(rect.left() + border, rect.top(), rect.right() - border, rect.top() + border, color);
            // Draw bottom border
            guiGraphics.fill(rect.left() + border, rect.bottom() - border, rect.right() - border, rect.bottom(), color);
            return true;
        } else return false;
    }

    public static void renderScrollingString(GuiGraphics guiGraphics, int currentTick, Font font, Component component, boolean center, int minX, int minY, int maxX, int maxY, int width, int color) {
        renderScrollingString(guiGraphics, currentTick, font, component.getVisualOrderText(), center, minX, minY, maxX, maxY, width, color);
    }

    public static void renderScrollingString(GuiGraphics guiGraphics, int currentTick, Font font, FormattedCharSequence sequence, boolean center, int minX, int minY, int maxX, int maxY, int width, int color) {
        int i = minX + width;
        int j = minX + maxX - width;
        renderScrollingString(guiGraphics, currentTick, font, sequence, center, i, minY, j, minY + maxY, color);
    }

    private static void renderScrollingString(GuiGraphics guiGraphics, int currentTick, Font font, FormattedCharSequence sequence, boolean center, int minX, int minY, int maxX, int maxY, int color) {
        int i = font.width(sequence);
        int j = (minY + maxY - 9) / 2 + 1;
        int k = maxX - minX;
        if (i > k) {
            int l = i - k;
            double d0 = (double) currentTick / 70.0;
            double d1 = Math.max((double)l * 0.5, 3.0);
            double d2 = Math.sin(1.57 * Math.cos(6.28 * d0 / d1)) / 2.0 + 0.5;
            double d3 = Mth.lerp(d2, 0.0, l);
            guiGraphics.enableScissor(minX, minY, maxX, maxY);
            guiGraphics.drawString(font, sequence, minX - (int)d3, j, color);
            guiGraphics.disableScissor();
        } else {
            if (center)
                guiGraphics.drawCenteredString(font, sequence, (minX + maxX) / 2, j, color);
            else
                guiGraphics.drawString(font, sequence, minX, j, color);
        }
    }
}
