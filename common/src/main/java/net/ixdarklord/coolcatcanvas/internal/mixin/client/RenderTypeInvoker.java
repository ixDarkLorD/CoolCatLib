package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// A render type for a scene viewport's glowing sprites, with a pipeline of CoolCatLib's own.
@Mixin(RenderType.class)
public interface RenderTypeInvoker {
    @Invoker("create")
    static RenderType coolcatcanvas$create(String name, RenderSetup setup) {
        throw new AssertionError();
    }
}
