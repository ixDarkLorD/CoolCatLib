package net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla;

import com.google.common.collect.MapMaker;
import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.coolcatcanvas.api.client.armature.Animator;
import net.ixdarklord.coolcatcanvas.api.client.armature.Armature;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureDraw;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureFrame;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmaturePose;
import net.ixdarklord.coolcatcanvas.api.client.armature.BakedArmature;
import net.ixdarklord.coolcatcanvas.api.client.armature.render.ArmatureMobRenderer;
import net.ixdarklord.coolcatcanvas.api.client.armature.vanilla.OverlapRule;
import net.ixdarklord.coolcatcanvas.api.client.armature.vanilla.VanillaAttachment;
import net.ixdarklord.coolcatcanvas.api.client.armature.vanilla.VanillaModel;
import net.ixdarklord.coolcatcanvas.api.client.armature.vanilla.VanillaModels;
import net.ixdarklord.coolcatcanvas.api.client.armature.vanilla.VanillaPose;
import net.ixdarklord.coolcatcanvas.internal.client.GameClock;
import net.ixdarklord.coolcatcanvas.internal.client.armature.ArmatureInstances;
import net.ixdarklord.coolcatcanvas.internal.integration.sharedpose.PlayerPoseOverlap;
import net.ixdarklord.coolcatcore.api.platform.Platform;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Armatures on vanilla models ({@link VanillaModels}). While an entity is extracted, its animator and attachments move
 * on; each time vanilla poses one of its models (the body for its layers, then each model drawn: body, armor), the
 * clips and changes go on top, by part name. A model touched this way is put back before vanilla poses it again, as
 * models are shared by every entity of the kind.
 *
 * <p>Axes: vanilla models are upside down and mirrored against Blockbench (X and Y flipped), so a pose's X and Y turns
 * and positions change sign; a keyframe's own values then read the same on both, as in Bedrock.</p>
 *
 * <p>Posing runs several times a frame for every such entity, so it does no lookups by name and allocates nothing:
 * joints are matched to parts once per armature ({@link ModelParts.Binding}), and each model keeps one record of what
 * was done to it ({@link Touch}), reused every time.</p>
 */
public final class VanillaArmatures {
    private static final Map<LivingEntity, Animator[]> ATTACHED = new MapMaker().weakKeys().makeMap();
    private static final Map<Model<?>, Touch> TOUCHED = new WeakHashMap<>();
    private static final ArmatureFrame[] NO_FRAMES = new ArmatureFrame[0];
    private static final Identifier[] NO_TEXTURES = new Identifier[0];
    // The height the whole model turns around when a clip moves its root: the middle of a player.
    private static final float ROOT_PIVOT = 12.0F;
    private static final float[] VALUES = new float[ArmaturePose.VALUES];
    private static final Quaternionf TURN = new Quaternionf();

    private VanillaArmatures() {
    }

    /** Other animation mods known without registering: the player animation mod Better Combat uses, when loaded. */
    public static void init() {
        if (Platform.isModLoaded(PlayerPoseOverlap.MOD_ID)) VanillaModels.registerOverlap(new PlayerPoseOverlap());
    }

    // ---- Extracting ----

