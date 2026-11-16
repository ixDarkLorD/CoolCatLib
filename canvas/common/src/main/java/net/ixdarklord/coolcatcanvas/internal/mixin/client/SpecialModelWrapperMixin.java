package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.internal.client.armature.render.ArmatureItemRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.SpecialModelWrapper;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// An armature-driven item moves every frame; marked animated, inventory slots draw it afresh each frame instead of once.
@Mixin(SpecialModelWrapper.class)
public abstract class SpecialModelWrapperMixin {
    @Shadow
    @Final
    private SpecialModelRenderer<?> specialRenderer;

    @Inject(method = "update", at = @At("TAIL"))
    private void coolcatcanvas$animateArmatures(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver, ItemDisplayContext displayContext,
                                                @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed, CallbackInfo ci) {
        if (ArmatureItemRenderer.animates(this.specialRenderer)) output.setAnimated();
    }
}
