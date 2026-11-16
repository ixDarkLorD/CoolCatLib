package net.ixdarklord.coolcatcanvas.internal.client.armature.io;

import net.minecraft.core.Direction;
import org.joml.Vector3f;

/**
 * Box UV: a cube's six faces unfolded from one corner of the texture, the layout Minecraft's entity models use and
 * Blockbench's "box UV" mode makes. Top and bottom in the first row, then east, north, west and south.
 */
final class BoxUv {
    private BoxUv() {
    }

    /** The face's texture rectangle in pixels, {@code [u0, v0, u1, v1]}; u0 > u1 where the face is mirrored. */
    static float[] face(Direction side, Vector3f offset, Vector3f size, boolean mirror) {
        float w = size.x, h = size.y, d = size.z;
        float[] rect = switch (side) {
            case EAST -> mirror ? new float[]{d + w, d, d, h} : new float[]{0.0F, d, d, h};
            case WEST -> mirror ? new float[]{0.0F, d, d, h} : new float[]{d + w, d, d, h};
            case NORTH -> new float[]{d, d, w, h};
            case SOUTH -> new float[]{d * 2 + w, d, w, h};
            case UP -> new float[]{d + w, d, -w, -d};
            case DOWN -> new float[]{d + w * 2, 0.0F, -w, d};
        };
        float u0 = offset.x + rect[0], v0 = offset.y + rect[1];
        float u1 = u0 + rect[2], v1 = v0 + rect[3];
        if (mirror) {
            float swap = u0;
            u0 = u1;
            u1 = swap;
        }
        return new float[]{u0, v0, u1, v1};
    }
}
