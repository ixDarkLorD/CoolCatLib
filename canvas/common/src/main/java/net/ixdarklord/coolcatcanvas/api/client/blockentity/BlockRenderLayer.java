package net.ixdarklord.coolcatcanvas.api.client.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Something drawn with a {@link BlockRenderer}'s block entities besides what the renderer draws itself, added with
 * {@link BlockRenderer#layer}: a floating item ({@link ItemLayer}), lines of text ({@link LabelLayer}), another block
 * entity's renderer ({@link ChildLayer}), or your own. Layers run in the order they're added, after the renderer's own
 * {@code extract} and {@code render}, and in its pose (turned to its facing, if it turns).
 * <p>
 * A layer keeps what it draws from in a state of its own type, {@code L}: one is made with each of the renderer's
 * states and reused with it, so a layer is typed through and allocates nothing per frame.
 *
 * @param <T> the block entity
 * @param <S> the renderer's state
 * @param <L> the layer's own state
 */
public interface BlockRenderLayer<T extends BlockEntity, S extends BlockRenderState, L> {
    /** A new state for the layer; made once with each of the renderer's states. */
    L newState();

    void extract(T blockEntity, S state, L layer, BlockRenderContext context);

    void submit(S state, L layer, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera);

    /** The least detail it's still drawn at ({@link Detail}); farther off it is skipped, extract and all. */
    default int detail() {
        return Detail.MINIMAL;
    }
}
