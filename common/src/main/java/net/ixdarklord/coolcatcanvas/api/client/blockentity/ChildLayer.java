package net.ixdarklord.coolcatcanvas.api.client.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Another block entity drawn with this one, by its own {@link BlockRenderer}: a trophy cabinet showing the pedestals
 * it holds, a shelf showing a lamp. The child is extracted and drawn as part of its parent (it isn't culled on its
 * own), placed wherever {@link #place} moves it.
 * <pre>{@code
 * PedestalRenderer pedestals = new PedestalRenderer(context);
 * this.layer(ChildLayer.<CabinetBlockEntity, State, PedestalBlockEntity, PedestalRenderer.State>of(pedestals, cabinet -> cabinet.shownPedestal(0))
 *         .place((state, poseStack) -> { poseStack.translate(0.1F, 0.125F, 0.3F); poseStack.scale(0.34F, 0.34F, 0.34F); }));
 * }</pre>
 * Its state is one of the child renderer's own, kept with the parent's state and reused with it.
 */
public final class ChildLayer<T extends BlockEntity, S extends BlockRenderState, C extends BlockEntity, CS extends BlockRenderState>
        implements BlockRenderLayer<T, S, ChildLayer.Child<CS>> {
    private final BlockRenderer<C, CS> renderer;
    private final Function<? super T, @Nullable C> child;
    private Placement<? super S> placement = (state, poseStack) -> {};
    private int detail = Detail.MINIMAL;

    private ChildLayer(BlockRenderer<C, CS> renderer, Function<? super T, @Nullable C> child) {
        this.renderer = renderer;
        this.child = child;
    }

    /** Draws the block entity {@code child} gives (none for null) with {@code renderer}. */
    public static <T extends BlockEntity, S extends BlockRenderState, C extends BlockEntity, CS extends BlockRenderState> ChildLayer<T, S, C, CS> of(
            BlockRenderer<C, CS> renderer, Function<? super T, @Nullable C> child) {
        return new ChildLayer<>(renderer, child);
    }

    /** Moves the pose to where the child is drawn, from its parent's (its block's corner, turned with it). */
    public ChildLayer<T, S, C, CS> place(Placement<? super S> placement) {
        this.placement = placement;
        return this;
    }

    /** The least detail it's still drawn at; {@link Detail#MINIMAL} by default. */
    public ChildLayer<T, S, C, CS> upTo(int detail) {
        this.detail = detail;
        return this;
    }

    @Override
    public int detail() {
        return this.detail;
    }

    @Override
    public Child<CS> newState() {
        return new Child<>(this.renderer.createUnpooledState());
    }

    @Override
    public void extract(T blockEntity, S state, Child<CS> child, BlockRenderContext context) {
        C shown = this.child.apply(blockEntity);
        child.shown = shown != null;
        if (shown != null) this.renderer.extractRenderState(shown, child.state, context.partialTicks(), context.camera(), null);
    }

    @Override
    public void submit(S state, Child<CS> child, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!child.shown) return;
        poseStack.pushPose();
        this.placement.place(state, poseStack);
        this.renderer.submit(child.state, poseStack, collector, camera);
        poseStack.popPose();
    }

    /** Moves the pose to where a {@link ChildLayer}'s child is drawn. */
    @FunctionalInterface
    public interface Placement<S> {
        void place(S state, PoseStack poseStack);
    }

    /** What a {@link ChildLayer} draws from: the child renderer's own state. */
    public static final class Child<CS> {
        public final CS state;
        public boolean shown;

        Child(CS state) {
            this.state = state;
        }
    }
}