    public static void extract(LivingEntityRenderer<?, ?, ?> renderer, LivingEntity entity, LivingEntityRenderState state, float partialTick) {
        VanillaStateHolder holder = (VanillaStateHolder) state;
        holder.coolcatlib$setVanilla(null);
        if (renderer instanceof ArmatureMobRenderer<?>) return;
        VanillaModel model = VanillaModels.find(entity.getType());
        if (model == null || !model.test(entity)) return;

        double clock = GameClock.seconds();
        Matrix4f placement = new Matrix4f().translation((float) state.x, (float) state.y, (float) state.z)
                .rotateY((180.0F - state.bodyRot) * Mth.DEG_TO_RAD)
                .scale(state.scale);
        Animator animator = animator(entity, model);
        animator.update(clock, partialTick, new Matrix4f(placement).scale(1.0F / 16.0F));

        List<VanillaAttachment> attachments = model.attachments();
        ArmatureFrame[] frames = NO_FRAMES;
        Identifier[] textures = NO_TEXTURES;
        if (!attachments.isEmpty()) {
            frames = new ArmatureFrame[attachments.size()];
            textures = new Identifier[attachments.size()];
            Animator[] worn = ATTACHED.get(entity);
            if (worn == null || worn.length != attachments.size()) {
                worn = worn == null ? new Animator[attachments.size()] : Arrays.copyOf(worn, attachments.size());
                ATTACHED.put(entity, worn);
            }
            ModelParts parts = ModelParts.of(renderer.getModel());
            for (int i = 0; i < attachments.size(); i++) {
                VanillaAttachment attachment = attachments.get(i);
                Vector3f pivot = parts.restPivot(attachment.part());
                if (pivot == null || !attachment.test(entity)) continue;
                if (worn[i] == null || !worn[i].armature().equals(attachment.armature())) {
                    Animator created = new Animator(attachment.armature(), entity);
                    attachment.setups().forEach(setup -> setup.accept(created));
                    worn[i] = created;
                }
                // Roughly where it is in the world, for springs: the part at rest (a vanilla model stands upside down
                // a block and a half up), then the attachment's own place.
                BakedArmature armature = attachment.armature().get();
                Matrix4f modelToWorld = new Matrix4f(placement).translate(-pivot.x / 16.0F, 1.501F - pivot.y / 16.0F, pivot.z / 16.0F);
                attachment.transform(modelToWorld).mul(ArmatureDraw.modelTransform(armature));
                frames[i] = worn[i].update(clock, partialTick, modelToWorld);
                textures[i] = attachment.texture(entity);
            }
        }
        boolean mirrored = model.mirrorsLeftHanded() && entity.getMainArm() == HumanoidArm.LEFT;
        holder.coolcatlib$setVanilla(new VanillaState(model, animator, frames, textures, mirrored));
    }

    /**
     * The entity's animator for its vanilla model, made now if it has none: commands for a player seen only in first
     * person (never drawn in the world) still find it. Null if its kind has no {@code VanillaModel}.
     */
    public static @Nullable Animator animator(LivingEntity entity) {
        VanillaModel model = VanillaModels.find(entity.getType());
        return model == null ? null : animator(entity, model);
    }

    private static Animator animator(LivingEntity entity, VanillaModel model) {
        Armature source = model.animations() != null ? model.animations() : Armature.NONE;
        return ArmatureInstances.get(entity, source, a -> model.setups().forEach(setup -> setup.accept(a)));
    }

    /** The animators of the armatures an entity wears, for server commands aimed at it. */
    public static List<Animator> attachments(Object owner) {
        Animator[] worn = owner instanceof LivingEntity living ? ATTACHED.get(living) : null;
        if (worn == null) return List.of();
        List<Animator> out = new ArrayList<>(worn.length);
        for (Animator animator : worn) {
            if (animator != null) out.add(animator);
        }
        return out;
    }

    // ---- The whole model ----

    /**
     * Moves the whole model by the clips' root joint ({@code root}, or {@code body} with torso joint names): somersaults,
     * sitting, lying down. Called where vanilla scales the model, in its upside-down axes.
     */
    public static void transformRoot(LivingEntityRenderState state, PoseStack poseStack) {
        VanillaState vanilla = ((VanillaStateHolder) state).coolcatlib$vanilla();
        if (vanilla == null || VanillaState.skipped(state)) return;
        ArmaturePose pose = vanilla.animator().pose();
        if (pose == null || pose.armature().jointCount() == 0) return;
        BakedArmature armature = pose.armature();
        // Any model's binding gives the same root; the vanilla one is at hand through the state's model lookup below.
        int root = rootJoint(armature, vanilla.model().usesTorsoJointNames());
        if (root < 0) return;
        pose.read(root, VALUES);
        float mirror = vanilla.mirrored() ? -1.0F : 1.0F;
        // Back to Blockbench's axes (Y up) for the move, around the middle of the model, then upside down again.
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(VALUES[3] * mirror / 16.0F, (VALUES[4] + ROOT_PIVOT) / 16.0F, VALUES[5] / 16.0F);
        poseStack.rotate(TURN.rotationZYX(VALUES[2] * mirror, VALUES[1] * mirror, VALUES[0]));
        poseStack.scale(VALUES[6], VALUES[7], VALUES[8]);
        poseStack.translate(0.0F, -ROOT_PIVOT / 16.0F, 0.0F);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
    }

