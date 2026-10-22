package net.ixdarklord.coolcatcanvas.internal.client.armature;

import net.ixdarklord.coolcatcanvas.internal.client.armature.anim.ClipSource;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A model as a file reader leaves it: joints, faces, points, textures and clips, all in Blockbench's own axes (model
 * pixels, Y up, facing north). {@link ArmatureLibrary} bakes it.
 */
public final class ArmatureData {
    public final List<JointData> joints = new ArrayList<>();
    public final List<Face> faces = new ArrayList<>();
    public final List<PointData> points = new ArrayList<>();
    public final List<TextureData> textures = new ArrayList<>();
    public final List<ClipSource> clips = new ArrayList<>();
    /** A Java block model, whose origin is the corner of the block rather than the middle of its base. */
    public boolean blockOrigin;

    public int addJoint(String name, int parent, Vector3f pivot, Vector3f rotationRadians) {
        this.joints.add(new JointData(name, parent, pivot, rotationRadians));
        return this.joints.size() - 1;
    }

    /** Joints so that parents come before children (Bedrock files list bones in any order). */
    public int[] sortJoints() {
        int n = this.joints.size();
        int[] order = new int[n];
        boolean[] placed = new boolean[n];
        int count = 0;
        while (count < n) {
            boolean progress = false;
            for (int j = 0; j < n; j++) {
                if (placed[j]) continue;
                int parent = this.joints.get(j).parent;
                if (parent < 0 || placed[parent]) {
                    placed[j] = true;
                    order[count++] = j;
                    progress = true;
                }
            }
            if (!progress) {
                // A loop of parents: break it by making the rest roots.
                for (int j = 0; j < n; j++) {
                    if (!placed[j]) {
                        this.joints.get(j).parent = -1;
                    }
                }
            }
        }
        int[] newIndex = new int[n];
        for (int i = 0; i < n; i++) newIndex[order[i]] = i;
        List<JointData> sorted = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            JointData joint = this.joints.get(order[i]);
            if (joint.parent >= 0) joint.parent = newIndex[joint.parent];
            sorted.add(joint);
        }
        this.joints.clear();
        this.joints.addAll(sorted);
        for (Face face : this.faces) face.joint = newIndex[face.joint];
        for (PointData point : this.points) point.joint = newIndex[point.joint];
        return newIndex;
    }

    public static final class JointData {
        public final String name;
        public int parent;
        public final Vector3f pivot;
        public final Vector3f rotation;

        JointData(String name, int parent, Vector3f pivot, Vector3f rotation) {
            this.name = name;
            this.parent = parent;
            this.pivot = pivot;
            this.rotation = rotation;
        }
    }

    /** Four corners in order around the face (a triangle repeats its last), with texture coordinates from 0 to 1. */
    public static final class Face {
        public int joint;
        public final int texture;
        public final float[] positions;
        public final float[] uvs;
        public final Vector3f normal;

        public Face(int joint, int texture, float[] positions, float[] uvs, Vector3f normal) {
            this.joint = joint;
            this.texture = texture;
            this.positions = positions;
            this.uvs = uvs;
            this.normal = normal;
        }
    }

    public static final class PointData {
        public final String name;
        public int joint;
        public final Vector3f position;
        public final Vector3f rotation;

        public PointData(String name, int joint, Vector3f position, Vector3f rotation) {
            this.name = name;
            this.joint = joint;
            this.position = position;
            this.rotation = rotation;
        }
    }

    /**
     * A texture slot: pixels inside the model file, or a texture in a resource pack.
     *
     * @param width,height the size its faces' coordinates count in (Blockbench's UV size)
     */
    public record TextureData(String name, byte @Nullable [] png, @Nullable Identifier location, int width, int height) {
    }

    // ---- Shared by the readers ----

    /** Makes the six faces of a cube (or fewer: faces given no texture are left out). */
    public void addCube(int joint, Vector3f from, Vector3f to, float inflate, Vector3f origin, Vector3f rotationRadians,
                        Map<Direction, CubeFace> faces) {
        float x0 = Math.min(from.x, to.x) - inflate, y0 = Math.min(from.y, to.y) - inflate, z0 = Math.min(from.z, to.z) - inflate;
        float x1 = Math.max(from.x, to.x) + inflate, y1 = Math.max(from.y, to.y) + inflate, z1 = Math.max(from.z, to.z) + inflate;
        Matrix4f turn = new Matrix4f().translate(origin).rotateZYX(rotationRadians.z, rotationRadians.y, rotationRadians.x).translate(-origin.x, -origin.y, -origin.z);
        for (Map.Entry<Direction, CubeFace> entry : faces.entrySet()) {
            Direction side = entry.getKey();
            CubeFace face = entry.getValue();
            float[][] corners = corners(side, x0, y0, z0, x1, y1, z1);
            // A face with no area (one side of a flat plane) draws nothing.
            float w = side.getAxis() == Direction.Axis.X ? z1 - z0 : x1 - x0;
            float h = side.getAxis() == Direction.Axis.Y ? z1 - z0 : y1 - y0;
            if (w <= 0.0F || h <= 0.0F) continue;
            float[] positions = new float[12];
            for (int c = 0; c < 4; c++) {
                Vector3f p = turn.transformPosition(new Vector3f(corners[c][0], corners[c][1], corners[c][2]));
                positions[c * 3] = p.x;
                positions[c * 3 + 1] = p.y;
                positions[c * 3 + 2] = p.z;
            }
            Vector3f normal = turn.transformDirection(new Vector3f(side.getStepX(), side.getStepY(), side.getStepZ())).normalize();
            this.faces.add(new Face(joint, face.texture, positions, face.uvCorners(), normal));
        }
    }

    // A face's corners in order: top-left, bottom-left, bottom-right, top-right as seen from outside, matching the
    // texture's corners (u0,v0), (u0,v1), (u1,v1), (u1,v0). The same layout as Minecraft's block model faces.
    private static float[][] corners(Direction side, float x0, float y0, float z0, float x1, float y1, float z1) {
        return switch (side) {
            case NORTH -> new float[][]{{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}};
            case SOUTH -> new float[][]{{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}};
            case EAST -> new float[][]{{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}};
            case WEST -> new float[][]{{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}};
            case UP -> new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
            case DOWN -> new float[][]{{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}};
        };
    }

    /**
     * One side of a cube: the texture rectangle {@code (u0,v0)-(u1,v1)} (0 to 1; u0 > u1 mirrors) and how many
     * quarter turns clockwise it is rotated.
     */
    public record CubeFace(int texture, float u0, float v0, float u1, float v1, int quarterTurns) {
        float[] uvCorners() {
            float[][] base = {{this.u0, this.v0}, {this.u0, this.v1}, {this.u1, this.v1}, {this.u1, this.v0}};
            float[] out = new float[8];
            int turns = Math.floorMod(this.quarterTurns, 4);
            for (int c = 0; c < 4; c++) {
                float[] uv = base[(c + turns) % 4];
                out[c * 2] = uv[0];
                out[c * 2 + 1] = uv[1];
            }
            return out;
        }
    }

    /** Joints by name, for animation files and definitions that name them. */
    public Map<String, Integer> jointIndex() {
        Map<String, Integer> index = new HashMap<>();
        for (int j = 0; j < this.joints.size(); j++) index.putIfAbsent(this.joints.get(j).name, j);
        return index;
    }
}
