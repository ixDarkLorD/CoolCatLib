package net.ixdarklord.coolcatlib.api.client.gui.components;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.ixdarklord.coolcatlib.api.utils.ColorUtils;
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
        super(Minecraft.getInstance(), MultiBufferSource.immediate(new BufferBuilder(256)));
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

        for(int i = 0; i < amountOfScreens; ++i) {
            components.add(new ScreenComponent(Lists.newArrayList(), false, i == 0));
        }

        return (new TextScreen(drawShadow, posX, posY, width, height, Collections.unmodifiableList(components))).selectScreen(0);
    }

    public TextScreen selectScreen(int index) {
        if (index < this.componentList.size() && index >= 0) {
            this.screenIndex = index;
            return this;
        } else {
            throw new IllegalArgumentException("Theres is no such a screen with index: " + index);
        }
    }

    public TextScreen selectLastScreen(boolean shouldBeRendered) {
        this.screenIndex = Math.max(0, this.componentList.stream().filter((component) -> !shouldBeRendered || component.render).toList().size() - 1);
        return this;
    }

    public TextScreen shouldRender(boolean value) {
        if (this.screenIndex == -1) {
            throw new IllegalArgumentException("There is no selected box.");
        } else {
            ((ScreenComponent)this.componentList.get(this.screenIndex)).render = value;
            return this;
        }
    }

    public TextScreen alignToCenter(boolean value) {
        if (this.screenIndex == -1) {
            throw new IllegalArgumentException("There is no selected box.");
        } else {
            ((ScreenComponent)this.componentList.get(this.screenIndex)).centered = value;
            return this;
        }
    }

    public TextScreen backgroundColor(Color colorRGBA) {
        if (this.screenIndex == -1) {
            throw new IllegalArgumentException("There is no selected box.");
        } else {
            ((ScreenComponent)this.componentList.get(this.screenIndex)).backgroundColor = colorRGBA;
            return this;
        }
    }

    public void renderAllBoxes(GuiGraphics guiGraphics, int backgroundColor, Color shaderColor) {
        if (this.componentList.isEmpty()) {
            throw new IllegalArgumentException("There is no screens created.");
        } else {
            for(int i = 0; i < this.componentList.size(); ++i) {
                ScreenComponent screen = (ScreenComponent)this.componentList.get(i);
                if (screen.render) {
                    this.renderScreen(i, guiGraphics);
                    int renderEnabledScreens = this.componentList.stream().filter((box) -> box.render).toList().size();
                    if (renderEnabledScreens > 1 && i < this.componentList.size() - 1) {
                        Color bgColor = this.componentList.get(i + 1).backgroundColor;
                        int color = bgColor != null ? ColorUtils.rgbToRgba(bgColor, (float)bgColor.getAlpha() / 255.0F) : ColorUtils.rgbToRgba(backgroundColor, 0.85F);
                        int width = this.widthOld > 1 ? this.widthOld : this.width;
                        int height = this.heightOld > 1 ? this.heightOld : this.height;
                        RenderSystem.setShaderColor((float)shaderColor.getRed() / 255.0F, (float)shaderColor.getGreen() / 255.0F, (float)shaderColor.getBlue() / 255.0F, (float)shaderColor.getAlpha() / 255.0F);
                        guiGraphics.pose().pushPose();
                        guiGraphics.pose().translate(0.0F, 0.0F, (float) (i + 1));
                        this.fill(RenderType.guiOverlay(), this.posX - 1, this.posY - 1, this.posX + width, this.posY + height, color);
                        guiGraphics.pose().popPose();
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                    }
                }
            }

        }
    }

    public void renderScreen(GuiGraphics guiGraphics) {
        this.renderScreen(this.screenIndex, guiGraphics);
    }

    public void renderScreen(int screenIndex, GuiGraphics guiGraphics) {
        if (screenIndex == -1) {
            throw new IllegalArgumentException("Invalid Selected Screen! Index: " + screenIndex);
        } else {
            ScreenComponent screen = (ScreenComponent)this.componentList.get(screenIndex);
            if (screen.render) {
                List<FormattedCharSequence> selected = screen.sequences;
                int scrollOffset = screen.scrollOffset;
                int length = this.getScreenLinesLength(screenIndex);
                screen.scrollOffset = Mth.clamp(screen.scrollOffset, 0, selected.size() - length);

                for(int m = 0; m < length; ++m) {
                    FormattedCharSequence sequences = (FormattedCharSequence)selected.get(Math.min(m + scrollOffset, selected.size() - 1));
                    float posX = (float)this.posX;
                    float posY = (float)this.posY;
                    int color = ((TextColor)Objects.requireNonNull(TextColor.fromLegacyFormat(ChatFormatting.WHITE))).getValue();
                    guiGraphics.pose().pushPose();
                    if (screen.centered) {
                        posX += (float)this.width / 2.0F;
                        posY += (float)this.height / 2.0F;
                        float k = (float)this.font.width(sequences) / 2.0F;
                        float j = (float)selected.size() * 7.35F / 2.0F;
                        int var10003 = (int)(posX - k);
                        float var10004 = posY - j;
                        Objects.requireNonNull(this.font);
                        this.drawString(this.font, sequences, var10003, (int)(var10004 + (float)(9 * m)), color, this.drawShadow);
                    } else {
                        int var17 = (int)posX;
                        Objects.requireNonNull(this.font);
                        this.drawString(this.font, sequences, var17, (int)(posY + (float)(m * 9)), color, this.drawShadow);
                    }

                    guiGraphics.pose().popPose();
                }

            }
        }
    }

    public void scrollTo(int pos, boolean replace) {
        this.scrollTo(this.screenIndex, pos, replace);
    }

    public void scrollTo(int screenIndex, int pos, boolean replace) {
        if (screenIndex == -1) {
            throw new IllegalArgumentException("Invalid Selected Screen! Index: " + screenIndex);
        } else {
            List<FormattedCharSequence> selected = ((ScreenComponent)this.componentList.get(screenIndex)).sequences;
            ((ScreenComponent)this.componentList.get(screenIndex)).scrollOffset = replace ? pos : ((ScreenComponent)this.componentList.get(screenIndex)).scrollOffset + pos;
            ((ScreenComponent)this.componentList.get(screenIndex)).scrollOffset = Mth.clamp(((ScreenComponent)this.componentList.get(screenIndex)).scrollOffset, 0, selected.size() - this.getScreenLinesLength());
        }
    }

    public Style getComponentStyleAt(double mouseX, double mouseY) {
        return this.getComponentStyleAt(this.screenIndex, mouseX, mouseY);
    }

    public Style getComponentStyleAt(int screenIndex, double mouseX, double mouseY) {
        if (screenIndex == -1) {
            throw new IllegalArgumentException("Invalid Selected Screen! Index: " + screenIndex);
        } else {
            List<FormattedCharSequence> selected = ((ScreenComponent)this.componentList.get(screenIndex)).sequences;
            int offset = ((ScreenComponent)this.componentList.get(screenIndex)).scrollOffset;
            if (selected.isEmpty()) {
                return null;
            } else {
                int i = Mth.floor(mouseX - (double)this.posX);
                int j = Mth.floor(mouseY - (double)this.posY);
                if (i >= 0 && j >= 0) {
                    if (i <= this.width && j <= this.height) {
                        int linePos;
                        if (((ScreenComponent)this.componentList.get(screenIndex)).centered) {
                            int textBoxHeight = this.height;
                            Objects.requireNonNull(this.font);
                            int lineHeight = 9;
                            int totalLines = selected.size();
                            int centerOffset = (textBoxHeight - totalLines * lineHeight) / 2;
                            j -= 2;
                            if (j < centerOffset || j > centerOffset + totalLines * lineHeight) {
                                return null;
                            }

                            linePos = (j - centerOffset) / lineHeight;
                        } else {
                            Objects.requireNonNull(this.font);
                            int var10002 = this.height;
                            Objects.requireNonNull(this.font);
                            if (j >= 9 * Math.min(var10002 / 9, selected.size())) {
                                return null;
                            }

                            Objects.requireNonNull(this.font);
                            linePos = j / 9;
                        }

                        if (linePos >= selected.size()) {
                            return null;
                        } else {
                            FormattedCharSequence sequence = (FormattedCharSequence)selected.get(Math.min(linePos + offset, selected.size() - 1));
                            if (((ScreenComponent)this.componentList.get(screenIndex)).centered) {
                                int textBoxWidth = this.width / 2;
                                int lineWidth = this.font.width(sequence) / 2;
                                i -= textBoxWidth - lineWidth;
                            }

                            return this.font.getSplitter().componentStyleAtWidth(sequence, i);
                        }
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            }
        }
    }

    public boolean canScroll() {
        return this.canScroll(this.screenIndex);
    }

    public boolean canScroll(int screenIndex) {
        if (screenIndex == -1) {
            throw new IllegalArgumentException("Invalid Selected Screen! Index: " + screenIndex);
        } else {
            int var10000 = this.height;
            Objects.requireNonNull(this.font);
            return var10000 / 9 < ((ScreenComponent)this.componentList.get(screenIndex)).sequences.size();
        }
    }

    public boolean isEmpty() {
        if (this.screenIndex == -1) {
            throw new IllegalArgumentException("Invalid Selected Screen! Index: " + this.screenIndex);
        } else {
            return ((ScreenComponent)this.componentList.get(this.screenIndex)).sequences.isEmpty();
        }
    }

    public TextScreen create(String text, ChatFormatting... formats) {
        return this.create((Component)Component.literal(text), formats);
    }

    public TextScreen create(Component text, ChatFormatting... formats) {
        return this.create(text, (Component)null, formats);
    }

    public TextScreen create(Component title, Component description, ChatFormatting... formats) {
        if (this.screenIndex == -1) {
            throw new IllegalArgumentException("Invalid Selected Screen! Index: " + this.screenIndex);
        } else {
            List<FormattedCharSequence> result = Lists.newArrayList();
            result.addAll(Language.getInstance().getVisualOrder(this.font.getSplitter().splitLines(title, this.width, Style.EMPTY.applyFormats(formats))));
            if (description != null) {
                result.addAll(Language.getInstance().getVisualOrder(this.font.getSplitter().splitLines(description, this.width, Style.EMPTY.applyFormats(formats))));
            }

            return !((ScreenComponent)this.componentList.get(this.screenIndex)).sequences.isEmpty() ? this.add(FormattedCharSequence.EMPTY).addAll(result) : this.addAll(result);
        }
    }

    public TextScreen add(FormattedCharSequence formattedCharSequence) {
        if (this.screenIndex == -1) {
            throw new IllegalArgumentException("Invalid Selected Screen! Index: " + this.screenIndex);
        } else {
            ((ScreenComponent)this.componentList.get(this.screenIndex)).sequences.add(formattedCharSequence);
            return this;
        }
    }

    public TextScreen addAll(List<FormattedCharSequence> formattedCharSequences) {
        if (this.screenIndex == -1) {
            throw new IllegalArgumentException("Invalid Selected Screen! Index: " + this.screenIndex);
        } else {
            ((ScreenComponent)this.componentList.get(this.screenIndex)).sequences.addAll(formattedCharSequences);
            return this;
        }
    }

    public void clear() {
        this.componentList.forEach((component) -> {
            component.sequences.clear();
            component.backgroundColor = null;
        });
    }

    public boolean canRender() {
        return ((ScreenComponent)this.componentList.get(this.screenIndex)).render;
    }

    public int getScreenLinesLength() {
        return this.getScreenLinesLength(this.screenIndex);
    }

    public int getScreenLinesLength(int screenIndex) {
        if (screenIndex == -1) {
            throw new IllegalArgumentException("Invalid Selected Screen! Index: " + screenIndex);
        } else {
            int var10000 = this.height;
            Objects.requireNonNull(this.font);
            return Math.min(var10000 / 9, ((ScreenComponent)this.componentList.get(screenIndex)).sequences.size());
        }
    }

    public int getRemainingLines() {
        return this.getRemainingLines(this.screenIndex);
    }

    public int getRemainingLines(int screenIndex) {
        if (screenIndex == -1) {
            throw new IllegalArgumentException("Invalid Selected Screen! Index: " + screenIndex);
        } else {
            return ((ScreenComponent)this.componentList.get(screenIndex)).sequences.size() - this.getScreenLinesLength();
        }
    }

    public int getScrollOffset() {
        return this.getScrollOffset(this.screenIndex);
    }

    public int getScrollOffset(int screenIndex) {
        if (screenIndex == -1) {
            throw new IllegalArgumentException("Invalid Selected Screen! Index: " + screenIndex);
        } else {
            return ((ScreenComponent)this.componentList.get(screenIndex)).scrollOffset;
        }
    }

    public List<ScreenComponent> getComponentList() {
        return this.componentList;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
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