    // The root joint's index, kept per armature and naming.
    private static final Map<BakedArmature, int[]> ROOTS = new WeakHashMap<>();

    private static int rootJoint(BakedArmature armature, boolean torsoJointNames) {
        int[] roots = ROOTS.computeIfAbsent(armature, a -> {
            int plain = -1, torso = -1;
            for (int j = 0; j < a.jointCount(); j++) {
                String name = ModelParts.normalize(a.joint(j).name());
                if (name.equals("root")) {
                    if (plain < 0) plain = j;
                    if (torso < 0) torso = j;
                } else if (name.equals("body") && torso < 0) {
                    torso = j;
                }
            }
            return new int[]{plain, torso};
        });
        return roots[torsoJointNames ? 1 : 0];
    }

    // ---- Posing ----

    /** Before vanilla poses a model: undo what was done to it for the last entity. */
    public static void beforePose(Model<?> model) {
        Touch touch = TOUCHED.get(model);
        if (touch == null || !touch.active) return;
        touch.active = false;
        model.resetPose();
        touch.restore();
    }

    /** After vanilla posed a model for a state: the entity's clips and changes on top. */
    public static void afterPose(Model<?> model, Object state) {
        if (!(state instanceof VanillaStateHolder holder) || !(state instanceof LivingEntityRenderState living)) return;
        VanillaState vanilla = holder.coolcatlib$vanilla();
        if (vanilla == null || VanillaState.skipped(living)) return;
        ModelParts parts = ModelParts.of(model);
        Touch touch = TOUCHED.computeIfAbsent(model, m -> new Touch());
        touch.active = true;

        ArmaturePose pose = vanilla.animator().pose();
        if (pose != null && pose.armature().jointCount() > 0) {
            VanillaModel settings = vanilla.model();
            ModelParts.Binding binding = parts.binding(pose.armature(), settings.usesTorsoJointNames());
            boolean mirrored = vanilla.mirrored();
            ModelPart[] targets = mirrored ? binding.mirrored : binding.parts;
            String[] names = mirrored ? binding.mirroredNames : binding.names;
            OverlapRule policy = settings.overlapRule();
            Set<String> external = policy == OverlapRule.OVERRIDE ? Set.of() : vanilla.overlappingParts(living);
            for (int j = 0; j < targets.length; j++) {
                ModelPart part = targets[j];
                if (part == null) continue;
                pose.read(j, VALUES);
                // A part another mod (Better Combat's swing, say) is moving: leave it, or only add to it.
                if (!external.isEmpty() && external.contains(names[j])) {
                    if (policy == OverlapRule.YIELD) continue;
                    VALUES[12] = 0.0F;
                    VALUES[13] = 0.0F;
                    VALUES[15] = 0.0F;
                }
                if (mirrored) {
                    VALUES[1] = -VALUES[1];
                    VALUES[2] = -VALUES[2];
                    VALUES[3] = -VALUES[3];
                    VALUES[10] = -VALUES[10];
                }
                apply(part, VALUES, touch);
            }
        }
        List<VanillaPose.Modifier> modifiers = vanilla.model().modifiers();
        if (!modifiers.isEmpty()) {
            Posing posing = new Posing(parts, touch, living);
            for (VanillaPose.Modifier modifier : modifiers) modifier.modify(posing, vanilla.animator().context());
        }
    }

    // One joint's values onto a part: what override layers cover is taken from vanilla back to rest first, then the
    // movement goes on. The pose is in Blockbench's axes; vanilla's has X and Y the other way. A bend goes on the part
    // and the layers made as its children (sleeves, pants), over its own middle.
    private static void apply(ModelPart part, float[] v, Touch touch) {
        PartPose rest = part.getInitialPose();
        float cr = v[12], cp = v[13];
        if (cr > 0.0F) {
            part.xRot = Mth.lerp(cr, part.xRot, rest.xRot());
            part.yRot = Mth.lerp(cr, part.yRot, rest.yRot());
            part.zRot = Mth.lerp(cr, part.zRot, rest.zRot());
        }
        part.xRot -= v[0];
        part.yRot -= v[1];
        part.zRot += v[2];
        if (cp > 0.0F) {
            part.x = Mth.lerp(cp, part.x, rest.x());
            part.y = Mth.lerp(cp, part.y, rest.y());
            part.z = Mth.lerp(cp, part.z, rest.z());
        }
        part.x -= v[3];
        part.y -= v[4];
        part.z += v[5];
        part.xScale *= v[6];
        part.yScale *= v[7];
        part.zScale *= v[8];
        if (v[9] != 0.0F) touch.bend(part, v[9], v[10]);
        if (v[16] > 0.0F) touch.hide(part);
    }

