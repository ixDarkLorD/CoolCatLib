package net.ixdarklord.coolcatcanvas.api.client.armature.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.ixdarklord.coolcatcanvas.api.client.armature.Animator;
import net.ixdarklord.coolcatcanvas.api.client.armature.BakedArmature;
import net.ixdarklord.coolcatcanvas.api.client.armature.Armature;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureDraw;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureFrame;
import net.ixdarklord.coolcatcanvas.internal.client.GameClock;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Draws any entity with an armature: projectiles, vehicles, effects, display props. The model turns with the entity's yaw
 * and, if asked, its pitch (for things that fly the way they face). For living entities use {@link ArmatureMobRenderer}.
 * <pre>{@code
 * EntityRendererRegistry.register(MyEntities.BOLT, ArmatureEntityRenderer.of(Armature.of("mymod", "bolt"), renderer -> renderer
 *         .pitch(true)
 *         .animate(animator -> animator.play(Motion.loop("spin")))));
 * }</pre>
 */
public class ArmatureEntityRenderer<T extends Entity> extends EntityRenderer<T, ArmatureEntityRenderer.State> {
    private final ArmatureRenderSettings<T> settings;
    private boolean pitch;

    public ArmatureEntityRenderer(EntityRendererProvider.Context context, Armature armature) {
        super(context);
        this.settings = new ArmatureRenderSettings<>(armature);
    }

    public static <T extends Entity> EntityRendererProvider<T> of(Armature armature, Consumer<ArmatureEntityRenderer<T>> configure) {
        return context -> {
            ArmatureEntityRenderer<T> renderer = new ArmatureEntityRenderer<>(context, armature);
            configure.accept(renderer);
            return renderer;
        };
    }

    public Armature armature() {
        return this.settings.armature;
    }

    public ArmatureEntityRenderer<T> animate(Consumer<Animator> setup) {
        this.settings.setups.add(setup);
        return this;
    }

    public ArmatureEntityRenderer<T> texture(Function<T, @Nullable Identifier> texture) {
        this.settings.texture = texture;
        return this;
    }

    public ArmatureEntityRenderer<T> scale(float scale) {
        this.settings.scale = scale;
        return this;
    }

    /** Tilts the model with the entity's pitch too, as arrows do. */
    public ArmatureEntityRenderer<T> pitch(boolean pitch) {
        this.pitch = pitch;
        return this;
    }

    /** The size of the ground shadow, in blocks; 0 (the default) for none. */
    public ArmatureEntityRenderer<T> shadow(float radius) {
        this.shadowRadius = radius;
        return this;
    }

    public ArmatureEntityRenderer<T> attach(String point, ArmatureAttachment attachment) {
        this.settings.attachments.add(new ArmatureRenderSettings.Attached(point, attachment));
        return this;
    }

    /** Sets up a new animator, after the {@link #animate} setups; for subclasses. */
    protected void setupAnimator(Animator animator, T entity) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yaw = entity.getYRot(partialTicks);
        state.pitch = this.pitch ? entity.getXRot(partialTicks) : 0.0F;
        state.modelScale = this.settings.scale;
        Animator animator = this.settings.animator(entity, a -> this.setupAnimator(a, entity));
        BakedArmature armature = this.settings.armature.get();
        Matrix4f modelToWorld = new Matrix4f().translation((float) state.x, (float) state.y, (float) state.z);
        this.place(modelToWorld, state);
        modelToWorld.mul(ArmatureDraw.modelTransform(armature));
        state.frame = animator.update(GameClock.seconds(), partialTicks, modelToWorld);
        state.texture = this.settings.texture != null ? this.settings.texture.apply(entity) : null;
    }

    private void place(Matrix4f matrix, State state) {
        matrix.rotateY((180.0F - state.yaw) * Mth.DEG_TO_RAD);
        if (state.pitch != 0.0F) matrix.rotateX(-state.pitch * Mth.DEG_TO_RAD);
        matrix.scale(state.modelScale);
    }

    @Override
    protected AABB getBoundingBoxForCulling(T entity) {
        AABB box = super.getBoundingBoxForCulling(entity);
        BakedArmature armature = this.settings.armature.get();
        if (armature.isEmpty()) return box;
        AABB bounds = armature.bounds();
        double reach = Math.max(Math.max(Math.abs(bounds.minX), Math.abs(bounds.maxX)), Math.max(Math.max(Math.abs(bounds.minZ), Math.abs(bounds.maxZ)), Math.max(Math.abs(bounds.minY), Math.abs(bounds.maxY))));
        reach *= armature.scale() / 16.0F * this.settings.scale;
        return box.minmax(new AABB(entity.getX() - reach, entity.getY() - reach, entity.getZ() - reach, entity.getX() + reach, entity.getY() + reach, entity.getZ() + reach));
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.frame != null && !state.isInvisible) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yaw));
            if (state.pitch != 0.0F) poseStack.mulPose(Axis.XP.rotationDegrees(-state.pitch));
            poseStack.scale(state.modelScale, state.modelScale, state.modelScale);
            Identifier texture = state.texture;
            ArmatureDraw.submit(state.frame, poseStack, collector, ArmatureDraw.Style.lit(state.lightCoords)
                    .outline(state.outlineColor)
                    .texture(texture == null ? null : slot -> slot == 0 ? texture : null));
            this.settings.submitAttachments(state.frame, poseStack, collector, state.lightCoords);
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, camera);
    }

    /** What an armature-driven entity is drawn from, each frame. */
    public static class State extends EntityRenderState {
        public @Nullable ArmatureFrame frame;
        public @Nullable Identifier texture;
        public float yaw;
        public float pitch;
        public float modelScale = 1.0F;
    }
}
