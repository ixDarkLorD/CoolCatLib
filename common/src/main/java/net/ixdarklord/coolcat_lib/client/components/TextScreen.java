package net.ixdarklord.coolcat_lib.client.components;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.coolcat_lib.util.ColorUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TextScreen extends GuiComponent {
    private final List<Interface> interfacesList = new ArrayList<>();
    private final Font font;
    private final int posX;
    private final int posY;
    private int width;
    private int height;
    private final boolean drawShadow;
    private int widthOld = -1;
    private int heightOld = -1;
    private int boxIndex;

    public TextScreen(int posX, int posY, int width, int height, boolean drawShadow) {
        this.posX = posX;
        this.posY = posY;
        this.width = width;
        this.height = height;
        this.drawShadow = drawShadow;
        this.font = Minecraft.getInstance().font;
        this.boxIndex = -1;
    }

    public TextScreen build(int count) {
        for (int i = 0; i < count; i++) {
            this.interfacesList.add(new Interface(new ArrayList<>(), false, this.interfacesList.isEmpty()));
            boxIndex = 0;
        }
        return this;
    }
    public TextScreen selectBox(int boxIndex) {
        if (boxIndex >= interfacesList.size() || boxIndex < 0)
            throw new IndexOutOfBoundsException("Theres is no such a box with index: " + boxIndex);
        this.boxIndex = boxIndex;
        return this;
    }
    public TextScreen shouldRender(boolean state) {
        if (boxIndex == -1) throw new IndexOutOfBoundsException("There is no selected box.");
        this.interfacesList.get(boxIndex).render = state;
        return this;
    }
    public TextScreen alignPosToCenter(boolean state) {
        if (boxIndex == -1) throw new IndexOutOfBoundsException("There is no selected box.");
        this.interfacesList.get(boxIndex).centered = state;
        return this;
    }
    public TextScreen backgroundColor(Color colorRGBA) {
        if (boxIndex == -1) throw new IndexOutOfBoundsException("There is no selected box.");
        this.interfacesList.get(boxIndex).backgroundColor = colorRGBA;
        return this;
    }

    @SuppressWarnings("unused")
    public void renderBox(PoseStack poseStack) {
        if (boxIndex == -1) throw new IndexOutOfBoundsException("There is no selected box.");
        if (!interfacesList.get(boxIndex).render) return;
        List<FormattedCharSequence> selected = interfacesList.get(boxIndex).sequences;
        int offset = interfacesList.get(boxIndex).scrollOffset;
        int length = getBoxLinesLength();
        this.interfacesList.get(boxIndex).scrollOffset = Mth.clamp(this.interfacesList.get(boxIndex).scrollOffset, 0, selected.size() - length);

        for(int m = 0; m < length; m++) {
            FormattedCharSequence sequences = selected.get(Math.min(m + offset, (selected.size()-1)));
            float posX = (float)(this.posX);
            float posY = (float)(this.posY);
            int color = Objects.requireNonNull(TextColor.fromLegacyFormat(ChatFormatting.WHITE)).getValue();
            if (this.interfacesList.get(boxIndex).centered) {
                posX += ((float) this.width / 2);
                posY += ((float) this.height / 2);
                float k = this.font.width(sequences) / 2.0F;
                float j = (selected.size() * 7.35F) / 2.0F;
                this.font.drawInternal(sequences, posX - k, posY - j + (this.font.lineHeight * m), color, poseStack.last().pose(), this.drawShadow);
            } else {
                this.font.drawInternal(sequences, posX, posY + m * this.font.lineHeight, color, poseStack.last().pose(), this.drawShadow);
            }
        }
    }
    public void renderAllBoxes(PoseStack poseStack, int backgroundColor, Color shaderColor) {
        if (interfacesList.isEmpty()) throw new IndexOutOfBoundsException("There is no interface created.");
        for (int i = 0; i < interfacesList.size(); i++) {
            if (!interfacesList.get(i).render) continue;
            this.selectBox(i);
            List<FormattedCharSequence> selected = interfacesList.get(i).sequences;
            int length = Math.min(this.height / this.font.lineHeight, this.interfacesList.get(i).sequences.size());
            this.interfacesList.get(i).scrollOffset = Mth.clamp(this.interfacesList.get(i).scrollOffset, 0, selected.size() - length);
            int offset = interfacesList.get(i).scrollOffset;

            poseStack.pushPose();
            poseStack.translate(0, 0, i);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            for(int m = 0; m < length; m++) {
                FormattedCharSequence sequence = selected.get(Math.min(m + offset, (selected.size()-1)));
                float posX = (float)(this.posX);
                float posY = (float)(this.posY);
                int color = Color.WHITE.getRGB();
                if (this.interfacesList.get(i).centered) {
                    posX += ((float) this.width / 2);
                    posY += ((float) this.height / 2);
                    float k = this.font.width(sequence) / 2.0F;
                    float j = (selected.size() * 7.35F) / 2.0F;
                    this.font.drawInternal(sequence, posX - k, posY - j + (this.font.lineHeight * m), color, poseStack.last().pose(), this.drawShadow);
                } else {
                    this.font.drawInternal(sequence, posX, posY + m * this.font.lineHeight, color, poseStack.last().pose(), this.drawShadow);
                }
            }
            int renderedInterfaces = this.interfacesList.stream().filter(box -> box.render).toList().size();
            if (renderedInterfaces > 1 && i < interfacesList.size()-1) {
                Color bgColor = this.interfacesList.get(i+1).backgroundColor;
                int color = bgColor != null ? ColorUtils.RGBToRGBA(bgColor.getRGB(), bgColor.getAlpha() / 255F) : ColorUtils.RGBToRGBA(backgroundColor, 0.85F);
                int width = this.widthOld > 1 ? this.widthOld : this.width;
                int height = this.heightOld > 1 ? this.heightOld : this.height;
                RenderSystem.setShaderColor(shaderColor.getRed() / 255F, shaderColor.getGreen() / 255F, shaderColor.getBlue() / 255F, shaderColor.getAlpha() / 255F);
                fill(poseStack, posX-1, posY-1, posX + width, posY + height, color);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
            poseStack.popPose();
        }
    }
    public void scrollTo(int pos, boolean replace) {
        if (boxIndex == -1) throw new IndexOutOfBoundsException("There is no selected box.");
        List<FormattedCharSequence> selected = interfacesList.get(boxIndex).sequences;
        this.interfacesList.get(boxIndex).scrollOffset = replace ? pos : this.interfacesList.get(boxIndex).scrollOffset+pos;
        this.interfacesList.get(boxIndex).scrollOffset = Mth.clamp(this.interfacesList.get(boxIndex).scrollOffset, 0, selected.size() - getBoxLinesLength());
    }

    public Style getComponentStyleAt(double mouseX, double mouseY) {
        if (boxIndex == -1) throw new IndexOutOfBoundsException("There is no selected box.");
        List<FormattedCharSequence> selected = interfacesList.get(boxIndex).sequences;
        int offset = interfacesList.get(boxIndex).scrollOffset;

        if (selected.isEmpty()) return null;
        int i = Mth.floor(mouseX - this.posX);
        int j = Mth.floor(mouseY - this.posY);
        if (i < 0 || j < 0) return null;
        if (i > this.width || j > this.height) return null;

        int linePos;
        if (interfacesList.get(boxIndex).centered) {
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

        if (interfacesList.get(boxIndex).centered) {
            int textBoxWidth = this.width / 2;
            int lineWidth = this.font.width(sequence) / 2;
            i -= textBoxWidth - lineWidth;
        }
        return this.font.getSplitter().componentStyleAtWidth(sequence, i);
    }
    public boolean canScroll() {
        if (boxIndex == -1) throw new IndexOutOfBoundsException("There is no selected box.");
        return this.height / this.font.lineHeight < this.interfacesList.get(boxIndex).sequences.size();
    }

    public boolean isEmpty() {
        if (boxIndex == -1) throw new IndexOutOfBoundsException("There is no selected box.");
        return this.interfacesList.get(boxIndex).sequences.isEmpty();
    }

    public TextScreen create(Component text, ChatFormatting... formats) {
        if (boxIndex == -1) throw new IndexOutOfBoundsException("There is no selected box.");
        List<FormattedCharSequence> result = new ArrayList<>(Language.getInstance().getVisualOrder(this.font.getSplitter().splitLines(text, this.width, Style.EMPTY.applyFormats(formats))));
        if (!this.interfacesList.get(boxIndex).sequences.isEmpty()) {
            this.interfacesList.get(boxIndex).sequences.add(FormattedCharSequence.EMPTY);
            this.interfacesList.get(boxIndex).sequences.addAll(result);
            return this;
        }
        this.interfacesList.get(boxIndex).sequences.addAll(result);
        return this;
    }
    public TextScreen create(Component title, Component description, ChatFormatting... formats) {
        if (boxIndex == -1) throw new IndexOutOfBoundsException("There is no selected box.");
        List<FormattedCharSequence> result = new ArrayList<>();
        result.addAll(Language.getInstance().getVisualOrder(this.font.getSplitter().splitLines(title, this.width, Style.EMPTY.applyFormats(formats))));
        result.addAll(Language.getInstance().getVisualOrder(this.font.getSplitter().splitLines(description, this.width, Style.EMPTY.applyFormats(formats))));
        if (!this.interfacesList.get(boxIndex).sequences.isEmpty()) {
            this.interfacesList.get(boxIndex).sequences.add(FormattedCharSequence.EMPTY);
            this.interfacesList.get(boxIndex).sequences.addAll(result);
            return this;
        }
        this.interfacesList.get(boxIndex).sequences.addAll(result);
        return this;
    }

    public TextScreen add(FormattedCharSequence formattedCharSequence) {
        if (boxIndex == -1) throw new IndexOutOfBoundsException("There is no selected box.");
        this.interfacesList.get(boxIndex).sequences.add(formattedCharSequence);
        return this;
    }
    public void addAll(List<FormattedCharSequence> formattedCharSequences) {
        if (boxIndex == -1) throw new IndexOutOfBoundsException("There is no selected box.");
        this.interfacesList.get(boxIndex).sequences.addAll(formattedCharSequences);
    }

    public void clear() {
        this.interfacesList.forEach(anInterface -> {
            anInterface.sequences.clear();
            anInterface.backgroundColor = null;
        });
    }

    @SuppressWarnings("unused")
    public boolean canRender() {
        return this.interfacesList.get(boxIndex).render;
    }
    public int getBoxLinesLength() {
        if (boxIndex == -1) throw new IndexOutOfBoundsException("There is no selected box.");
        return Math.min(this.height / this.font.lineHeight, this.interfacesList.get(boxIndex).sequences.size());
    }
    public int getRemainingLines() {
        if (boxIndex == -1) throw new IndexOutOfBoundsException("There is no selected box.");
        return this.interfacesList.get(boxIndex).sequences.size() - getBoxLinesLength();
    }
    public int getScrollOffset() {
        if (boxIndex == -1) throw new IndexOutOfBoundsException("There is no selected box.");
        return this.interfacesList.get(boxIndex).scrollOffset;
    }
    public int getWidth() {
        return width;
    }
    @SuppressWarnings("unused")
    public int getHeight() {
        return height;
    }

    @SuppressWarnings("unused")
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

    private static class Interface {
        private final List<FormattedCharSequence> sequences;
        private boolean centered;
        private boolean render;
        private Color backgroundColor;
        private int scrollOffset;

        private Interface(List<FormattedCharSequence> sequences, boolean centered, boolean render) {
            this.sequences = sequences;
            this.centered = centered;
            this.render = render;
        }
    }
}