    /**
     * What was done to one model since vanilla last posed it: the parts hidden, and whether each was shown before.
     * One per model, reused, growing only when a model has more parts hidden at once than ever before.
     */
    static final class Touch {
        private ModelPart[] parts = new ModelPart[4];
        private boolean[] wasVisible = new boolean[4];
        private int count;
        private final List<ModelPart> bent = new ArrayList<>();
        boolean active;

        /** Bends a part (and its child layers) over the middle of its length; straightened again by {@link #restore}. */
        void bend(ModelPart part, float amount, float direction) {
            float[] zone = LimbBend.zone(part);
            if (zone[1] > zone[0]) LimbBend.bendTree(part, amount, direction, zone[0], zone[1], this.bent);
        }

        void hide(ModelPart part) {
            if (!part.visible) return;
            if (this.count == this.parts.length) {
                this.parts = Arrays.copyOf(this.parts, this.count * 2);
                this.wasVisible = Arrays.copyOf(this.wasVisible, this.count * 2);
            }
            this.parts[this.count] = part;
            this.wasVisible[this.count++] = true;
            part.visible = false;
        }

        void restore() {
            for (int i = 0; i < this.bent.size(); i++) BendablePart.of(this.bent.get(i)).coolcatlib$setBend(0.0F, 0.0F, 0.0F, 0.0F);
            this.bent.clear();
            for (int i = 0; i < this.count; i++) {
                this.parts[i].visible = this.wasVisible[i];
                this.parts[i] = null;
            }
            this.count = 0;
        }
    }

    // The VanillaPose handed to modifiers: degrees and pixels as a Blockbench keyframe says them.
    private record Posing(ModelParts parts, Touch touch, LivingEntityRenderState state) implements VanillaPose {
        @Override
        public @Nullable ModelPart part(String name) {
            return this.parts.part(name);
        }

        @Override
        public VanillaPose rotate(String name, float x, float y, float z) {
            ModelPart part = this.parts.part(name);
            if (part != null) {
                part.xRot += x * Mth.DEG_TO_RAD;
                part.yRot += y * Mth.DEG_TO_RAD;
                part.zRot += z * Mth.DEG_TO_RAD;
            }
            return this;
        }

        @Override
        public VanillaPose setRotation(String name, float x, float y, float z) {
            ModelPart part = this.parts.part(name);
            if (part != null) {
                PartPose rest = part.getInitialPose();
                part.xRot = rest.xRot() + x * Mth.DEG_TO_RAD;
                part.yRot = rest.yRot() + y * Mth.DEG_TO_RAD;
                part.zRot = rest.zRot() + z * Mth.DEG_TO_RAD;
            }
            return this;
        }

        @Override
        public VanillaPose translate(String name, float x, float y, float z) {
            ModelPart part = this.parts.part(name);
            if (part != null) {
                part.x += x;
                part.y -= y;
                part.z += z;
            }
            return this;
        }

        @Override
        public VanillaPose scale(String name, float x, float y, float z) {
            ModelPart part = this.parts.part(name);
            if (part != null) {
                part.xScale *= x;
                part.yScale *= y;
                part.zScale *= z;
            }
            return this;
        }

        @Override
        public VanillaPose bend(String name, float degrees, float direction) {
            ModelPart part = this.parts.part(name);
            if (part != null) this.touch.bend(part, BendablePart.of(part).coolcatlib$bendAmount() + degrees * Mth.DEG_TO_RAD, direction * Mth.DEG_TO_RAD);
            return this;
        }

        @Override
        public VanillaPose hide(String name) {
            ModelPart part = this.parts.part(name);
            if (part != null) this.touch.hide(part);
            return this;
        }
    }

    // ---- Attachments, drawn by VanillaArmatureLayer ----

    static @Nullable List<ModelPart> path(Model<?> model, String part) {
        return ModelParts.of(model).path(part);
    }
}
