package net.ixdarklord.coolcat_lib.client.button;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.awt.*;

public class CustomImageButton extends ImageButton {
   private Color color;

   public CustomImageButton(int pX, int pY, int pWidth, int pHeight, int pXTexStart, int pYTexStart, ResourceLocation resourceLocation, OnPress onPress) {
      this(pX, pY, pWidth, pHeight, pXTexStart, pYTexStart, pHeight, resourceLocation, 256, 256, onPress);
   }

   public CustomImageButton(int pX, int pY, int pWidth, int pHeight, int pXTexStart, int pYTexStart, int pYDiffTex, ResourceLocation resourceLocation, OnPress onPress) {
      this(pX, pY, pWidth, pHeight, pXTexStart, pYTexStart, pYDiffTex, resourceLocation, 256, 256, onPress);
   }

   public CustomImageButton(int pX, int pY, int pWidth, int pHeight, int pXTexStart, int pYTexStart, int pYDiffTex, ResourceLocation resourceLocation, int pTextureWidth, int pTextureHeight, OnPress onPress) {
      this(pX, pY, pWidth, pHeight, pXTexStart, pYTexStart, pYDiffTex, resourceLocation, pTextureWidth, pTextureHeight, onPress, CommonComponents.EMPTY);
   }

   public CustomImageButton(int pX, int pY, int pWidth, int pHeight, int pXTexStart, int pYTexStart, int pYDiffTex, ResourceLocation pResourceLocation, int pTextureWidth, int pTextureHeight, OnPress pOnPress, Component pMessage) {
      super(pX, pY, pWidth, pHeight, pXTexStart, pYTexStart, pYDiffTex, pResourceLocation, pTextureWidth, pTextureHeight, pOnPress, pMessage);
   }

   @Override
   public void renderTexture(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int uOffset, int vOffset, int textureDifference, int width, int height, int textureWidth, int textureHeight) {
      int i = vOffset;
      if (!this.isActive()) {
         i = vOffset + textureDifference * 2;
      } else if (this.isHoveredOrFocused()) {
         i = vOffset + textureDifference;
      }

      if (color != null) RenderSystem.setShaderColor(this.color.getRed()/255.0F, this.color.getGreen()/255.0F, this.color.getBlue()/255.0F, this.color.getAlpha()/255.0F);
      RenderSystem.enableDepthTest();
      guiGraphics.blit(texture, x, y, (float)uOffset, (float)i, width, height, textureWidth, textureHeight);
   }

   public void setColor(Color color) {
      this.color = color;
   }
}