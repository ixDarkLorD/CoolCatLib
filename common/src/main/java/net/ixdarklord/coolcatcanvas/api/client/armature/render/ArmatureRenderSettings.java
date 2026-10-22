package net.ixdarklord.coolcatcanvas.api.client.armature.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.coolcatcanvas.api.client.armature.Animator;
import net.ixdarklord.coolcatcanvas.api.client.armature.Armature;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureFrame;
import net.ixdarklord.coolcatcanvas.internal.client.armature.ArmatureInstances;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** What every armature renderer is set up with: the armature, animator setup, a texture swap and attachments. */
final class ArmatureRenderSettings<T> {
    final Armature armature;
    final List<Consumer<Animator>> setups = new ArrayList<>();
    final List<Attached> attachments = new ArrayList<>();
    @Nullable Function<T, @Nullable Identifier> texture;
    /** Picks the armature per owner (variants with bodies of their own); null draws every one with {@link #armature}. */
    @Nullable Function<T, @Nullable Armature> picker;
    float scale = 1.0F;

    ArmatureRenderSettings(Armature armature) {
        this.armature = armature;
    }

    /** The armature an owner is drawn with: its pick, or the renderer's own. */
    Armature armatureFor(T owner) {
        Armature picked = this.picker != null ? this.picker.apply(owner) : null;
        return picked != null ? picked : this.armature;
    }

    Animator animator(Object owner, Consumer<Animator> extra) {
        return this.animator(owner, this.armature, extra);
    }

    /** The owner's animator for the armature it is drawn with now (a new one, set up afresh, when that changes). */
    Animator animator(Object owner, Armature armature, Consumer<Animator> extra) {
        return ArmatureInstances.get(owner, armature, animator -> {
            for (Consumer<Animator> setup : this.setups) setup.accept(animator);
            extra.accept(animator);
        });
    }

    void submitAttachments(ArmatureFrame frame, PoseStack poseStack, SubmitNodeCollector collector, int light) {
        for (Attached attached : this.attachments) {
            poseStack.pushPose();
            if (ArmaturePoints.moveTo(frame, attached.point, poseStack)) attached.attachment.submit(frame, poseStack, collector, light);
            poseStack.popPose();
        }
    }

    record Attached(String point, ArmatureAttachment attachment) {
    }
}
