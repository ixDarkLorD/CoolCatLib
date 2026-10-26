package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.internal.client.gui.GuiOpacity;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// FadedItems: each GUI item remembers the opacity it was drawn at, for GuiRendererMixin to blit it with.
@Mixin(GuiItemRenderState.class)
public abstract class GuiItemRenderStateMixin implements GuiOpacity.FadedItem {
    @Unique
    private float coolcatlib$opacity = 1.0F;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void coolcatlib$rememberOpacity(CallbackInfo ci) {
        this.coolcatlib$opacity = GuiOpacity.items();
    }

    @Override
    public float coolcatlib$opacity() {
        return this.coolcatlib$opacity;
    }
}
