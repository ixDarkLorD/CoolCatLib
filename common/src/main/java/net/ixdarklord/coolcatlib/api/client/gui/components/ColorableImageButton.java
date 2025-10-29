package net.ixdarklord.coolcatlib.api.client.gui.components;

import com.mojang.blaze3d.systems.RenderSystem;
import net.ixdarklord.coolcatlib.api.client.gui.components.widgets.WidgetSprites;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class ColorableImageButton extends ImageButton {
    protected final WidgetSprites sprites;
    protected Color color;

    public ColorableImageButton(int pX, int pY, int pWidth, int pHeight, int pXTexStart, int pYTexStart, WidgetSprites sprites, OnPress onPress) {
        this(pX, pY, pWidth, pHeight, pXTexStart, pYTexStart, pHeight, sprites, 256, 256, onPress);
    }

    public ColorableImageButton(int pX, int pY, int pWidth, int pHeight, int pXTexStart, int pYTexStart, int pYDiffTex, WidgetSprites sprites, OnPress onPress) {
        this(pX, pY, pWidth, pHeight, pXTexStart, pYTexStart, pYDiffTex, sprites, 256, 256, onPress);
    }

    public ColorableImageButton(int pX, int pY, int pWidth, int pHeight, int pXTexStart, int pYTexStart, int pYDiffTex, WidgetSprites sprites, int pTextureWidth, int pTextureHeight, OnPress onPress) {
        this(pX, pY, pWidth, pHeight, pXTexStart, pYTexStart, pYDiffTex, sprites, pTextureWidth, pTextureHeight, onPress, CommonComponents.EMPTY);
    }

    public ColorableImageButton(int pX, int pY, int pWidth, int pHeight, int pXTexStart, int pYTexStart, int pYDiffTex, WidgetSprites sprites, int pTextureWidth, int pTextureHeight, OnPress pOnPress, Component pMessage) {
        super(pX, pY, pWidth, pHeight, pXTexStart, pYTexStart, pYDiffTex, sprites.enabled(), pTextureWidth, pTextureHeight, pOnPress, pMessage);
        this.sprites = sprites;
    }

    @Override
    public void renderTexture(@NotNull GuiGraphics guiGraphics, @NotNull ResourceLocation texture, int x, int y, int uOffset, int vOffset, int textureDifference, int width, int height, int textureWidth, int textureHeight) {
        if (color != null)
            RenderSystem.setShaderColor(this.color.getRed() / 255.0F, this.color.getGreen() / 255.0F, this.color.getBlue() / 255.0F, this.color.getAlpha() / 255.0F);
        RenderSystem.enableDepthTest();
        guiGraphics.blit(this.sprites.get(this.isActive(), this.isHoveredOrFocused()), x, y, 0, 0, width, height, textureWidth, textureHeight);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void setColor(Color color) {
        this.color = color;
    }
}