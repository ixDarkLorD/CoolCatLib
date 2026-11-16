package net.ixdarklord.coolcatcanvas.internal.client.blockentity;

import net.ixdarklord.coolcatcanvas.internal.mixin.client.render.FrustumInvoker;
import net.ixdarklord.coolcatcanvas.internal.mixin.client.render.LevelRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

// The frame block renderers are in (counted from GameRendererMixin, at the start of each frame), for their
// once-a-frame work, and the view they're culled against. Client thread only.
public final class BlockRenderFrames {
    private static int frame;

    private BlockRenderFrames() {}

    public static void beginFrame() {
        frame++;
    }

    public static int current() {
        return frame;
    }

    // Whether this box, in blocks from the block's corner, is in the main camera's view (true while there is no view).
    public static boolean inView(BlockPos pos, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        Frustum frustum = frustum();
        if (frustum == null) return true;
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        return ((FrustumInvoker) frustum).coolcatcanvas$cubeInFrustum(x + minX, y + minY, z + minZ, x + maxX, y + maxY, z + maxZ);
    }

    // The view the level is culled by this frame (the captured one while F3's frustum capture holds one).
    private static @Nullable Frustum frustum() {
        LevelRendererAccessor renderer = (LevelRendererAccessor) Minecraft.getInstance().levelRenderer;
        if (renderer == null) return null;
        Frustum captured = renderer.coolcatcanvas$capturedFrustum();
        return captured != null ? captured : renderer.coolcatcanvas$cullingFrustum();
    }
}
