package net.ixdarklord.coolcatcanvas.api.client.vfx;

import net.ixdarklord.coolcatcanvas.internal.client.vfx.GhostRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;

/**
 * Blocks drawn as ghosts: their real models (block entities' too, a chest as a chest), drawn by the game's own
 * renderers with the player's camera into a picture of their own, then laid over the world at one opacity and tint. As
 * one picture, a ghost shows only its outside, as the real block would, never its inside or far faces through its near
 * ones; and it is in perspective from every side.
 * <p>
 * Call {@link #draw} from a world effects renderer ({@link WorldVfx#register}) or anywhere else right after the level is
 * drawn, while its depth is still there. Each call draws one group in one colour; call it once for each.
 * <pre>{@code
 * GhostBlocks.draw(List.of(new GhostBlocks.Placed(pos, Blocks.CHEST.defaultBlockState())), 0x80FFFFFF,
 *         GhostBlocks.Show.THROUGH_WALLS, false);
 * }</pre>
 */
public final class GhostBlocks {
    private GhostBlocks() {}

    /** A block to draw as a ghost, where it is drawn. */
    public record Placed(BlockPos pos, BlockState state) {}

    /** Where a ghost shows, against the world in front of it or behind it. */
    public enum Show {
        /**
         * Only where something in the world is in front of it: seen through walls, and not over the real block where it
         * is in plain sight (a ghost of something that is there, to find it).
         */
        THROUGH_WALLS,
        /** Only where nothing in the world is in front of it, as a real block would be (a ghost of what is to be built). */
        IN_WORLD,
        /** Everywhere, over whatever is in front of it. */
        ALWAYS
    }

    /**
     * Draws blocks as ghosts in {@code argb}: its colour tints them, its alpha is their opacity.
     *
     * @param shaded lit as blocks are in the world, each face its own shade; false for every face full bright (so they
     *               stand out, through walls say)
     */
    public static void draw(Collection<Placed> blocks, int argb, Show show, boolean shaded) {
        GhostRenderer.draw(blocks, argb, show, shaded);
    }

    /** Forgets the block models made so far: call after the game's models are reloaded, so new ones are drawn. */
    public static void clearModels() {
        GhostRenderer.clearModels();
    }
}
