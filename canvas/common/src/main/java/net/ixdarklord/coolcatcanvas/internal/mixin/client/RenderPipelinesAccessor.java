package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The entity pipelines' uniforms, for a scene viewport's glowing sprites, and the GUI's textured blit, for its picture.
@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {
    @Accessor("MATRICES_FOG_SNIPPET")
    static RenderPipeline.Snippet coolcatcanvas$matricesFogSnippet() {
        throw new AssertionError();
    }

    @Accessor("GUI_TEXTURED_SNIPPET")
    static RenderPipeline.Snippet coolcatcanvas$guiTexturedSnippet() {
        throw new AssertionError();
    }
}
