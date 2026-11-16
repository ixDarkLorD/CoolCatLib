package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.ixdarklord.coolcatcanvas.internal.client.gui.GuiAreaRecorder;
import net.ixdarklord.coolcatcanvas.internal.client.gui.TranslucentItemBuffers;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

// ItemOpacityRenderer: a GUI item drawn while the shader color is faded draws see-through (TranslucentItemBuffers).
// GuiAreas: 1.21.1 draws at once, so the box of each thing is recorded from the primitives every draw ends in (the two
// innerBlits under every blit and sprite, fill, fillGradient, fillRenderType, the text, the items), taken to the
// screen through the pose; the tooltip under the mouse is left out.
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {
    @ModifyArg(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V"),
            index = 4)
    private MultiBufferSource coolcatlib$seeThroughWhileFaded(MultiBufferSource buffers) {
        return TranslucentItemBuffers.wrap(buffers);
    }

    @Unique
    private void coolcatlib$record(float x1, float y1, float x2, float y2) {
        if (GuiAreaRecorder.isRecording()) {
            GuiAreaRecorder.record(((GuiGraphics) (Object) this).pose().last().pose(), x1, y1, x2, y2);
        }
    }

    @Inject(method = "innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFF)V", at = @At("HEAD"))
    private void coolcatlib$recordBlit(ResourceLocation atlas, int x1, int x2, int y1, int y2, int blitOffset,
                                       float minU, float maxU, float minV, float maxV, CallbackInfo ci) {
        this.coolcatlib$record(x1, y1, x2, y2);
    }

    @Inject(method = "innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFFFFFF)V", at = @At("HEAD"))
    private void coolcatlib$recordTintedBlit(ResourceLocation atlas, int x1, int x2, int y1, int y2, int blitOffset,
                                             float minU, float maxU, float minV, float maxV,
                                             float red, float green, float blue, float alpha, CallbackInfo ci) {
        this.coolcatlib$record(x1, y1, x2, y2);
    }

    @Inject(method = "fill(Lnet/minecraft/client/renderer/RenderType;IIIIII)V", at = @At("HEAD"))
    private void coolcatlib$recordFill(RenderType renderType, int minX, int minY, int maxX, int maxY, int z, int color, CallbackInfo ci) {
        this.coolcatlib$record(minX, minY, maxX, maxY);
    }

    @Inject(method = "fillGradient(Lcom/mojang/blaze3d/vertex/VertexConsumer;IIIIIII)V", at = @At("HEAD"))
    private void coolcatlib$recordGradient(VertexConsumer consumer, int x1, int y1, int x2, int y2, int z, int colorFrom, int colorTo, CallbackInfo ci) {
        this.coolcatlib$record(x1, y1, x2, y2);
    }

    @Inject(method = "fillRenderType", at = @At("HEAD"))
    private void coolcatlib$recordRenderTypeFill(RenderType renderType, int x1, int y1, int x2, int y2, int z, CallbackInfo ci) {
        this.coolcatlib$record(x1, y1, x2, y2);
    }

    // Text is recorded where it is handed to the font: on Fabric in the int overloads, on NeoForge and Forge in the
    // float ones they add (their int overloads only pass on to those), so each line is recorded once on every loader.
    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;drawInBatch(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;IIZ)I"))
    private void coolcatlib$recordString(Font font, @Nullable String text, int x, int y, int color, boolean dropShadow, CallbackInfoReturnable<Integer> cir) {
        if (GuiAreaRecorder.isRecording() && text != null) this.coolcatlib$record(x, y, x + font.width(text), y + font.lineHeight);
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I"))
    private void coolcatlib$recordSequence(Font font, FormattedCharSequence text, int x, int y, int color, boolean dropShadow, CallbackInfoReturnable<Integer> cir) {
        if (GuiAreaRecorder.isRecording()) this.coolcatlib$record(x, y, x + font.width(text), y + font.lineHeight);
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;FFIZ)I", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;drawInBatch(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;IIZ)I"))
    private void coolcatlib$recordFloatString(Font font, @Nullable String text, float x, float y, int color, boolean dropShadow, CallbackInfoReturnable<Integer> cir) {
        if (GuiAreaRecorder.isRecording() && text != null) this.coolcatlib$record(x, y, x + font.width(text), y + font.lineHeight);
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;FFIZ)I", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I"))
    private void coolcatlib$recordFloatSequence(Font font, FormattedCharSequence text, float x, float y, int color, boolean dropShadow, CallbackInfoReturnable<Integer> cir) {
        if (GuiAreaRecorder.isRecording()) this.coolcatlib$record(x, y, x + font.width(text), y + font.lineHeight);
    }

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V", at = @At("HEAD"))
    private void coolcatlib$recordItem(@Nullable LivingEntity entity, @Nullable Level level, ItemStack stack, int x, int y, int seed, int guiOffset, CallbackInfo ci) {
        if (!stack.isEmpty()) this.coolcatlib$record(x, y, x + 16, y + 16);
    }

    @Inject(method = "renderTooltipInternal", at = @At("HEAD"))
    private void coolcatlib$pauseForTooltip(Font font, List<ClientTooltipComponent> components, int mouseX, int mouseY,
                                            ClientTooltipPositioner positioner, CallbackInfo ci) {
        GuiAreaRecorder.pause();
    }

    @Inject(method = "renderTooltipInternal", at = @At("RETURN"))
    private void coolcatlib$resumeAfterTooltip(Font font, List<ClientTooltipComponent> components, int mouseX, int mouseY,
                                               ClientTooltipPositioner positioner, CallbackInfo ci) {
        GuiAreaRecorder.resume();
    }
}
