package net.ixdarklord.coolcatcanvas.api.client.armature.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.coolcatcanvas.api.client.armature.Animator;
import net.ixdarklord.coolcatcanvas.api.client.armature.BakedArmature;
import net.ixdarklord.coolcatcanvas.api.client.armature.Armature;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureDraw;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureFrame;
import net.ixdarklord.coolcatcanvas.internal.client.GameClock;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Draws a block entity with an armature, standing on the bottom of its block and turned to the block's {@code facing} if it
 * has one (the model's front, north in Blockbench, toward that side). Blockbench "Java Block" models, whose origin is
 * a corner, are moved to fit by themselves. Give the block {@code RenderShape.INVISIBLE} so only the armature shows.
 * <pre>{@code
 * BlockEntityRendererRegistry.register(MyBlocks.TURRET_ENTITY, ArmatureBlockRenderer.of(Armature.of("mymod", "turret"), renderer -> renderer
 *         .animate(animator -> {
 *             animator.lookAt("barrel").range(12).smoothing(0.3F);
 *             animator.layer("base").drive(ctx -> ctx.<TurretEntity>owner().isFiring() ? FIRE : IDLE);
 *         })));
 * }</pre>
 */
public class ArmatureBlockRenderer<T extends BlockEntity> implements BlockEntityRenderer<T, ArmatureBlockRenderer.State> {
    private final ArmatureRenderSettings<T> settings;
    private boolean facing = true;
    private int viewDistance = 64;

    public ArmatureBlockRenderer(BlockEntityRendererProvider.Context context, Armature armature) {
        this.settings = new ArmatureRenderSettings<>(armature);
    }

    public static <T extends BlockEntity> BlockEntityRendererProvider<T, State> of(Armature armature, Consumer<ArmatureBlockRenderer<T>> configure) {
        return context -> {
            ArmatureBlockRenderer<T> renderer = new ArmatureBlockRenderer<>(context, armature);
            configure.accept(renderer);
            return renderer;
        };
    }

    public static <T extends BlockEntity> BlockEntityRendererProvider<T, State> of(Armature armature) {
        return of(armature, renderer -> {});
    }

    public Armature armature() {
        return this.settings.armature;
    }

    public ArmatureBlockRenderer<T> animate(Consumer<Animator> setup) {
        this.settings.setups.add(setup);
        return this;
    }

    public ArmatureBlockRenderer<T> texture(Function<T, @Nullable Identifier> texture) {
        this.settings.texture = texture;
        return this;
    }

    public ArmatureBlockRenderer<T> scale(float scale) {
        this.settings.scale = scale;
        return this;
    }

    /** Whether the model turns to the block's {@code facing}; on by default. */
    public ArmatureBlockRenderer<T> facing(boolean facing) {
        this.facing = facing;
        return this;
    }

    /** How far away, in blocks, it is still drawn; 64 by default. */
    public ArmatureBlockRenderer<T> viewDistance(int blocks) {
        this.viewDistance = blocks;
        return this;
    }

    public ArmatureBlockRenderer<T> attach(String point, ArmatureAttachment attachment) {
        this.settings.attachments.add(new ArmatureRenderSettings.Attached(point, attachment));
        return this;
    }

    /** Sets up a new animator, after the {@link #animate} setups; for subclasses. */
    protected void setupAnimator(Animator animator, T blockEntity) {
    }

    /** Which way the model faces; by default the block's {@code facing}, or north. */
    protected Direction facing(T blockEntity, BlockState state) {
        if (!this.facing) return Direction.NORTH;
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        if (state.hasProperty(BlockStateProperties.FACING)) return state.getValue(BlockStateProperties.FACING);
        return Direction.NORTH;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T blockEntity, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(blockEntity, state, breakProgress);
        state.facing = this.facing(blockEntity, blockEntity.getBlockState());
        state.modelScale = this.settings.scale;
        Animator animator = this.settings.animator(blockEntity, a -> this.setupAnimator(a, blockEntity));
        BakedArmature armature = this.settings.armature.get();
        Matrix4f modelToWorld = new Matrix4f().translation(blockEntity.getBlockPos().getX(), blockEntity.getBlockPos().getY(), blockEntity.getBlockPos().getZ());
        place(modelToWorld, state);
        modelToWorld.mul(ArmatureDraw.modelTransform(armature));
        state.frame = animator.update(GameClock.seconds(), partialTicks, modelToWorld);
        state.texture = this.settings.texture != null ? this.settings.texture.apply(blockEntity) : null;
    }

    // From the block's corner to the model's origin: the middle of the floor, turned to face.
    private static void place(Matrix4f matrix, State state) {
        matrix.translate(0.5F, 0.5F, 0.5F);
        switch (state.facing) {
            case UP -> matrix.rotateX(Mth.HALF_PI);
            case DOWN -> matrix.rotateX(-Mth.HALF_PI);
            default -> matrix.rotateY((180.0F - state.facing.toYRot()) * Mth.DEG_TO_RAD);
        }
        matrix.translate(0.0F, -0.5F, 0.0F);
        matrix.scale(state.modelScale);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.frame == null) return;
        poseStack.pushPose();
        Matrix4f placement = new Matrix4f();
        place(placement, state);
        poseStack.mulPose(placement);
        Identifier texture = state.texture;
        ArmatureDraw.submit(state.frame, poseStack, collector, ArmatureDraw.Style.lit(state.lightCoords)
                .crumbling(state.breakProgress)
                .texture(texture == null ? null : slot -> slot == 0 ? texture : null));
        this.settings.submitAttachments(state.frame, poseStack, collector, state.lightCoords);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        // A model reaching well past its block is drawn even when the block itself is off screen.
        AABB bounds = this.settings.armature.get().bounds();
        float reach = 16.0F * 1.5F / Math.max(0.01F, this.settings.scale);
        return bounds.minX < -reach || bounds.maxX > reach || bounds.minZ < -reach || bounds.maxZ > reach || bounds.maxY > reach * 1.5F;
    }

    @Override
    public int getViewDistance() {
        return this.viewDistance;
    }

    /** What an armature-driven block entity is drawn from, each frame. */
    public static class State extends BlockEntityRenderState {
        public @Nullable ArmatureFrame frame;
        public @Nullable Identifier texture;
        public Direction facing = Direction.NORTH;
        public float modelScale = 1.0F;
    }
}
