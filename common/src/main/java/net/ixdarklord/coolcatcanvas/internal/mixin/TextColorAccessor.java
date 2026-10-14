package net.ixdarklord.coolcatcanvas.internal.mixin;

import net.minecraft.network.chat.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TextColor.class)
public interface TextColorAccessor {
    // The named constructor, for rainbow text colors.
    @Invoker("<init>")
    static TextColor coolcatcanvas$create(int value, String name) {
        throw new AssertionError();
    }
}
