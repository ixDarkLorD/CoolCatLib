package net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla;

import net.ixdarklord.coolcatcanvas.api.client.armature.Animator;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureFrame;
import net.ixdarklord.coolcatcanvas.api.client.armature.vanilla.AnimationOverlap;
import net.ixdarklord.coolcatcanvas.api.client.armature.vanilla.VanillaAttachment;
import net.ixdarklord.coolcatcanvas.api.client.armature.vanilla.VanillaModel;
import net.ixdarklord.coolcatcanvas.api.client.armature.vanilla.VanillaModels;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * What a vanilla mob is drawn with this frame, beyond vanilla: its animator (for clips and modifiers), its
 * attachments' frames by the index of the attachment in its {@link VanillaModel} (null where one isn't worn), and,
 * found once when first needed, the parts other animation libraries are moving.
 */
public final class VanillaState {
    private final VanillaModel model;
    private final Animator animator;
    private final @Nullable ArmatureFrame[] frames;
    private final @Nullable Identifier[] textures;
    private final boolean mirrored;
    private @Nullable Set<String> external;

    public VanillaState(VanillaModel model, Animator animator, @Nullable ArmatureFrame[] frames, @Nullable Identifier[] textures, boolean mirrored) {
        this.model = model;
        this.animator = animator;
        this.frames = frames;
        this.textures = textures;
        this.mirrored = mirrored;
    }

    /** Whether clips play mirrored (a left-handed entity, with {@code mirrorLeftHanded}). */
    public boolean mirrored() {
        return this.mirrored;
    }

    public VanillaModel model() {
        return this.model;
    }

    public Animator animator() {
        return this.animator;
    }

    public @Nullable ArmatureFrame[] frames() {
        return this.frames;
    }

    public @Nullable Identifier[] textures() {
        return this.textures;
    }

    public @Nullable ArmatureFrame frame(int index) {
        return index < this.frames.length ? this.frames[index] : null;
    }

    public VanillaAttachment attachment(int index) {
        return this.model.attachments().get(index);
    }

    /** The parts (normalized names) other animation libraries are moving, asked of them once. */
    Set<String> overlappingParts(LivingEntityRenderState state) {
        if (this.external == null) {
            Set<String> parts = new HashSet<>();
            for (AnimationOverlap animator : VanillaModels.overlaps()) {
                for (String part : animator.overlappingParts(state)) parts.add(ModelParts.normalize(part));
            }
            this.external = parts;
        }
        return this.external;
    }

    /** Whether another library is drawing this for itself (a first-person pass), which clips and attachments stay out of. */
    static boolean skipped(LivingEntityRenderState state) {
        for (AnimationOverlap animator : VanillaModels.overlaps()) {
            if (animator.skipsDraw(state)) return true;
        }
        return false;
    }
}
