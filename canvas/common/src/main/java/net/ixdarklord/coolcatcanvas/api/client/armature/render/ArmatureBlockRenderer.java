package net.ixdarklord.coolcatcanvas.api.client.armature.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.coolcatcanvas.api.client.armature.Animator;
import net.ixdarklord.coolcatcanvas.api.client.armature.Armature;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureDraw;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureFrame;
import net.ixdarklord.coolcatcanvas.api.client.armature.BakedArmature;
import net.ixdarklord.coolcatcanvas.api.client.blockentity.BlockRenderContext;
import net.ixdarklord.coolcatcanvas.api.client.blockentity.BlockRenderLayer;
import net.ixdarklord.coolcatcanvas.api.client.blockentity.BlockRenderState;
import net.ixdarklord.coolcatcanvas.api.client.blockentity.BlockRenderer;
import net.ixdarklord.coolcatcanvas.api.client.blockentity.Detail;
import net.ixdarklord.coolcatcanvas.api.client.blockentity.Facing;
import net.ixdarklord.coolcatcanvas.api.client.blockentity.RenderBounds;
import net.ixdarklord.coolcatcanvas.internal.client.GameClock;
import net.ixdarklord.coolcatcanvas.internal.client.blockentity.BlockRenderFrames;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Draws a block entity with an armature, standing on the bottom of its block and turned to the block's {@code facing} if it
 * has one (the model's front, north in Blockbench, toward that side). Blockbench "Java Block" models, whose origin is
 * a corner, are moved to fit by themselves. Give the block {@code RenderShape.INVISIBLE} so only the armature shows.
 * <pre>{@code
 * BlockEntityRendererRegistry.register(MyBlocks.TURRET_ENTITY, ArmatureBlockRenderer.of(Armature.of("mymod", "turret"), renderer -> renderer
 *         .detail(Detail.FULL, 24)
 *         .animate(animator -> {
 *             animator.lookAt("barrel").range(12).smoothing(0.3F);
 *             animator.layer("base").drive(ctx -> ctx.<TurretEntity>owner().isFiring() ? FIRE : IDLE);
 *         })));
 * }</pre>
 * It's a {@link BlockRenderer} whose drawing is the armature, so every {@link BlockRenderer} setting works on it the same
 * way: {@link #facing} (by default {@link Facing#ALL}), {@link #detail} bands (an armature at {@link Detail#REDUCED}
 * animates every other frame, at {@link Detail#MINIMAL} every fourth, without its attachments), {@link #layer}s drawn
 * with it, and {@link #bounds}, which grow from the armature's own. It's culled out of view by those bounds and its
 * states are reused.
 * <p>
 * To draw more than the armature, extend it as any {@link BlockRenderer}: a {@link State} subclass from
 * {@link #newState}, and {@link #extract} and {@link #render} that call these first.
 */
public class ArmatureBlockRenderer<T extends BlockEntity> extends BlockRenderer<T, ArmatureBlockRenderer.State> {
    private final ArmatureRenderSettings<T> settings;
    private final Matrix4f modelToWorld = new Matrix4f();

    public ArmatureBlockRenderer(BlockEntityRendererProvider.Context context, Armature armature) {
        super(context);
        this.settings = new ArmatureRenderSettings<>(armature);
        super.facing(Facing.ALL);
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

    // The armature's settings.

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

    public ArmatureBlockRenderer<T> attach(String point, ArmatureAttachment attachment) {
        this.settings.attachments.add(new ArmatureRenderSettings.Attached(point, attachment));
        return this;
    }

    // BlockRenderer's settings, returning this renderer so they chain with the armature's.

    @Override
    public ArmatureBlockRenderer<T> viewDistance(int blocks) {
        super.viewDistance(blocks);
        return this;
    }

    @Override
    public ArmatureBlockRenderer<T> detail(int detail, double blocks) {
        super.detail(detail, blocks);
        return this;
    }

    /** Grows its bounds past the armature's own (which it's always culled by), for something drawn farther out. */
    @Override
    public ArmatureBlockRenderer<T> bounds(BoundsFunction<? super T> bounds) {
        super.bounds(bounds);
        return this;
    }

    @Override
    public ArmatureBlockRenderer<T> reach(ToDoubleFunction<? super T> reach) {
        super.reach(reach);
        return this;
    }

    /** Which way the model turns to its block's facing; {@link Facing#ALL} by default. */
    @Override
    public ArmatureBlockRenderer<T> facing(Facing facing) {
        super.facing(facing);
        return this;
    }

    @Override
    public ArmatureBlockRenderer<T> pooled(boolean pooled) {
        super.pooled(pooled);
        return this;
    }

    @Override
    public ArmatureBlockRenderer<T> trackLookedAt() {
        super.trackLookedAt();
        return this;
    }

    @Override
    public ArmatureBlockRenderer<T> offScreen(boolean offScreen) {
        super.offScreen(offScreen);
        return this;
    }

    @Override
    public ArmatureBlockRenderer<T> layer(BlockRenderLayer<? super T, ? super State, ?> layer) {
        super.layer(layer);
        return this;
    }

    /**
     * Whether the model turns to the block's {@code facing}.
     *
     * @deprecated Use {@link #facing(Facing)}: {@code facing(Facing.NONE)} for {@code false}, and {@link Facing#HORIZONTAL}
     * keeps it upright.
     */
    @Deprecated
    public ArmatureBlockRenderer<T> facing(boolean facing) {
        return this.facing(facing ? Facing.ALL : Facing.NONE);
    }

    // What a subclass may override.

    /** Sets up a new animator, after the {@link #animate} setups; for subclasses. */
    protected void setupAnimator(Animator animator, T blockEntity) {
    }

    /**
     * Which way the model faces.
     *
     * @deprecated Override {@link #direction(BlockEntity)}, as for any {@link BlockRenderer}.
     */
    @Deprecated
    protected Direction facing(T blockEntity, BlockState state) {
        return super.direction(blockEntity);
    }

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
            Matrix4f modelToWorld = turn(state, this.modelToWorld.translation(pos.getX(), pos.getY(), pos.getZ()));
            stand(modelToWorld, state);
            modelToWorld.mul(ArmatureDraw.modelTransform(armature));
            state.frame = animator.update(GameClock.seconds(), context.partialTicks(), modelToWorld);
        }
        state.texture = this.settings.texture != null ? this.settings.texture.apply(blockEntity) : null;
    }

    // From the block's corner (already turned to face) to the model's origin: the middle of its floor.
    private static void stand(Matrix4f matrix, State state) {
        matrix.translate(0.5F, 0.0F, 0.5F).scale(state.modelScale);
    }

    @Override
    protected void render(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.frame == null) return;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.scale(state.modelScale, state.modelScale, state.modelScale);
        Identifier texture = state.texture;
        ArmatureDraw.submit(state.frame, poseStack, collector, ArmatureDraw.Style.lit(state.lightCoords)
                .crumbling(state.breakProgress)
                .texture(texture == null ? null : slot -> slot == 0 ? texture : null));
        if (state.detail < Detail.MINIMAL) this.settings.submitAttachments(state.frame, poseStack, collector, state.lightCoords);
        poseStack.popPose();
    }

    // However it's turned, the model stays within its farthest reach from the block's middle; bounds() grows from there.
    @Override
    protected void growBounds(T blockEntity, RenderBounds box) {
        float reach = this.modelReach();
        box.set(0.5F - reach, 0.5F - reach, 0.5F - reach, 0.5F + reach, 0.5F + reach, 0.5F + reach);
        super.growBounds(blockEntity, box);
    }

    // How far the model reaches from the block's middle, in blocks, however it's turned.
    private float modelReach() {
        AABB bounds = this.settings.armature.get().bounds();
        double far = Math.max(Math.max(Math.max(-bounds.minX, bounds.maxX), Math.max(-bounds.minY, bounds.maxY)), Math.max(-bounds.minZ, bounds.maxZ));
        // (In model pixels from its origin, which may be the block's corner rather than its floor's middle: a block's
        // more either way.)
        return (float) (far / 16.0 * this.settings.scale) + 1.0F;
    }

    // A model reaching well past its block is drawn even when the block itself is off screen.
    @Override
    protected boolean reachesOut() {
        AABB bounds = this.settings.armature.get().bounds();
        float reach = 16.0F * 1.5F / Math.max(0.01F, this.settings.scale);
        return bounds.minX < -reach || bounds.maxX > reach || bounds.minZ < -reach || bounds.maxZ > reach || bounds.maxY > reach * 1.5F
                || super.reachesOut();
    }

    /** What an armature-driven block entity is drawn from, each frame. Extend it for a subclass's own state. */
    public static class State extends BlockRenderState {
        public @Nullable ArmatureFrame frame;
        public @Nullable Identifier texture;
        public float modelScale = 1.0F;
    }
}
