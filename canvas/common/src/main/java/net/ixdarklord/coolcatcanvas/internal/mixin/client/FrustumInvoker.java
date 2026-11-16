package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// A box's test against the view without making an AABB, for block renderers' culling (internal/client/blockentity).
@Mixin(Frustum.class)
public interface FrustumInvoker {
    @Invoker("cubeInFrustum")
    int coolcatcanvas$cubeInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
}
