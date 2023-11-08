package net.ixdarklord.coolcat_lib.util;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.IntIterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.stream.Collectors;

public class ScreenUtils {
    public static void blitNineSliced(PoseStack poseStack, int x, int y, int width, int height, int sliceWidth, int sliceHeight, int uWidth, int vHeight, int textureX, int textureY) {
        blitNineSliced(poseStack, x, y, width, height, sliceWidth, sliceHeight, sliceWidth, sliceHeight, uWidth, vHeight, textureX, textureY);
    }

    public static void blitNineSliced(PoseStack poseStack, int x, int y, int width, int height, int leftSliceWidth, int topSliceHeight, int rightSliceWidth, int bottomSliceHeight, int uWidth, int vHeight, int textureX, int textureY) {
        leftSliceWidth = Math.min(leftSliceWidth, width / 2);
        rightSliceWidth = Math.min(rightSliceWidth, width / 2);
        topSliceHeight = Math.min(topSliceHeight, height / 2);
        bottomSliceHeight = Math.min(bottomSliceHeight, height / 2);
        if (width == uWidth && height == vHeight) {
            GuiComponent.blit(poseStack, x, y, textureX, textureY, width, height, 256, 256);
        } else if (height == vHeight) {
            GuiComponent.blit(poseStack, x, y, textureX, textureY, leftSliceWidth, height, 256, 256);
            blitRepeating(poseStack, x + leftSliceWidth, y, width - rightSliceWidth - leftSliceWidth, height, textureX + leftSliceWidth, textureY, uWidth - rightSliceWidth - leftSliceWidth, vHeight);
            GuiComponent.blit(poseStack, x + width - rightSliceWidth, y, textureX + uWidth - rightSliceWidth, textureY, rightSliceWidth, height, 256, 256);
        } else if (width == uWidth) {
            GuiComponent.blit(poseStack, x, y, textureX, textureY, width, topSliceHeight, 256, 256);
            blitRepeating(poseStack, x, y + topSliceHeight, width, height - bottomSliceHeight - topSliceHeight, textureX, textureY + topSliceHeight, uWidth, vHeight - bottomSliceHeight - topSliceHeight);
            GuiComponent.blit(poseStack, x, y + height - bottomSliceHeight, textureX, textureY + vHeight - bottomSliceHeight, width, bottomSliceHeight, 256, 256);
        } else {
            GuiComponent.blit(poseStack, x, y, textureX, textureY, leftSliceWidth, topSliceHeight, 256, 256);
            blitRepeating(poseStack, x + leftSliceWidth, y, width - rightSliceWidth - leftSliceWidth, topSliceHeight, textureX + leftSliceWidth, textureY, uWidth - rightSliceWidth - leftSliceWidth, topSliceHeight);
            GuiComponent.blit(poseStack, x + width - rightSliceWidth, y, textureX + uWidth - rightSliceWidth, textureY, rightSliceWidth, topSliceHeight, 256, 256);
            GuiComponent.blit(poseStack, x, y + height - bottomSliceHeight, textureX, textureY + vHeight - bottomSliceHeight, leftSliceWidth, bottomSliceHeight, 256, 256);
            blitRepeating(poseStack, x + leftSliceWidth, y + height - bottomSliceHeight, width - rightSliceWidth - leftSliceWidth, bottomSliceHeight, textureX + leftSliceWidth, textureY + vHeight - bottomSliceHeight, uWidth - rightSliceWidth - leftSliceWidth, bottomSliceHeight);
            GuiComponent.blit(poseStack, x + width - rightSliceWidth, y + height - bottomSliceHeight, textureX + uWidth - rightSliceWidth, textureY + vHeight - bottomSliceHeight, rightSliceWidth, bottomSliceHeight, 256, 256);
            blitRepeating(poseStack, x, y + topSliceHeight, leftSliceWidth, height - bottomSliceHeight - topSliceHeight, textureX, textureY + topSliceHeight, leftSliceWidth, vHeight - bottomSliceHeight - topSliceHeight);
            blitRepeating(poseStack, x + leftSliceWidth, y + topSliceHeight, width - rightSliceWidth - leftSliceWidth, height - bottomSliceHeight - topSliceHeight, textureX + leftSliceWidth, textureY + topSliceHeight, uWidth - rightSliceWidth - leftSliceWidth, vHeight - bottomSliceHeight - topSliceHeight);
            blitRepeating(poseStack, x + width - rightSliceWidth, y + topSliceHeight, leftSliceWidth, height - bottomSliceHeight - topSliceHeight, textureX + uWidth - rightSliceWidth, textureY + topSliceHeight, rightSliceWidth, vHeight - bottomSliceHeight - topSliceHeight);
        }
    }

    public static void blitRepeating(PoseStack poseStack, int x, int y, int width, int height, int uOffset, int vOffset, int sourceWidth, int sourceHeight) {
        blitRepeating(poseStack, x, y, width, height, uOffset, vOffset, sourceWidth, sourceHeight, 256, 256);
    }

    public static void blitRepeating(PoseStack poseStack, int m, int n, int o, int p, int q, int r, int s, int t, int textureWidth, int textureHeight) {
        int i = m;

        int j;
        for(IntIterator intiterator = slices(o, s); intiterator.hasNext(); i += j) {
            j = intiterator.nextInt();
            int k = (s - j) / 2;
            int l = n;

            int i1;
            for(IntIterator intIterator1 = slices(p, t); intIterator1.hasNext(); l += i1) {
                i1 = intIterator1.nextInt();
                int j1 = (t - i1) / 2;
                GuiComponent.blit(poseStack, i, l, (float)(q + k), (float)(r + j1), j, i1, textureWidth, textureHeight);
            }
        }

    }

    private static IntIterator slices(int target, int total) {
        int i = Mth.positiveCeilDiv(target, total == 0 ? 1 : total);
        return new Divisor(target, i);
    }

    public static void drawScrollingString(PoseStack poseStack, int currentTick, Font font, Component component, int minX, int minY, int maxX, int maxY, int width, int color) {
        drawScrollingString(poseStack, currentTick, font, component.getVisualOrderText(), minX, minY, maxX, maxY, width, color);
    }

    public static void drawScrollingString(PoseStack poseStack, int currentTick, Font font, FormattedCharSequence sequence, int minX, int minY, int maxX, int maxY, int width, int color) {
        int i = minX + width;
        int j = minX + maxX - width;
        renderScrollingString(poseStack, currentTick, font, sequence, i, minY, j, minY + maxY, color);
    }

    private static void renderScrollingString(PoseStack poseStack, int currentTick, Font font, FormattedCharSequence sequence, int minX, int minY, int maxX, int maxY, int color) {
        int i = font.width(sequence);
        int j = (minY + maxY - 9) / 2 + 1;
        int k = maxX - minX;
        if (i > k) {
            int l = i - k;
            double d0 = (double) currentTick / 70.0;
            double d1 = Math.max((double)l * 0.5, 3.0);
            double d2 = Math.sin(1.57 * Math.cos(6.28 * d0 / d1)) / 2.0 + 0.5;
            double d3 = Mth.lerp(d2, 0.0, l);
            GuiComponent.enableScissor(minX, minY, maxX, maxY);
            GuiComponent.drawString(poseStack, font, sequence, minX - (int)d3, j, color);
            GuiComponent.disableScissor();
        } else {
            GuiComponent.drawCenteredString(poseStack, font, sequence, (minX + maxX) / 2, j, color);
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
