package net.ixdarklord.coolcatcanvas.api.client.armature;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.coolcatcanvas.api.client.model.ObjModel;
import net.ixdarklord.coolcatcanvas.api.client.model.ObjModels;
import net.ixdarklord.coolcatcanvas.internal.client.armature.ArmatureMesh;
import net.ixdarklord.coolcatcanvas.internal.client.armature.render.ArmatureModelAdapter;
import net.ixdarklord.coolcatcanvas.internal.client.render.Poses;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.function.IntFunction;

/**
 * Draws a posed armature. CoolCatLib's armature renderers use it; call it yourself to draw an armature inside a renderer of your own.
 * The pose stack should be where the model's origin goes (an entity's feet, a block's base centre), turned the way
 * the model faces.
 * <pre>{@code
 * ArmatureFrame frame = animator.update(clock, partialTick, modelToWorld);
 * ArmatureDraw.submit(frame, poseStack, buffers, ArmatureDraw.Style.lit(light));
 * }</pre>
 */
public final class ArmatureDraw {
    private ArmatureDraw() {
    }

    /**
     * The matrix from model pixels to the pose stack's space for an armature: its scale (16 pixels to a block, and its own
     * {@code .armature.json} scale) and offset. Renderers multiply their placement by this to get a frame's model-to-world.
     */
    public static Matrix4f modelTransform(BakedArmature armature) {
        float scale = armature.scale() / 16.0F;
        return new Matrix4f().scale(scale).translate(armature.offset().x(), armature.offset().y(), armature.offset().z());
    }

    public static void submit(ArmatureFrame frame, PoseStack poseStack, MultiBufferSource buffers, Style style) {
        BakedArmature armature = frame.armature();
        ArmatureMesh.Group[] groups = armature.mesh().groups();
        if (groups.length == 0 && frame.props().isEmpty()) return;
        poseStack.pushPose();
        Poses.mulPose(poseStack, modelTransform(armature));
        for (int g = 0; g < groups.length; g++) {
            ArmatureMesh.Group group = groups[g];
            ArmatureModelAdapter adapter = ArmatureModelAdapter.forGroup(g);
            ResourceLocation texture = style.texture != null ? style.texture.apply(group.texture()) : null;
            if (texture == null) texture = armature.texture(group.texture());
            RenderType type;
            if (style.outlineOnly) type = RenderType.outline(texture);
            else if (style.ghost) type = RenderType.itemEntityTranslucentCull(texture);
            else if (style.translucent && group.material().lit()) type = RenderType.entityTranslucent(texture);
            else type = group.material().renderType(texture);
            int light = group.material().lit() ? style.light : LightTexture.FULL_BRIGHT;
            adapter.setupAnim(frame);
            adapter.renderToBuffer(poseStack, buffers.getBuffer(type), light, style.overlay, style.color);
            if (style.outlineOnly) continue;
            ResourceLocation glow = armature.glowTexture(group.texture());
            if (glow != null && style.glow > 0.0F) {
                int color = style.glow >= 1.0F ? style.color : scaleRgb(style.color, style.glow);
                adapter.renderToBuffer(poseStack, buffers.getBuffer(RenderType.eyes(glow)), LightTexture.FULL_BRIGHT, style.overlay, color);
            }
            if (style.foil) adapter.renderToBuffer(poseStack, buffers.getBuffer(RenderType.entityGlint()), light, style.overlay, -1);
        }
        if (!style.outlineOnly) {
            for (ObjProp prop : frame.props()) submitProp(frame, prop, poseStack, buffers, style);
        }
        poseStack.popPose();
    }

