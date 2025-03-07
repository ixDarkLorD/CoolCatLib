package net.ixdarklord.coolcatlib.api.client.gui.components;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.ixdarklord.coolcatlib.api.util.ColorUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class TextScreen extends GuiGraphics {
    private final List<ScreenComponent> componentList;
    private final Font font;
    private final int posX;
    private final int posY;
    private int width;
    private int height;
    private final boolean drawShadow;
    private int widthOld = -1;
    private int heightOld = -1;
    private int screenIndex;

    private TextScreen(boolean drawShadow, int posX, int posY, int width, int height, List<ScreenComponent> componentList) {
        super(Minecraft.getInstance(), MultiBufferSource.immediate(new ByteBufferBuilder(256)));
        this.posX = posX;
        this.posY = posY;
        this.width = width;
        this.height = height;
        this.componentList = componentList;
        this.drawShadow = drawShadow;
        this.font = Minecraft.getInstance().font;
        this.screenIndex = -1;
    }

    public static TextScreen build(int posX, int posY, int width, int height, boolean drawShadow, int amountOfScreens) {
        List<ScreenComponent> components = Lists.newArrayList();
        for (int i = 0; i < amountOfScreens; i++)
            components.add(new ScreenComponent(Lists.newArrayList(), false, i == 0));

        return new TextScreen(drawShadow, posX, posY, width, height, Collections.unmodifiableList(components)).selectScreen(0);
    }

    public TextScreen selectScreen(int index) {
        if (index >= componentList.size() || index < 0)
            throw new IllegalArgumentException("Theres is no such a screen with index: " + index);
        this.screenIndex = index;
        return this;
    }

    public TextScreen selectLastScreen(boolean shouldBeRendered) {
        this.screenIndex = Math.max(0, this.componentList.stream()
                .filter(component -> !shouldBeRendered || component.render)
                .toList()
                .size()-1);
        return this;
    }

    public TextScreen shouldRender(boolean value) {
        if (screenIndex == -1) throw new IllegalArgumentException("There is no selected box.");
        this.componentList.get(screenIndex).render = value;
        return this;
    }

    public TextScreen alignToCenter(boolean value) {
        if (screenIndex == -1) throw new IllegalArgumentException("There is no selected box.");
        this.componentList.get(screenIndex).centered = value;
        return this;
    }

    public TextScreen backgroundColor(Color colorRGBA) {
        if (screenIndex == -1) throw new IllegalArgumentException("There is no selected box.");
        this.componentList.get(screenIndex).backgroundColor = colorRGBA;
        return this;
    }

    public void renderAllBoxes(GuiGraphics guiGraphics, int backgroundColor, Color shaderColor) {
        if (componentList.isEmpty())
            throw new IllegalArgumentException("There is no screens created.");

        for (int i = 0; i < componentList.size(); i++) {
            ScreenComponent screen = componentList.get(i);
            if (!screen.render) continue;
            this.renderScreen(i, guiGraphics);

            int renderEnabledScreens = this.componentList.stream().filter(box -> box.render).toList().size();
            if (renderEnabledScreens > 1 && i < this.componentList.size()-1) {
                Color bgColor = this.componentList.get(i+1).backgroundColor;
                int color = bgColor != null ? ColorUtils.RGBToRGBA(bgColor.getRGB(), bgColor.getAlpha() / 255F) : ColorUtils.RGBToRGBA(backgroundColor, 0.85F);
                int width = this.widthOld > 1 ? this.widthOld : this.width;
                int height = this.heightOld > 1 ? this.heightOld : this.height;

                RenderSystem.setShaderColor(shaderColor.getRed() / 255F, shaderColor.getGreen() / 255F, shaderColor.getBlue() / 255F, shaderColor.getAlpha() / 255F);
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(0.0F, 0.0F, 1.0F * (i+1));
                this.fill(RenderType.guiOverlay(),posX-1, posY-1, posX + width, posY + height, color);
                guiGraphics.pose().popPose();
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }

    public void renderScreen(GuiGraphics guiGraphics) {
        renderScreen(this.screenIndex, guiGraphics);
    }

    public void renderScreen(int screenIndex, GuiGraphics guiGraphics) {
        if (screenIndex == -1) throw new IllegalArgumentException("Invalid Selected Screen! Index: " + screenIndex);
        ScreenComponent screen = componentList.get(screenIndex);
        if (!screen.render) return;

        List<FormattedCharSequence> selected = screen.sequences;
        int scrollOffset = screen.scrollOffset;
        int length = this.getScreenLinesLength(screenIndex);
        screen.scrollOffset = Mth.clamp(screen.scrollOffset, 0, selected.size() - length);

        for(int m = 0; m < length; m++) {
            FormattedCharSequence sequences = selected.get(Math.min(m + scrollOffset, (selected.size()-1)));
            float posX = (float)(this.posX);
            float posY = (float)(this.posY);
            int color = Objects.requireNonNull(TextColor.fromLegacyFormat(ChatFormatting.WHITE)).getValue();

            guiGraphics.pose().pushPose();
            if (screen.centered) {
                posX += ((float) this.width / 2);
                posY += ((float) this.height / 2);
                float k = this.font.width(sequences) / 2.0F;
                float j = (selected.size() * 7.35F) / 2.0F;
                this.drawString(this.font, sequences, (int) (posX - k), (int) (posY - j + (this.font.lineHeight * m)), color, this.drawShadow);
            } else {
                this.drawString(this.font, sequences, (int) posX, (int) (posY + m * this.font.lineHeight), color, this.drawShadow);
            }
            guiGraphics.pose().popPose();
        }
    }

    public void scrollTo(int pos, boolean replace) {
        scrollTo(this.screenIndex, pos, replace);
    }

    public void scrollTo(int screenIndex, int pos, boolean replace) {
        if (screenIndex == -1) throw new IllegalArgumentException("Invalid Selected Screen! Index: " + screenIndex);
        List<FormattedCharSequence> selected = this.componentList.get(screenIndex).sequences;
        this.componentList.get(screenIndex).scrollOffset = replace ? pos : this.componentList.get(screenIndex).scrollOffset+pos;
        this.componentList.get(screenIndex).scrollOffset = Mth.clamp(this.componentList.get(screenIndex).scrollOffset, 0, selected.size() - getScreenLinesLength());
    }

    public Style getComponentStyleAt(double mouseX, double mouseY) {
        return getComponentStyleAt(this.screenIndex, mouseX, mouseY);
    }

    public Style getComponentStyleAt(int screenIndex, double mouseX, double mouseY) {
        if (screenIndex == -1) throw new IllegalArgumentException("Invalid Selected Screen! Index: " + screenIndex);
        List<FormattedCharSequence> selected = this.componentList.get(screenIndex).sequences;
        int offset = this.componentList.get(screenIndex).scrollOffset;

        if (selected.isEmpty()) return null;
        int i = Mth.floor(mouseX - this.posX);
        int j = Mth.floor(mouseY - this.posY);
        if (i < 0 || j < 0) return null;
        if (i > this.width || j > this.height) return null;

        int linePos;
        if (this.componentList.get(screenIndex).centered) {
            int textBoxHeight = this.height;
            int lineHeight = this.font.lineHeight;
            int totalLines = selected.size();
            int centerOffset = (textBoxHeight - (totalLines * lineHeight)) / 2;

            j -= 2;
            if (j < centerOffset || j > (centerOffset + totalLines * lineHeight)) {
                return null;
            }
            linePos = (j - centerOffset) / lineHeight;

        } else {
            if (j >= this.font.lineHeight * Math.min(this.height / this.font.lineHeight, selected.size())) return null;
            linePos = j / this.font.lineHeight;
        }
        if (linePos >= selected.size()) return null;
        FormattedCharSequence sequence = selected.get(Math.min(linePos + offset, (selected.size()-1)));

        if (this.componentList.get(screenIndex).centered) {
            int textBoxWidth = this.width / 2;
            int lineWidth = this.font.width(sequence) / 2;
            i -= textBoxWidth - lineWidth;
        }
        return this.font.getSplitter().componentStyleAtWidth(sequence, i);
    }

    public boolean canScroll() {
        return canScroll(this.screenIndex);
    }

    public boolean canScroll(int screenIndex) {
        if (screenIndex == -1) throw new IllegalArgumentException("Invalid Selected Screen! Index: " + screenIndex);
        return this.height / this.font.lineHeight < this.componentList.get(screenIndex).sequences.size();
    }

    public boolean isEmpty() {
        if (screenIndex == -1) throw new IllegalArgumentException("Invalid Selected Screen! Index: " + screenIndex);
        return this.componentList.get(screenIndex).sequences.isEmpty();
    }

    public TextScreen create(String text, ChatFormatting... formats) {
        return create(Component.literal(text), formats);
    }

    public TextScreen create(Component text, ChatFormatting... formats) {
        return create(text, null, formats);
    }
    
    public TextScreen create(Component title, Component description, ChatFormatting... formats) {
        if (screenIndex == -1) throw new IllegalArgumentException("Invalid Selected Screen! Index: " + screenIndex);
        List<FormattedCharSequence> result = Lists.newArrayList();
        
        result.addAll(Language.getInstance().getVisualOrder(this.font.getSplitter().splitLines(title, this.width, Style.EMPTY.applyFormats(formats))));
        if (description != null)
            result.addAll(Language.getInstance().getVisualOrder(this.font.getSplitter().splitLines(description, this.width, Style.EMPTY.applyFormats(formats))));

        if (!this.componentList.get(screenIndex).sequences.isEmpty()) {
            return add(FormattedCharSequence.EMPTY).addAll(result);
        }
        return addAll(result);
    }

    public TextScreen add(FormattedCharSequence formattedCharSequence) {
        if (screenIndex == -1) throw new IllegalArgumentException("Invalid Selected Screen! Index: " + screenIndex);
        this.componentList.get(screenIndex).sequences.add(formattedCharSequence);
        return this;
    }

    public TextScreen addAll(List<FormattedCharSequence> formattedCharSequences) {
        if (screenIndex == -1) throw new IllegalArgumentException("Invalid Selected Screen! Index: " + screenIndex);
        this.componentList.get(screenIndex).sequences.addAll(formattedCharSequences);
        return this;
    }

    public void clear() {
        this.componentList.forEach(component -> {
            component.sequences.clear();
            component.backgroundColor = null;
        });
    }

    public boolean canRender() {
        return this.componentList.get(screenIndex).render;
    }

    public int getScreenLinesLength() {
        return getScreenLinesLength(this.screenIndex);
    }

    public int getScreenLinesLength(int screenIndex) {
        if (screenIndex == -1) throw new IllegalArgumentException("Invalid Selected Screen! Index: " + screenIndex);
        return Math.min(this.height / this.font.lineHeight, this.componentList.get(screenIndex).sequences.size());
    }

    public int getRemainingLines() {
        return getRemainingLines(this.screenIndex);
    }

    public int getRemainingLines(int screenIndex) {
        if (screenIndex == -1) throw new IllegalArgumentException("Invalid Selected Screen! Index: " + screenIndex);
        return this.componentList.get(screenIndex).sequences.size() - getScreenLinesLength();
    }

    public int getScrollOffset() {
        return getScrollOffset(this.screenIndex);
    }

    public int getScrollOffset(int screenIndex) {
        if (screenIndex == -1) throw new IllegalArgumentException("Invalid Selected Screen! Index: " + screenIndex);
        return this.componentList.get(screenIndex).scrollOffset;
    }

    public List<ScreenComponent> getComponentList() {
        return componentList;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void setWidth(int size, boolean cacheFirstValue) {
        if (cacheFirstValue && this.widthOld == -1) {
            this.widthOld = this.width;
        }
        this.width = size;
    }

    public void setHeight(int size, boolean cacheFirstValue) {
        if (cacheFirstValue && this.heightOld == -1) {
            this.heightOld = this.height;
        }
        this.height = size;
    }

    public static class ScreenComponent {
        private final List<FormattedCharSequence> sequences;
        private boolean centered;
        private boolean render;
        private Color backgroundColor;
        private int scrollOffset;

        private ScreenComponent(List<FormattedCharSequence> sequences, boolean centered, boolean render) {
            this.sequences = sequences;
            this.centered = centered;
            this.render = render;
        }
    }
}
