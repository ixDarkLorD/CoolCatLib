package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// ItemOpacityRenderer: a GUI item drawn while the shader color is faded draws see-through (TranslucentItemBuffers).
// GuiAreas: the box of each blit, fill, gradient and item, the calls every GUI draw ends in (blitNineSliced,
// blitRepeating and sprite blits go through blit, so innerBlit; text is recorded from Font by FontMixin), taken to
// screen space by the pose it is drawn with. Nothing is recorded while a tooltip draws.
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {
    @Shadow
    @Final
    private PoseStack pose;

    @ModifyArg(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V"),
            index = 4)
    private MultiBufferSource coolcatlib$seeThroughWhileFaded(MultiBufferSource buffers) {
        return TranslucentItemBuffers.wrap(buffers);
    }

    @Inject(method = "innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFF)V", at = @At("HEAD"))
    private void coolcatlib$recordBlit(ResourceLocation atlas, int x1, int x2, int y1, int y2, int blitOffset,
                                       float minU, float maxU, float minV, float maxV, CallbackInfo ci) {
        GuiAreaRecorder.record(this.pose.last().pose(), x1, y1, x2, y2);
    }

    @Inject(method = "innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFFFFFF)V", at = @At("HEAD"))
    private void coolcatlib$recordTintedBlit(ResourceLocation atlas, int x1, int x2, int y1, int y2, int blitOffset,
                                             float minU, float maxU, float minV, float maxV,
                                             float red, float green, float blue, float alpha, CallbackInfo ci) {
        GuiAreaRecorder.record(this.pose.last().pose(), x1, y1, x2, y2);
    }

    // Every fill, hLine, vLine and renderOutline ends here.
    @Inject(method = "fill(Lnet/minecraft/client/renderer/RenderType;IIIIII)V", at = @At("HEAD"))
    private void coolcatlib$recordFill(RenderType renderType, int minX, int minY, int maxX, int maxY, int z, int color, CallbackInfo ci) {
        GuiAreaRecorder.record(this.pose.last().pose(), minX, minY, maxX, maxY);
    }

    @Inject(method = "fillGradient(Lcom/mojang/blaze3d/vertex/VertexConsumer;IIIIIII)V", at = @At("HEAD"))
    private void coolcatlib$recordGradient(VertexConsumer consumer, int x1, int y1, int x2, int y2, int z,
                                           int colorFrom, int colorTo, CallbackInfo ci) {
        GuiAreaRecorder.record(this.pose.last().pose(), x1, y1, x2, y2);
    }

    // Every renderItem and renderFakeItem ends here; an item is 16 pixels square from where it is drawn.
    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V", at = @At("HEAD"))
    private void coolcatlib$recordItem(LivingEntity entity, Level level, ItemStack stack, int x, int y, int seed, int guiOffset, CallbackInfo ci) {
        if (!stack.isEmpty()) GuiAreaRecorder.record(this.pose.last().pose(), x, y, x + 16, y + 16);
    }

    // The tooltip under the mouse is no one's place to keep out of: it follows the mouse and goes away.
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
