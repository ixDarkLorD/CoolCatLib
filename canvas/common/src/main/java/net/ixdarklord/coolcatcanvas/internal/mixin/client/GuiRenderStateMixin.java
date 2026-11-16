package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.internal.client.gui.GuiAreaRecorder;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// GuiAreas: the box of everything the HUD and the open screen add, recorded as it comes.
@Mixin(GuiRenderState.class)
public abstract class GuiRenderStateMixin {
    @Inject(method = "addItem", at = @At("HEAD"))
    private void coolcatlib$recordItem(GuiItemRenderState state, CallbackInfo ci) {
        GuiAreaRecorder.record(state.bounds());
    }

    @Inject(method = "addText", at = @At("HEAD"))
    private void coolcatlib$recordText(GuiTextRenderState state, CallbackInfo ci) {
        GuiAreaRecorder.record(state.bounds());
    }

    @Inject(method = "addPicturesInPictureState", at = @At("HEAD"))
    private void coolcatlib$recordPicture(PictureInPictureRenderState state, CallbackInfo ci) {
        GuiAreaRecorder.record(state.bounds());
    }

    @Inject(method = "addGuiElement", at = @At("HEAD"))
    private void coolcatlib$recordElement(GuiElementRenderState state, CallbackInfo ci) {
        GuiAreaRecorder.record(state.bounds());
    }
}
