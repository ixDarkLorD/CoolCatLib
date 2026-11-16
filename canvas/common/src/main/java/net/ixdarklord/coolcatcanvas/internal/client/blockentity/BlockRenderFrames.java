package net.ixdarklord.coolcatcanvas.internal.client.blockentity;

import net.ixdarklord.coolcatcanvas.internal.mixin.client.FrustumInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

// The frame block renderers are in (counted from GameRendererMixin, at the start of each frame), for their state pools
// and once-a-frame work, and the view they're culled against. Client thread only.
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
        int result = ((FrustumInvoker) frustum).coolcatcanvas$cubeInFrustum(x + minX, y + minY, z + minZ, x + maxX, y + maxY, z + maxZ);
        // (As Frustum.isVisible: FrustumIntersection's INSIDE and INTERSECT.)
        return result == -2 || result == -1;
    }

    private static @Nullable Frustum frustum() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.gameRenderer == null ? null : minecraft.gameRenderer.getMainCamera().getCullFrustum();
    }
}