    // An .obj prop at its point, in model pixels until the point, then blocks; hidden, lit and tinted with its joint.
    private static void submitProp(ArmatureFrame frame, ObjProp prop, PoseStack poseStack, MultiBufferSource buffers, Style style) {
        int joint = frame.pointJoint(prop.point());
        Matrix4f point = frame.pointMatrix(prop.point());
        if (joint < 0 || point == null || !frame.visible(joint)) return;
        ObjModel model = ObjModels.get(prop.model());
        if (model.isEmpty()) return;
        poseStack.pushPose();
        Poses.mulPose(poseStack, point);
        poseStack.scale(16.0F, 16.0F, 16.0F);
        poseStack.translate(prop.offset().x() / 16.0F, prop.offset().y() / 16.0F, prop.offset().z() / 16.0F);
        poseStack.mulPose(new Quaternionf().rotationZYX(prop.rotation().z() * Mth.DEG_TO_RAD, prop.rotation().y() * Mth.DEG_TO_RAD, prop.rotation().x() * Mth.DEG_TO_RAD));
        poseStack.scale(prop.scale(), prop.scale(), prop.scale());
        RenderType type = style.ghost ? RenderType.itemEntityTranslucentCull(prop.texture()) : prop.material().renderType(prop.texture());
        int light = frame.glows(joint) || !prop.material().lit() ? LightTexture.FULL_BRIGHT : style.light;
        int tint = frame.tint(joint);
        int argb = tint == -1 ? style.color : FastColor.ARGB32.multiply(style.color, tint);
        if (prop.part() != null) {
            ObjModel.Part part = model.part(prop.part());
            if (part != null) part.submit(poseStack, buffers, type, light, style.overlay, argb);
        } else {
            model.submit(poseStack, buffers, type, light, style.overlay, argb);
        }
        poseStack.popPose();
    }

    // A colour's red, green and blue scaled, its alpha kept.
    private static int scaleRgb(int argb, float scale) {
        return FastColor.ARGB32.color(FastColor.ARGB32.alpha(argb), (int) (FastColor.ARGB32.red(argb) * scale),
                (int) (FastColor.ARGB32.green(argb) * scale), (int) (FastColor.ARGB32.blue(argb) * scale));
    }

    /** How an armature is drawn this time: light, colour, outline and so on. Each method returns a changed copy. */
    public static final class Style {
        private int light = LightTexture.FULL_BRIGHT;
        private int overlay = OverlayTexture.NO_OVERLAY;
        private int color = -1;
        private int outlineColor;
        private boolean ghost;
        private boolean translucent;
        private boolean outlineOnly;
        private boolean foil;
        private float glow = 1.0F;
        private @Nullable IntFunction<@Nullable ResourceLocation> texture;

        private Style() {
        }

        private Style copy() {
            Style style = new Style();
            style.light = this.light;
            style.overlay = this.overlay;
            style.color = this.color;
            style.outlineColor = this.outlineColor;
            style.ghost = this.ghost;
            style.translucent = this.translucent;
            style.outlineOnly = this.outlineOnly;
            style.foil = this.foil;
            style.glow = this.glow;
            style.texture = this.texture;
            return style;
        }

        /** Drawn with this light (packed block and sky light). */
        public static Style lit(int light) {
            Style style = new Style();
            style.light = light;
            return style;
        }

        /** The overlay: the red hurt flash and the white flash, as {@code LivingEntityRenderer.getOverlayCoords} gives. */
        public Style overlay(int overlay) {
            Style style = this.copy();
            style.overlay = overlay;
            return style;
        }

        /** Multiplies every face's colour (ARGB). */
        public Style color(int argb) {
            Style style = this.copy();
            style.color = argb;
            return style;
        }

        /**
         * The glowing effect's outline colour, 0 for none. (On 1.20.1 the outline comes from the buffers the game hands
         * a glowing entity's renderer, in the team's colour; this is kept for the same API.)
         */
        public Style outline(int color) {
            Style style = this.copy();
            style.outlineColor = color;
            return style;
        }

        /** See-through, as an invisible mob looks to a spectator. Use with a faint {@link #color}. */
        public Style ghost() {
            Style style = this.copy();
            style.ghost = true;
            return style;
        }

        /**
         * Its lit parts blended with what is behind them (a ghost, a hallucination, something fading), so the alpha of
         * its {@link #color} shows; glowing parts are drawn as before.
         */
        public Style translucent() {
            Style style = this.copy();
            style.translucent = true;
            return style;
        }

        /** Only the glowing outline, as an invisible glowing mob. */
        public Style outlineOnly() {
            Style style = this.copy();
            style.outlineOnly = true;
            return style;
        }

        /** How bright the {@code _glow} textures are drawn: 1 as they are, 0 not at all (eyes that are shut, a lamp out). */
        public Style glow(float strength) {
            Style style = this.copy();
            style.glow = Math.max(0.0F, Math.min(1.0F, strength));
            return style;
        }

        /** The enchantment shimmer on top. */
        public Style foil(boolean foil) {
            Style style = this.copy();
            style.foil = foil;
            return style;
        }

        /** Swaps textures: given a texture slot, the texture to use instead, or null to keep the armature's. */
        public Style texture(@Nullable IntFunction<@Nullable ResourceLocation> texture) {
            Style style = this.copy();
            style.texture = texture;
            return style;
        }
    }
}
