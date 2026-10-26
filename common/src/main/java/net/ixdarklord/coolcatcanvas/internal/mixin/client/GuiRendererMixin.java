package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.internal.client.gui.GuiOpacity;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ItemOpacityRenderer: a GUI item is blitted from the item atlas at the opacity it was drawn at. The atlas is premultiplied, so
// the color scales every channel, not only alpha.
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    // The item being blitted, between the two injections below (one render thread, one item at a time).
    @Unique
    private static float coolcatlib$itemOpacity = 1.0F;

    @Inject(method = "submitBlitFromItemAtlas", at = @At("HEAD"))
    private void coolcatlib$readOpacity(GuiItemRenderState itemState, GuiItemAtlas.SlotView slotView, CallbackInfo ci) {
        // The state is a final class, so the interface GuiItemRenderStateMixin adds is reached through Object.
        coolcatlib$itemOpacity = (Object) itemState instanceof GuiOpacity.FadedItem faded ? faded.coolcatlib$opacity() : 1.0F;
    }

    @ModifyArg(method = "submitBlitFromItemAtlas",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;addBlitToCurrentLayer(Lnet/minecraft/client/renderer/state/gui/BlitRenderState;)V"))
    private BlitRenderState coolcatlib$fade(BlitRenderState blit) {
        float opacity = coolcatlib$itemOpacity;
        if (opacity >= 1.0F) return blit;
        return new BlitRenderState(blit.pipeline(), blit.textureSetup(), blit.pose(), blit.x0(), blit.y0(), blit.x1(), blit.y1(),
                blit.u0(), blit.u1(), blit.v0(), blit.v1(), ARGB.colorFromFloat(opacity, opacity, opacity, opacity), blit.scissorArea(), blit.bounds());
    }
}
