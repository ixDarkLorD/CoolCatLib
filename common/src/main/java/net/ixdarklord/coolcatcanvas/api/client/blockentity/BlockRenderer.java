package net.ixdarklord.coolcatcanvas.api.client.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.ixdarklord.coolcatcanvas.internal.client.blockentity.BlockRenderFrames;
import net.ixdarklord.coolcatcanvas.internal.client.blockentity.BlockStatePool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * A block entity renderer that does the work every renderer otherwise repeats, so a subclass writes only what it
 * draws: {@link #newState}, {@link #extract} and {@link #render}.
 * <ul>
 *     <li><b>Culling.</b> A block entity farther off than its {@link #viewDistance} or out of view ({@link #bounds}) is
 *     skipped before the game makes it a state, on both loaders (Fabric doesn't cull block entities by view at all).</li>
 *     <li><b>Detail by distance.</b> {@link #detail} bands set what each one's state is drawn at, and every band
 *     reaches farther for one shown larger ({@link #reach}). Layers are skipped past the detail they're drawn to.</li>
 *     <li><b>No allocation per frame.</b> The game asks for a new state for every block entity every frame; these
 *     come from a pool and are reused ({@link #pooled}).</li>
 *     <li><b>Facing.</b> Turns what it draws to its block's facing ({@link #facing}), with no math in {@code render}.</li>
 *     <li><b>Layers</b> ({@link #layer}): floating items, text, other renderers, drawn with it.</li>
 * </ul>
 * It's a plain {@link BlockEntityRenderer}, registered as any other:
 * <pre>{@code
 * public final class PedestalRenderer extends BlockRenderer<PedestalBlockEntity, PedestalRenderer.State> {
 *     public PedestalRenderer(BlockEntityRendererProvider.Context context) {
 *         super(context);
 *         this.viewDistance(96).detail(Detail.FULL, 24).detail(Detail.REDUCED, 48)
 *                 .facing(Facing.HORIZONTAL)
 *                 .bounds((pedestal, box) -> box.up(1.5F))
 *                 .layer(ItemLayer.<PedestalBlockEntity, State>of(PedestalBlockEntity::display).at(0.5F, 1.2F, 0.5F).spin(2.0F));
 *     }
 *     protected State newState() { return new State(); }
 *     protected void extract(PedestalBlockEntity pedestal, State state, BlockRenderContext context) { ... }
 *     protected void render(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) { ... }
 *     public static final class State extends BlockRenderState { ... }
 * }
 *
 * BlockEntityRendererRegistry.register(MyBlockEntities.PEDESTAL, PedestalRenderer::new);
 * }</pre>
 * Set it up in the constructor: a renderer is made again on every resource reload, and its settings are read every
 * frame. Like all rendering, it runs on the client thread only.
 */
public abstract class BlockRenderer<T extends BlockEntity, S extends BlockRenderState> implements BlockEntityRenderer<T, S> {
    // A model's turn to each facing, from facing north, about its block's middle.
    private static final Quaternionf[] TURNS = new Quaternionf[Direction.values().length];
    private static final Quaternionf[] UNTURNS = new Quaternionf[Direction.values().length];
    // (Infinite on every side: what NeoForge's AABB.isInfinite passes without a test.)
    private static final AABB EVERYWHERE = new AABB(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
            Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    private static final int BOUNDS_COLOR = 0xFF55FFFF, REDUCED_BOUNDS_COLOR = 0xFFFFFF55, MINIMAL_BOUNDS_COLOR = 0xFFFF5555;

    static {
        for (Direction direction : Direction.values()) {
            Quaternionf turn = switch (direction) {
                case UP -> new Quaternionf().rotationX(Mth.HALF_PI);
                case DOWN -> new Quaternionf().rotationX(-Mth.HALF_PI);
                default -> new Quaternionf().rotationY((180.0F - direction.toYRot()) * Mth.DEG_TO_RAD);
            };
            TURNS[direction.ordinal()] = turn;
            UNTURNS[direction.ordinal()] = new Quaternionf(turn).conjugate();
        }
    }

    protected final BlockEntityRendererProvider.Context context;
    private final BlockRenderContext extractContext;
    private final BlockStatePool<S> pool = new BlockStatePool<>(this::createUnpooledState);
    private final List<BlockRenderLayer<? super T, ? super S, ?>> layers = new ArrayList<>();
    private final RenderBounds box = new RenderBounds();
    private int viewDistance = 64;
    private double[] bands = new double[0];
    private @Nullable BoundsFunction<? super T> bounds;
    private @Nullable ToDoubleFunction<? super T> reach;
    private Facing facing = Facing.NONE;
    private boolean pooled = true;
    private boolean trackLookedAt;
    private @Nullable Boolean offScreen;
    private int lastFrame = Integer.MIN_VALUE;

    protected BlockRenderer(BlockEntityRendererProvider.Context context) {
        this.context = context;
        this.extractContext = new BlockRenderContext(context);
    }

    // Settings: set in the constructor.

    /** How far away, in blocks, it is drawn at all; 64 by default (vanilla's). Grows with {@link #reach}. */
    public BlockRenderer<T, S> viewDistance(int blocks) {
        this.viewDistance = blocks;
        return this;
    }

    /**
     * Draws it at this {@link Detail} up to this far away, in blocks (grown with {@link #reach}); past the last band
     * set, it's drawn at the next level down, out to its {@link #viewDistance}. With no bands, it's always FULL.
     * <pre>{@code
     * this.detail(Detail.FULL, 16).detail(Detail.REDUCED, 40);  // FULL to 16, REDUCED to 40, MINIMAL beyond
     * }</pre>
     */
    public BlockRenderer<T, S> detail(int detail, double blocks) {
        if (detail < 0) throw new IllegalArgumentException("Detail levels start at 0 (Detail.FULL)");
        if (detail >= this.bands.length) {
            int from = this.bands.length;
            this.bands = Arrays.copyOf(this.bands, detail + 1);
            Arrays.fill(this.bands, from, detail, Double.NaN);
        }
        this.bands[detail] = blocks;
        return this;
    }

    /**
     * The space each block entity draws in, for culling it out of view: the box starts as its own block, and the
     * function grows it. By default it's the block alone. Bounds that reach past the block also make it drawn while
     * its block's section is out of view (vanilla's {@code shouldRenderOffScreen}), unless {@link #offScreen} says otherwise.
     * <pre>{@code
     * this.bounds((pedestal, box) -> box.grow(pedestal.scale(), 0.0F, pedestal.scale()).up(2.0F + pedestal.height()));
     * }</pre>
     */
    public BlockRenderer<T, S> bounds(BoundsFunction<? super T> bounds) {
        this.bounds = bounds;
        return this;
    }

    /**
     * How many times farther than usual one is drawn, and in detail: for a block entity shown larger, seen from farther
     * off. Scales {@link #viewDistance} and every {@link #detail} band. 1 by default.
     */
    public BlockRenderer<T, S> reach(ToDoubleFunction<? super T> reach) {
        this.reach = reach;
        return this;
    }

    /** Turns what it draws to its block's facing (and sets {@link BlockRenderState#facing}); {@link Facing#NONE} by default. */
    public BlockRenderer<T, S> facing(Facing facing) {
        this.facing = facing;
        return this;
    }

    /**
     * Whether its states are reused from frame to frame; on by default. Turn it off only if something keeps a state
     * past the frame it was made for.
     */
    public BlockRenderer<T, S> pooled(boolean pooled) {
        this.pooled = pooled;
        return this;
    }

    /** Works out {@link BlockRenderState#lookedAt} for each one (off by default: one check against the crosshair). */
    public BlockRenderer<T, S> trackLookedAt() {
        this.trackLookedAt = true;
        return this;
    }

    /** Whether it's drawn while its block's section is out of view; by default, when its {@link #bounds} reach out. */
    public BlockRenderer<T, S> offScreen(boolean offScreen) {
        this.offScreen = offScreen;
        return this;
    }

    /** Adds a layer, drawn after it (and after the layers added before it). */
    public BlockRenderer<T, S> layer(BlockRenderLayer<? super T, ? super S, ?> layer) {
        this.layers.add(layer);
        return this;
    }

    // What a subclass writes.

    /** A new, empty state. */
    protected abstract S newState();

    /**
     * Reads what this block entity shows into its state. The base fields ({@link BlockRenderState#detail},
     * {@link BlockRenderState#time}, {@link BlockRenderState#facing}, ...) are set already; call
     * {@link BlockRenderState#skip()} to draw nothing.
     */
    protected abstract void extract(T blockEntity, S state, BlockRenderContext context);

    /**
     * Draws it. The pose is at its block's corner, turned to its facing if it {@link #facing turns}, and is popped
     * afterwards; its layers are drawn after this.
     */
    protected abstract void render(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera);

    /** Called once a frame, before the frame's first block entity is extracted: for work every one of them shares. */
    protected void onFrame() {
    }

    /** Which way it faces; by default the block's facing as {@link #facing} reads it (north for {@link Facing#NONE}). */
    protected Direction direction(T blockEntity) {
        if (this.facing == Facing.NONE) return Direction.NORTH;
        BlockState block = blockEntity.getBlockState();
        if (this.facing == Facing.ALL && block.hasProperty(BlockStateProperties.FACING)) return block.getValue(BlockStateProperties.FACING);
        if (block.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return block.getValue(BlockStateProperties.HORIZONTAL_FACING);
        if (block.hasProperty(BlockStateProperties.FACING)) {
            Direction direction = block.getValue(BlockStateProperties.FACING);
            return direction.getAxis().isHorizontal() ? direction : Direction.NORTH;
        }
        return Direction.NORTH;
    }

    // The game's side.

    /** A state from the pool (or a new one if it isn't {@link #pooled}). Override {@link #newState} rather than this. */
    @Override
    public S createRenderState() {
        return this.pooled ? this.pool.take() : this.createUnpooledState();
    }

    /** A new state of its own, outside the pool: for a state kept across frames, or drawn inside another renderer's. */
    public final S createUnpooledState() {
        S state = this.newState();
        state.layers = this.newLayerStates();
        return state;
    }

    private Object[] newLayerStates() {
        Object[] states = new Object[this.layers.size()];
        for (int i = 0; i < states.length; i++) states[i] = this.layers.get(i).newState();
        return states;
    }

    /** Culls it by distance and view before the game makes it a state. */
    @Override
    public boolean shouldRender(T blockEntity, Vec3 cameraPosition) {
        BlockPos pos = blockEntity.getBlockPos();
        double dx = pos.getX() + 0.5 - cameraPosition.x, dy = pos.getY() + 0.5 - cameraPosition.y, dz = pos.getZ() + 0.5 - cameraPosition.z;
        double far = this.viewDistance * this.reachOf(blockEntity);
        if (dx * dx + dy * dy + dz * dz >= far * far) return false;
        RenderBounds box = this.boundsOf(blockEntity);
        return BlockRenderFrames.inView(pos, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    /**
     * NeoForge culls a block entity by this box before {@link #shouldRender}, and makes a new one for each by default;
     * this renderer culls by its own {@link #bounds} there instead, on both loaders, so NeoForge's test is passed
     * straight through. (Overrides NeoForge's {@code IBlockEntityRendererExtension} method; unused on Fabric.)
     */
    public AABB getRenderBoundingBox(BlockEntity blockEntity) {
        return EVERYWHERE;
    }

    @Override
    public boolean shouldRenderOffScreen() {
        if (this.offScreen != null) return this.offScreen;
        return this.bounds != null;
    }

    @Override
    public int getViewDistance() {
        return this.viewDistance;
    }

    /**
     * Fills the base fields, then {@link #extract}, then the layers. A subclass overriding this calls it first.
     */
    @Override
    public void extractRenderState(T blockEntity, S state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(blockEntity, state, breakProgress);
        int frame = BlockRenderFrames.current();
        if (frame != this.lastFrame) {
            this.lastFrame = frame;
            this.onFrame();
        }
        BlockPos pos = blockEntity.getBlockPos();
        double dx = pos.getX() + 0.5 - cameraPosition.x, dy = pos.getY() + 0.5 - cameraPosition.y, dz = pos.getZ() + 0.5 - cameraPosition.z;
        double reach = this.reachOf(blockEntity);
        state.distanceSq = dx * dx + dy * dy + dz * dz;
        state.reach = (float) reach;
        state.detail = this.detailAt(state.distanceSq, reach);
        state.partialTicks = partialTicks;
        Level level = blockEntity.getLevel();
        state.gameTime = level != null ? level.getGameTime() : 0L;
        state.time = state.gameTime + partialTicks;
        state.facing = this.direction(blockEntity);
        state.posed = this.facing != Facing.NONE;
        state.lookedAt = this.trackLookedAt && lookedAt(pos);
        state.skip = false;
        state.showBounds = Minecraft.getInstance().debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES);
        if (state.showBounds) {
            RenderBounds box = this.boundsOf(blockEntity);
            state.minX = box.minX;
            state.minY = box.minY;
            state.minZ = box.minZ;
            state.maxX = box.maxX;
            state.maxY = box.maxY;
            state.maxZ = box.maxZ;
        }
        // (A state made by a subclass's own createRenderState, or before a layer was added, gets its layers' states now.)
        if (state.layers.length != this.layers.size()) state.layers = this.newLayerStates();
        this.extractContext.set(cameraPosition, partialTicks, level);
        this.extract(blockEntity, state, this.extractContext);
        if (state.skip) return;
        for (int i = 0; i < this.layers.size(); i++) {
            BlockRenderLayer<? super T, ? super S, ?> layer = this.layers.get(i);
            if (state.detail <= layer.detail()) extractLayer(layer, blockEntity, state, state.layers[i], this.extractContext);
        }
    }

    /** Draws it in its pose: {@link #render}, then the layers. A subclass overriding this calls it. */
    @Override
    public void submit(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.skip) return;
        poseStack.pushPose();
        if (state.posed) poseStack.rotateAround(TURNS[state.facing.ordinal()], 0.5F, 0.5F, 0.5F);
        this.render(state, poseStack, collector, camera);
        for (int i = 0; i < this.layers.size(); i++) {
            BlockRenderLayer<? super T, ? super S, ?> layer = this.layers.get(i);
            if (state.detail <= layer.detail()) submitLayer(layer, state, state.layers[i], poseStack, collector, camera);
        }
        poseStack.popPose();
        if (state.showBounds) submitBounds(state, poseStack, collector);
    }

    /**
     * Undoes the turn to its facing that {@link #submit} applied, about the pose's current point: for something that
     * must not turn with it (text that faces the camera, a beam that stands upright).
     */
    public static void unturn(BlockRenderState state, PoseStack poseStack) {
        if (state.posed) poseStack.mulPose(UNTURNS[state.facing.ordinal()]);
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity, S extends BlockRenderState, L> void extractLayer(BlockRenderLayer<? super T, ? super S, L> layer, T blockEntity, S state,
                                                                                            Object layerState, BlockRenderContext context) {
        layer.extract(blockEntity, state, (L) layerState, context);
    }

    @SuppressWarnings("unchecked")
    private static <S extends BlockRenderState, L> void submitLayer(BlockRenderLayer<?, ? super S, L> layer, S state, Object layerState, PoseStack poseStack,
                                                                    SubmitNodeCollector collector, CameraRenderState camera) {
        layer.submit(state, (L) layerState, poseStack, collector, camera);
    }

    private double reachOf(T blockEntity) {
        return this.reach == null ? 1.0 : Math.max(1.0E-3, this.reach.applyAsDouble(blockEntity));
    }

    private RenderBounds boundsOf(T blockEntity) {
        RenderBounds box = this.box.reset();
        if (this.bounds != null) this.bounds.bounds(blockEntity, box);
        return box;
    }

    private int detailAt(double distanceSq, double reach) {
        for (int i = 0; i < this.bands.length; i++) {
            double band = this.bands[i] * reach;
            if (!Double.isNaN(band) && distanceSq <= band * band) return i;
        }
        return this.bands.length;
    }

    private static boolean lookedAt(BlockPos pos) {
        return Minecraft.getInstance().hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    // Its bounds as a line box, colored by its detail, while hitboxes are shown (F3 + B).
    private static void submitBounds(BlockRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        int color = state.detail <= Detail.FULL ? BOUNDS_COLOR : state.detail == Detail.REDUCED ? REDUCED_BOUNDS_COLOR : MINIMAL_BOUNDS_COLOR;
        float x0 = state.minX, y0 = state.minY, z0 = state.minZ, x1 = state.maxX, y1 = state.maxY, z1 = state.maxZ;
        collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, consumer) -> {
            line(consumer, pose, x0, y0, z0, x1, y0, z0, color);
            line(consumer, pose, x0, y0, z1, x1, y0, z1, color);
            line(consumer, pose, x0, y1, z0, x1, y1, z0, color);
            line(consumer, pose, x0, y1, z1, x1, y1, z1, color);
            line(consumer, pose, x0, y0, z0, x0, y1, z0, color);
            line(consumer, pose, x1, y0, z0, x1, y1, z0, color);
            line(consumer, pose, x0, y0, z1, x0, y1, z1, color);
            line(consumer, pose, x1, y0, z1, x1, y1, z1, color);
            line(consumer, pose, x0, y0, z0, x0, y0, z1, color);
            line(consumer, pose, x1, y0, z0, x1, y0, z1, color);
            line(consumer, pose, x0, y1, z0, x0, y1, z1, color);
            line(consumer, pose, x1, y1, z0, x1, y1, z1, color);
        });
    }

    private static void line(VertexConsumer consumer, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        float nx = x1 - x0, ny = y1 - y0, nz = z1 - z0;
        float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (length > 0.0F) {
            nx /= length;
            ny /= length;
            nz /= length;
        }
        consumer.addVertex(pose, x0, y0, z0).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(2.0F);
        consumer.addVertex(pose, x1, y1, z1).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(2.0F);
    }

    /** Grows a block entity's {@link RenderBounds} to the space it draws in. */
    @FunctionalInterface
    public interface BoundsFunction<T> {
        void bounds(T blockEntity, RenderBounds box);
    }
}
