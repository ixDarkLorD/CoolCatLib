package net.ixdarklord.coolcatlib.api.client.gui.components;

import net.minecraft.util.ARGB;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
import java.awt.*;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;

public class ColorableImageButton extends ImageButton {
    private Color color;

    public ColorableImageButton(int x, int y, int width, int height, WidgetSprites sprites, OnPress onPress) {
        super(x, y, width, height, sprites, onPress);
    }

    public ColorableImageButton(int x, int y, int width, int height, WidgetSprites sprites, OnPress onPress, Component message) {
        super(x, y, width, height, sprites, onPress, message);
    }

    public ColorableImageButton(int width, int height, WidgetSprites sprites, OnPress onPress, Component message) {
        super(width, height, sprites, onPress, message);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.color == null) {
            super.extractContents(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }
        Identifier sprite = this.sprites.get(this.isActive(), this.isHoveredOrFocused());
        int tint = ARGB.color(Math.round(this.color.getAlpha() * this.alpha), this.color.getRed(), this.color.getGreen(), this.color.getBlue());
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, this.getX(), this.getY(), this.width, this.height, tint);
    }

    public void setColor(Color color) {
        this.color = color;
    }
}