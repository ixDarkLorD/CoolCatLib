package net.ixdarklord.coolcatcanvas.api.client.gui.components;

import net.ixdarklord.coolcatcanvas.api.client.gui.components.widgets.WidgetSprites;
import net.ixdarklord.coolcatcore.api.utils.ARGB;
import java.awt.*;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * An image button drawn from {@link WidgetSprites} (one texture per state), optionally tinted with a color.
 */
public class ColorableImageButton extends ImageButton {
    protected final WidgetSprites sprites;
    private Color color;

    public ColorableImageButton(int x, int y, int width, int height, WidgetSprites sprites, OnPress onPress) {
        this(x, y, width, height, sprites, onPress, CommonComponents.EMPTY);
    }

    public ColorableImageButton(int x, int y, int width, int height, WidgetSprites sprites, OnPress onPress, Component message) {
        super(x, y, width, height, 0, 0, 0, sprites.enabled(), width, height, onPress, message);
        this.sprites = sprites;
    }

    public ColorableImageButton(int width, int height, WidgetSprites sprites, OnPress onPress, Component message) {
        this(0, 0, width, height, sprites, onPress, message);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.color == null) {
            this.sprites.blit(guiGraphics, this.isActive(), this.isHoveredOrFocused(), this.getX(), this.getY(), this.width, this.height,
                    ARGB.white(this.alpha));
            return;
        }
        int tint = ARGB.color(Math.round(this.color.getAlpha() * this.alpha), this.color.getRed(), this.color.getGreen(), this.color.getBlue());
        this.sprites.blit(guiGraphics, this.isActive(), this.isHoveredOrFocused(), this.getX(), this.getY(), this.width, this.height, tint);
    }

    public void setColor(Color color) {
        this.color = color;
    }
}
