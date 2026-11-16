package net.ixdarklord.coolcatcanvas.internal.mixin.client.render;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The view the level is culled by, for block renderers' culling (internal/client/blockentity): 1.20.1 keeps it here,
// not on the camera.
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    @Accessor("cullingFrustum")
    @Nullable Frustum coolcatcanvas$cullingFrustum();

    @Accessor("capturedFrustum")
    @Nullable Frustum coolcatcanvas$capturedFrustum();
}
