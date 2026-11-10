package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The special item model types, so the armature item model ("coolcatlib:armature") and mods' own
// (api/client/registry/SpecialModelRendererRegistry) can be among them on either loader.
@Mixin(SpecialModelRenderers.class)
public interface SpecialModelRenderersAccessor {
    @Accessor("ID_MAPPER")
    static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends SpecialModelRenderer.Unbaked<?>>> coolcatcanvas$idMapper() {
        throw new AssertionError();
    }
}
