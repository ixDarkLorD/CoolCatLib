package net.ixdarklord.coolcat_lib.client.button;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class CustomImageButton extends Button {
   private final ResourceLocation resourceLocation;
   private Color color;
   private final int xTexStart;
   private final int yTexStart;
   private final int yDiffTex;
   private final int textureWidth;
   private final int textureHeight;

   public CustomImageButton(int pX, int pY, int pWidth, int pHeight, int pXTexStart, int pYTexStart, ResourceLocation pResourceLocation, OnPress pOnPress) {
      this(pX, pY, pWidth, pHeight, pXTexStart, pYTexStart, pHeight, pResourceLocation, 256, 256, pOnPress);
   }

   public CustomImageButton(int pX, int pY, int pWidth, int pHeight, int pXTexStart, int pYTexStart, int pYDiffTex, ResourceLocation pResourceLocation, OnPress pOnPress) {
      this(pX, pY, pWidth, pHeight, pXTexStart, pYTexStart, pYDiffTex, pResourceLocation, 256, 256, pOnPress);
   }

   public CustomImageButton(int pX, int pY, int pWidth, int pHeight, int pXTexStart, int pYTexStart, int pYDiffTex, ResourceLocation pResourceLocation, int pTextureWidth, int pTextureHeight, OnPress pOnPress) {
      this(pX, pY, pWidth, pHeight, pXTexStart, pYTexStart, pYDiffTex, pResourceLocation, pTextureWidth, pTextureHeight, pOnPress, new TextComponent(""));
   }

   public CustomImageButton(int pX, int pY, int pWidth, int pHeight, int pXTexStart, int pYTexStart, int pYDiffTex, ResourceLocation pResourceLocation, int pTextureWidth, int pTextureHeight, OnPress pOnPress, Component pMessage) {
      this(pX, pY, pWidth, pHeight, pXTexStart, pYTexStart, pYDiffTex, pResourceLocation, pTextureWidth, pTextureHeight, pOnPress, NO_TOOLTIP, pMessage);
   }

   public CustomImageButton(int pX, int pY, int pWidth, int pHeight, int pXTexStart, int pYTexStart, int pYDiffTex, ResourceLocation pResourceLocation, int pTextureWidth, int pTextureHeight, OnPress pOnPress, OnTooltip pOnTooltip, Component pMessage) {
      super(pX, pY, pWidth, pHeight, pMessage, pOnPress, pOnTooltip);
      this.textureWidth = pTextureWidth;
      this.textureHeight = pTextureHeight;
      this.xTexStart = pXTexStart;
      this.yTexStart = pYTexStart;
      this.yDiffTex = pYDiffTex;
      this.resourceLocation = pResourceLocation;
   }

   public void renderButton(@NotNull PoseStack pPoseStack, int pMouseX, int pMouseY, float pPartialTick) {
      RenderSystem.setShader(GameRenderer::getPositionTexShader);
      RenderSystem.setShaderTexture(0, this.resourceLocation);
      if (color != null) RenderSystem.setShaderColor(this.color.getRed()/255.0F, this.color.getGreen()/255.0F, this.color.getBlue()/255.0F, this.color.getAlpha()/255.0F);
      int i = this.yTexStart;
      if (!this.isActive()) {
         i += this.yDiffTex * 2;
      } else if (this.isHoveredOrFocused()) {
         i += this.yDiffTex;
      }

      blit(pPoseStack, this.x, this.y, (float)this.xTexStart, (float)i, this.width, this.height, this.textureWidth, this.textureHeight);
      RenderSystem.enableDepthTest();
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      if (this.isHovered) this.renderToolTip(pPoseStack, pMouseX, pMouseY);

   }

   public void setColor(Color color) {
      this.color = color;
   }
}