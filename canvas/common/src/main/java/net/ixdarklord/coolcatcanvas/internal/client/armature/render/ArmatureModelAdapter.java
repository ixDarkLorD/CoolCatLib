package net.ixdarklord.coolcatcanvas.internal.client.armature.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureFrame;
import net.ixdarklord.coolcatcanvas.internal.client.armature.ArmatureMesh;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FastColor;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a posed armature's faces for one mesh group into a vertex consumer, as a vanilla {@link Model}: what a
 * {@code MultiBufferSource} hands out already carries the glowing outline and the block-breaking overlay on 1.21.1, so
 * drawing through it gets those for free. The frame is the one handed to {@link #setupAnim}; vanilla sets a model up and
 * draws it straight away, so one adapter per group serves every armature.
 */
public final class ArmatureModelAdapter extends Model {
    private static final List<ArmatureModelAdapter> BY_GROUP = new ArrayList<>();

    private final int group;
    private final Matrix4f position = new Matrix4f();
    private final Matrix3f normal = new Matrix3f();
    private final Vector3f corner = new Vector3f();
    private final Vector3f faceNormal = new Vector3f();
    private final Vector3f part = new Vector3f();
    private @Nullable ArmatureFrame frame;
    // Cloth: each joint's matrix into the pose stack's space, worked out once a draw as the skin asks for it.
    private Matrix4f[] placed = new Matrix4f[0];
    private int[] placedAt = new int[0];
    private int draw;

    private ArmatureModelAdapter(int group) {
        super(RenderType::entityCutoutNoCull);
        this.group = group;
    }

    /** The adapter that draws one mesh group. Render thread only. */
    public static ArmatureModelAdapter forGroup(int group) {
        while (BY_GROUP.size() <= group) BY_GROUP.add(new ArmatureModelAdapter(BY_GROUP.size()));
        return BY_GROUP.get(group);
    }

    public void setupAnim(ArmatureFrame frame) {
        this.frame = frame;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int lightCoords, int overlayCoords, int color) {
        ArmatureFrame frame = this.frame;
        if (frame == null) return;
        PoseStack.Pose pose = poseStack.last();
        ArmatureMesh.Group[] groups = frame.armature().mesh().groups();
        if (this.group >= groups.length) return;
        float[][] byJoint = groups[this.group].byJoint();
        float[][] skins = groups[this.group].skin();
        if (skins != null && this.placed.length < byJoint.length) {
            this.placed = new Matrix4f[byJoint.length];
            for (int i = 0; i < byJoint.length; i++) this.placed[i] = new Matrix4f();
            this.placedAt = new int[byJoint.length];
        }
        this.draw++;
        for (int j = 0; j < byJoint.length; j++) {
            float[] faces = byJoint[j];
            if (faces == null || !frame.visible(j)) continue;
            this.position.set(pose.pose()).mul(frame.joint(j));
            this.normal.set(pose.normal()).mul(frame.normal(j));
            int light = frame.glows(j) ? LightTexture.FULL_BRIGHT : lightCoords;
            int tint = frame.tint(j);
            int argb = tint == -1 ? color : FastColor.ARGB32.multiply(color, tint);
            float[] skin = skins != null ? skins[j] : null;
            for (int at = 0, face = 0; at < faces.length; at += ArmatureMesh.STRIDE, face++) {
                this.normal.transform(faces[at + 20], faces[at + 21], faces[at + 22], this.faceNormal).normalize();
                float nx = this.faceNormal.x, ny = this.faceNormal.y, nz = this.faceNormal.z;
                for (int c = 0; c < 4; c++) {
                    int v = at + c * 5;
                    if (skin != null) this.skinned(skin, (face * 4 + c) * ArmatureMesh.Group.SKIN_CORNER, pose, frame);
                    else this.position.transformPosition(faces[v], faces[v + 1], faces[v + 2], this.corner);
                    buffer.addVertex(this.corner.x, this.corner.y, this.corner.z, argb, faces[v + 3], faces[v + 4], overlayCoords, light, nx, ny, nz);
                }
            }
        }
    }

    // A cloth corner: where each joint weighing it would put it, blended by their weights.
    private void skinned(float[] skin, int at, PoseStack.Pose pose, ArmatureFrame frame) {
        this.corner.zero();
        for (int i = 0; i < ArmatureMesh.Group.SKIN_CORNER; i += 5) {
            float weight = skin[at + i + 1];
            if (weight <= 0.0F) continue;
            int joint = (int) skin[at + i];
            if (this.placedAt[joint] != this.draw) {
                this.placed[joint].set(pose.pose()).mul(frame.joint(joint));
                this.placedAt[joint] = this.draw;
            }
            this.placed[joint].transformPosition(skin[at + i + 2], skin[at + i + 3], skin[at + i + 4], this.part);
            this.corner.fma(weight, this.part);
        }
    }
}
