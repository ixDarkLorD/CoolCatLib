package net.ixdarklord.coolcat_lib.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.stream.Collectors;

public class ScreenUtils {
    public static void drawScrollingString(GuiGraphics guiGraphics, int currentTick, Font font, Component component, int minX, int minY, int maxX, int maxY, int width, int color) {
        drawScrollingString(guiGraphics, currentTick, font, component.getVisualOrderText(), minX, minY, maxX, maxY, width, color);
    }

    public static void drawScrollingString(GuiGraphics guiGraphics, int currentTick, Font font, FormattedCharSequence sequence, int minX, int minY, int maxX, int maxY, int width, int color) {
        int i = minX + width;
        int j = minX + maxX - width;
        renderScrollingString(guiGraphics, currentTick, font, sequence, i, minY, j, minY + maxY, color);
    }

    private static void renderScrollingString(GuiGraphics guiGraphics, int currentTick, Font font, FormattedCharSequence sequence, int minX, int minY, int maxX, int maxY, int color) {
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
            guiGraphics.drawCenteredString(font, sequence, (minX + maxX) / 2, j, color);
        }
    }

    public static Component limitComponent(Component component, int maxWidth) {
        String text = component.getString(maxWidth);
        return component.getString().length() >= maxWidth
                ? Component.literal(text.substring(0, text.length() - 3) + "...").withStyle(component.getStyle())
                : component;
    }

    public static List<Component> splitComponent(Component component, int maxWidth) {
        Font font = Minecraft.getInstance().font;
        return font.getSplitter().splitLines(component, maxWidth, component.getStyle()).stream()
                .map(text -> Component.literal(text.getString()).withStyle(component.getStyle()))
                .collect(Collectors.toList());
    }
}
