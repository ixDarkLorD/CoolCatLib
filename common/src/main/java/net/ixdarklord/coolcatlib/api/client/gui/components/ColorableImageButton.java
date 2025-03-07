package net.ixdarklord.coolcatlib.api.client.gui.components;

import java.awt.*;
import net.minecraft.client.gui.GuiGraphics;
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
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (color != null) guiGraphics.setColor(this.color.getRed()/255.0F, this.color.getGreen()/255.0F, this.color.getBlue()/255.0F, this.color.getAlpha()/255.0F);
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, this.alpha);
    }

    public void setColor(Color color) {
        this.color = color;
    }
}