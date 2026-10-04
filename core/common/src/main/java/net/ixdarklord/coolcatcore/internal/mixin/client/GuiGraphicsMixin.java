package net.ixdarklord.coolcatcore.internal.mixin.client;

import net.ixdarklord.coolcatcore.internal.client.ItemDecorators;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Item decorators (DecoratedItem) draw after vanilla's own decorations: its bar, cooldown and count.
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {
    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("TAIL"))
    private void coolcatcore$decorateItem(Font font, ItemStack stack, int x, int y, @Nullable String countText, CallbackInfo callback) {
        if (!stack.isEmpty()) ItemDecorators.extract((GuiGraphics) (Object) this, font, stack, x, y);
    }
}
