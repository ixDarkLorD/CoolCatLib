package net.ixdarklord.coolcatcanvas.api.client.armature;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.coolcatcanvas.internal.client.armature.ArmatureMesh;
import net.ixdarklord.coolcatcanvas.internal.client.armature.render.ArmatureModelAdapter;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import org.jetbrains.annotations.Nullable;
import net.ixdarklord.coolcatcanvas.api.client.model.ObjModel;
import net.ixdarklord.coolcatcanvas.api.client.model.ObjModels;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.function.IntFunction;

/**
 * Draws a posed armature. CoolCatLib's armature renderers use it; call it yourself to draw an armature inside a renderer of your own.
 * The pose stack should be where the model's origin goes (an entity's feet, a block's base centre), turned the way
 * the model faces.
 * <pre>{@code
 * ArmatureFrame frame = animator.update(clock, partialTick, modelToWorld);
 * ArmatureDraw.submit(frame, poseStack, collector, ArmatureDraw.Style.lit(light));
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

    public static void submit(ArmatureFrame frame, PoseStack poseStack, SubmitNodeCollector collector, Style style) {
        BakedArmature armature = frame.armature();
        ArmatureMesh.Group[] groups = armature.mesh().groups();
        if (groups.length == 0 && frame.props().isEmpty()) return;
        poseStack.pushPose();
        poseStack.mulPose(modelTransform(armature));
        for (int g = 0; g < groups.length; g++) {
            ArmatureMesh.Group group = groups[g];
            ArmatureModelAdapter adapter = ArmatureModelAdapter.forGroup(g);
            Identifier texture = style.texture != null ? style.texture.apply(group.texture()) : null;
            if (texture == null) texture = armature.texture(group.texture());
            RenderType type;
            if (style.outlineOnly) type = RenderTypes.outline(texture);
            else if (style.ghost) type = RenderTypes.entityTranslucentCullItemTarget(texture);
            else if (style.translucent && group.material().lit()) type = RenderTypes.entityTranslucent(texture);
            else type = group.material().renderType(texture);
            int light = group.material().lit() ? style.light : LightCoordsUtil.FULL_BRIGHT;
            collector.submitModel(adapter, frame, poseStack, type, light, style.overlay, style.color, null, style.outlineColor, style.crumbling);
            if (style.outlineOnly) continue;
            Identifier glow = armature.glowTexture(group.texture());
            if (glow != null && style.glow > 0.0F) {
                int color = style.glow >= 1.0F ? style.color : ARGB.scaleRGB(style.color, style.glow);
                collector.submitModel(adapter, frame, poseStack, RenderTypes.eyes(glow), LightCoordsUtil.FULL_BRIGHT, style.overlay, color, null, 0, null);
            }
            if (style.foil) collector.submitModel(adapter, frame, poseStack, RenderTypes.entityGlint(), light, style.overlay, -1, null, 0, null);
        }
        if (!style.outlineOnly) {
            for (ObjProp prop : frame.props()) submitProp(frame, prop, poseStack, collector, style);
        }
        poseStack.popPose();
    }

    // An .obj prop at its point, in model pixels until the point, then blocks; hidden, lit and tinted with its joint.
    private static void submitProp(ArmatureFrame frame, ObjProp prop, PoseStack poseStack, SubmitNodeCollector collector, Style style) {
        int joint = frame.pointJoint(prop.point());
        Matrix4f point = frame.pointMatrix(prop.point());
        if (joint < 0 || point == null || !frame.visible(joint)) return;
        ObjModel model = ObjModels.get(prop.model());
        if (model.isEmpty()) return;
        poseStack.pushPose();
        poseStack.mulPose(point);
        poseStack.scale(16.0F, 16.0F, 16.0F);
        poseStack.translate(prop.offset().x() / 16.0F, prop.offset().y() / 16.0F, prop.offset().z() / 16.0F);
        poseStack.mulPose(new Quaternionf().rotationZYX(prop.rotation().z() * Mth.DEG_TO_RAD, prop.rotation().y() * Mth.DEG_TO_RAD, prop.rotation().x() * Mth.DEG_TO_RAD));
        poseStack.scale(prop.scale(), prop.scale(), prop.scale());
        RenderType type = style.ghost ? RenderTypes.entityTranslucentCullItemTarget(prop.texture()) : prop.material().renderType(prop.texture());
        int light = frame.glows(joint) || !prop.material().lit() ? LightCoordsUtil.FULL_BRIGHT : style.light;
        int tint = frame.tint(joint);
        int argb = tint == -1 ? style.color : ARGB.multiply(style.color, tint);
        if (prop.part() != null) {
            ObjModel.Part part = model.part(prop.part());
            if (part != null) part.submit(poseStack, collector, type, light, style.overlay, argb);
        } else {
            model.submit(poseStack, collector, type, light, style.overlay, argb);
        }
        poseStack.popPose();
    }

    /** How an armature is drawn this time: light, colour, outline and so on. Each method returns a changed copy. */
    public static final class Style {
        private int light = LightCoordsUtil.FULL_BRIGHT;
        private int overlay = OverlayTexture.NO_OVERLAY;
        private int color = -1;
        private int outlineColor;
        private boolean ghost;
        private boolean translucent;
        private boolean outlineOnly;
        private boolean foil;
        private float glow = 1.0F;
        private @Nullable ModelFeatureRenderer.CrumblingOverlay crumbling;
        private @Nullable IntFunction<@Nullable Identifier> texture;

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
            style.crumbling = this.crumbling;
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

        /** The glowing effect's outline colour, 0 for none. */
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

        /** The block-breaking cracks, as a block entity renderer is given them. */
        public Style crumbling(@Nullable ModelFeatureRenderer.CrumblingOverlay crumbling) {
            Style style = this.copy();
            style.crumbling = crumbling;
            return style;
        }

        /** Swaps textures: given a texture slot, the texture to use instead, or null to keep the armature's. */
        public Style texture(@Nullable IntFunction<@Nullable Identifier> texture) {
            Style style = this.copy();
            style.texture = texture;
            return style;
        }
    }
}
