package net.ixdarklord.coolcatcanvas.api.client.armature.vanilla;

import net.ixdarklord.coolcatcanvas.api.client.armature.Animator;
import net.ixdarklord.coolcatcanvas.api.client.armature.Armature;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * What CoolCatLib adds to one kind of vanilla mob (or the player) without replacing its model: clips played on its
 * parts, changes to its parts, and armatures worn on them. Get it from {@link VanillaModels}; every call adds to it, so
 * several mods can add to the same mob. Set it up while the client sets up.
 * <pre>{@code
 * VanillaModels.player()
 *         .animations(Armature.of("mymod", "emotes"))        // clips by part name: head, right_arm, left_leg...
 *         .hide("hat")
 *         .pose((pose, ctx) -> { if (ctx.living().isCrouching()) pose.scale("head", 1.2F); });
 * VanillaModels.of(EntityType.ZOMBIE).attach("head", Armature.of("mymod", "party_hat")).offset(0, 8, 0);
 * }</pre>
 */
public final class VanillaModel {
    private @Nullable Armature animations;
    private final List<Consumer<Animator>> setups = new ArrayList<>();
    private final List<VanillaPose.Modifier> modifiers = new ArrayList<>();
    private final List<VanillaAttachment> attachments = new ArrayList<>();
    private final List<Predicate<LivingEntity>> filters = new ArrayList<>();
    private OverlapRule external = OverlapRule.YIELD;
    private boolean mirrorLeftHanded;
    private boolean torsoJointNames;
    private @Nullable Predicate<Animator> firstPerson;

    VanillaModel() {
    }

    /**
     * Where its clips come from: an armature (a Blockbench model, or a Bedrock {@code .animation.json} alone) whose
     * joints are named after the vanilla parts. Clips played on it ({@code VanillaModels.play},
     * {@code ArmatureControl.play}, a layer's driver) move the parts of the same names, on top of vanilla's animation:
     * a part a playing clip moves is taken over from vanilla, the rest walk and look around as usual.
     */
    public VanillaModel animations(Armature armature) {
        this.animations = armature;
        return this;
    }

    /** Sets up each entity's animator (layers, drivers, listeners) when it is first drawn. */
    public VanillaModel animate(Consumer<Animator> setup) {
        this.setups.add(setup);
        return this;
    }

    /** Changes its parts every time it is drawn, after vanilla and the clips. */
    public VanillaModel pose(VanillaPose.Modifier modifier) {
        this.modifiers.add(modifier);
        return this;
    }

    /** Hides parts (and what hangs from them) for good. */
    public VanillaModel hide(String... parts) {
        return this.pose((pose, ctx) -> {
            for (String part : parts) pose.hide(part);
        });
    }

    /** Scales a part for good. */
    public VanillaModel scale(String part, float scale) {
        return this.pose((pose, ctx) -> pose.scale(part, scale));
    }

    /** Turns a part further for good, in degrees. */
    public VanillaModel rotate(String part, float x, float y, float z) {
        return this.pose((pose, ctx) -> pose.rotate(part, x, y, z));
    }

    /** Moves a part for good, in pixels. */
    public VanillaModel translate(String part, float x, float y, float z) {
        return this.pose((pose, ctx) -> pose.translate(part, x, y, z));
    }

    /** Wears an armature on a part; set it up through what this returns. */
    public VanillaAttachment attach(String part, Armature armature) {
        VanillaAttachment attachment = new VanillaAttachment(part, armature);
        this.attachments.add(attachment);
        return attachment;
    }

    /**
     * Applies everything here only to entities that pass, asked every frame (a cosmetic some players own, a mob with a
     * tag). With several filters, all must pass.
     */
    public VanillaModel when(Predicate<LivingEntity> filter) {
        this.filters.add(filter);
        return this;
    }

    /**
     * What its clips do on parts another mod is moving (overlapping parts, such as Better Combat's attack swings):
     * {@link OverlapRule#YIELD} by default.
     */
    public VanillaModel overlapRule(OverlapRule rule) {
        this.external = rule;
        return this;
    }

    /**
     * Mirrors its clips for left-handed entities (a left-handed player's swing comes from the left arm): left and right
     * parts swap, and turns and moves flip sideways. Off by default.
     */
    public VanillaModel mirrorLeftHanded(boolean mirror) {
        this.mirrorLeftHanded = mirror;
        return this;
    }

    /**
     * Reads its clips' joints with torso joint names, common in player emote files: {@code torso} is the body part, and
     * {@code body} moves the whole model. Without this, {@code body} is the body part and only {@code root} moves
     * the whole model.
     */
    public VanillaModel torsoJointNames() {
        this.torsoJointNames = true;
        return this;
    }

    /**
     * For the player: shows your own arms in first person posed as the clips pose them (an emote waves the arm you see),
     * with what you hold, in place of vanilla's first-person hands, while anything plays on the animator. The arms are
     * drawn in the hand pass, so they never clip into walls, and bend, follow other mods' swings and wear their
     * sleeves as in third person.
     */
    public VanillaModel firstPersonArms(boolean show) {
        this.firstPerson = show ? FirstPersonArms.WHILE_PLAYING : null;
        return this;
    }

    /** As {@link #firstPersonArms(boolean)}, shown only while this holds (asked every frame). */
    public VanillaModel firstPersonArms(Predicate<Animator> when) {
        this.firstPerson = when;
        return this;
    }

    // ---- For CoolCatLib's renderer ----

    @Nullable Predicate<Animator> firstPersonRule() {
        return this.firstPerson;
    }

    @ApiStatus.Internal
    public boolean showsFirstPersonArms(Animator animator) {
        return this.firstPerson != null && this.firstPerson.test(animator);
    }

    @ApiStatus.Internal
    public boolean mirrorsLeftHanded() {
        return this.mirrorLeftHanded;
    }

    @ApiStatus.Internal
    public boolean usesTorsoJointNames() {
        return this.torsoJointNames;
    }

    @ApiStatus.Internal
    public OverlapRule overlapRule() {
        return this.external;
    }

    @ApiStatus.Internal
    public @Nullable Armature animations() {
        return this.animations;
    }

    @ApiStatus.Internal
    public List<Consumer<Animator>> setups() {
        return Collections.unmodifiableList(this.setups);
    }

    @ApiStatus.Internal
    public List<VanillaPose.Modifier> modifiers() {
        return Collections.unmodifiableList(this.modifiers);
    }

    @ApiStatus.Internal
    public List<VanillaAttachment> attachments() {
        return Collections.unmodifiableList(this.attachments);
    }

    List<VanillaAttachment> attachmentsList() {
        return this.attachments;
    }

    @ApiStatus.Internal
    public boolean test(LivingEntity entity) {
        for (Predicate<LivingEntity> filter : this.filters) {
            if (!filter.test(entity)) return false;
        }
        return true;
    }

    // Shown while any layer has a clip playing.
    private static final class FirstPersonArms {
        static final Predicate<Animator> WHILE_PLAYING = animator -> {
            for (var layer : animator.layers()) {
                if (layer.isPlaying()) return true;
            }
            return false;
        };
    }
}
