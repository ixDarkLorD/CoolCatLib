package net.ixdarklord.coolcatcanvas.api.client.armature.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.coolcatcanvas.api.client.armature.Animator;
import net.ixdarklord.coolcatcanvas.api.client.armature.Armature;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureDraw;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureFrame;
import net.ixdarklord.coolcatcanvas.api.client.armature.BakedArmature;
import net.ixdarklord.coolcatcanvas.api.client.blockentity.BlockRenderContext;
import net.ixdarklord.coolcatcanvas.api.client.blockentity.BlockRenderState;
import net.ixdarklord.coolcatcanvas.api.client.blockentity.BlockRenderer;
import net.ixdarklord.coolcatcanvas.api.client.blockentity.Detail;
import net.ixdarklord.coolcatcanvas.internal.client.GameClock;
import net.ixdarklord.coolcatcanvas.internal.client.blockentity.BlockRenderFrames;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
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
 * A {@link BlockRenderer}: culled out of view by the armature's bounds, its states reused, and drawn in less detail
 * farther off once {@link #detail} bands are set (an armature at {@link Detail#REDUCED} animates every other frame, at
 * {@link Detail#MINIMAL} every fourth, without its attachments).
 */
public class ArmatureBlockRenderer<T extends BlockEntity> extends BlockRenderer<T, ArmatureBlockRenderer.State> {
    private final ArmatureRenderSettings<T> settings;
    private final Matrix4f modelToWorld = new Matrix4f();
    private final Matrix4f placement = new Matrix4f();
    private boolean facing = true;

    public ArmatureBlockRenderer(BlockEntityRendererProvider.Context context, Armature armature) {
        super(context);
        this.settings = new ArmatureRenderSettings<>(armature);
        this.bounds((blockEntity, box) -> {
            // However it's turned, the model stays within its farthest reach from the block's middle.
            float reach = this.reach();
            box.set(0.5F - reach, 0.5F - reach, 0.5F - reach, 0.5F + reach, 0.5F + reach, 0.5F + reach);
        });
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
    @Override
    public ArmatureBlockRenderer<T> viewDistance(int blocks) {
        super.viewDistance(blocks);
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

    // (Turned by its own placement, not BlockRenderer's: the armature stands on its block's floor.)
    @Override
    protected Direction direction(T blockEntity) {
        return this.facing(blockEntity, blockEntity.getBlockState());
    }

    @Override
    protected State newState() {
        return new State();
    }

    @Override
    protected void extract(T blockEntity, State state, BlockRenderContext context) {
        state.modelScale = this.settings.scale;
        Animator animator = this.settings.animator(blockEntity, a -> this.setupAnimator(a, blockEntity));
        ArmatureFrame last = animator.frame();
        // Farther off, it animates less often (staggered by place, so they don't all update on the same frame).
        int every = state.detail <= Detail.FULL ? 1 : state.detail == Detail.REDUCED ? 2 : 4;
        if (last != null && every > 1 && Math.floorMod(BlockRenderFrames.current() + (int) BlockPos.asLong(state.blockPos.getX(), 0, state.blockPos.getZ()), every) != 0) {
            state.frame = last;
        } else {
            BakedArmature armature = this.settings.armature.get();
            BlockPos pos = blockEntity.getBlockPos();
            Matrix4f modelToWorld = this.modelToWorld.translation(pos.getX(), pos.getY(), pos.getZ());
            place(modelToWorld, state);
            modelToWorld.mul(ArmatureDraw.modelTransform(armature));
            state.frame = animator.update(GameClock.seconds(), context.partialTicks(), modelToWorld);
        }
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
    protected void render(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.frame == null) return;
        poseStack.pushPose();
        place(this.placement.identity(), state);
        poseStack.mulPose(this.placement);
        Identifier texture = state.texture;
        ArmatureDraw.submit(state.frame, poseStack, collector, ArmatureDraw.Style.lit(state.lightCoords)
                .crumbling(state.breakProgress)
                .texture(texture == null ? null : slot -> slot == 0 ? texture : null));
        if (state.detail < Detail.MINIMAL) this.settings.submitAttachments(state.frame, poseStack, collector, state.lightCoords);
        poseStack.popPose();
    }

    // How far the model reaches from the block's middle, in blocks, however it's turned.
    private float reach() {
        AABB bounds = this.settings.armature.get().bounds();
        double far = Math.max(Math.max(Math.max(-bounds.minX, bounds.maxX), Math.max(-bounds.minY, bounds.maxY)), Math.max(-bounds.minZ, bounds.maxZ));
        // (In model pixels from its origin, which may be the block's corner rather than its floor's middle: a block's
        // more either way.)
        return (float) (far / 16.0 * this.settings.scale) + 1.0F;
    }

    @Override
    public boolean shouldRenderOffScreen() {
        // A model reaching well past its block is drawn even when the block itself is off screen.
        AABB bounds = this.settings.armature.get().bounds();
        float reach = 16.0F * 1.5F / Math.max(0.01F, this.settings.scale);
        return bounds.minX < -reach || bounds.maxX > reach || bounds.minZ < -reach || bounds.maxZ > reach || bounds.maxY > reach * 1.5F;
    }

    /** What an armature-driven block entity is drawn from, each frame. */
    public static class State extends BlockRenderState {
        public @Nullable ArmatureFrame frame;
        public @Nullable Identifier texture;
        public float modelScale = 1.0F;
    }
}
