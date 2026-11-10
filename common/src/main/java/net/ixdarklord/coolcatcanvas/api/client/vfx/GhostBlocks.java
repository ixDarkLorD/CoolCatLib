package net.ixdarklord.coolcatcanvas.api.client.vfx;

import net.ixdarklord.coolcatcanvas.internal.client.vfx.GhostRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

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
        ALWAYS,
        /**
         * Over the real block where it stands, hidden only by what is in front of it: drawn a hair nearer the camera
         * than the block, so it covers exactly the block's faces in plain sight and nothing beside them (one block
         * fading into another, say).
         */
        OVER_BLOCK
    }

    /** How a ghost is lit. */
    public enum Light {
        /** Every face full bright, so it stands out (through walls, say). */
        BRIGHT,
        /** Lit as an item is: each face shaded by its side, at full brightness. */
        SHADED,
        /**
         * Lit as the world lights a real block there: each face shaded by its side as blocks are, smooth lighting, and
         * the block and sky light where it stands (dark in a cave, dimmer at dusk). A ghost of one block over another
         * matches the real block's shading exactly.
         */
        WORLD
    }

    /**
     * Draws blocks as ghosts in {@code argb}: its colour tints them, its alpha is their opacity.
     *
     * @param shaded lit as blocks are in the world, each face its own shade; false for every face full bright (so they
     *               stand out, through walls say)
     */
    public static void draw(Collection<Placed> blocks, int argb, Show show, boolean shaded) {
        GhostRenderer.draw(blocks, argb, show, shaded ? Light.SHADED : Light.BRIGHT);
    }

    /** Draws blocks as ghosts in {@code argb} (its colour tints them, its alpha is their opacity), lit this way. */
    public static void draw(Collection<Placed> blocks, int argb, Show show, Light light) {
        GhostRenderer.draw(blocks, argb, show, light);
    }

    /**
     * Draws several groups of ghosts at once, each in its own {@code argbs} entry (colour and opacity), lit this way: the
     * same as a {@link #draw} for each, but groups lit by the {@link Light#WORLD world} whose blocks are all solid go
     * over the world in one pass, much cheaper than one pass a group (a reveal fading in waves, say). A group with no
     * opacity is skipped.
     */
    public static void draw(List<? extends Collection<Placed>> groups, int[] argbs, Show show, Light light) {
        if (argbs.length < groups.size()) throw new IllegalArgumentException("A colour is needed for each of the " + groups.size() + " groups");
        GhostRenderer.draw(groups, argbs, show, light);
    }

    /**
     * Draws blocks as ghosts (their real models, as {@link #draw} does: a flower's petals, grass's blades, a leaf's cut
     * out holes) and lays a fragment shader of the mod's own over the world wherever they show, in place of a flat
     * colour: a force field running over the blocks, a scan, a frost creeping across them. Being worked out per pixel,
     * it follows the blocks' true shapes with no seams between them.
     * <p>
     * The shader ({@code assets/<namespace>/shaders/<path>.fsh}) begins:
     * <pre>{@code
     * #version 330
     * #moj_import <coolcatlib:ghost_effect.glsl>
     * out vec4 fragColor;
     * }</pre>
     * {@code ghost_effect.glsl} gives it {@code ghost_depth(pixel)} (where a ghost shows at a pixel, as {@code show}
     * says, or -1), {@code ghost_world_position(fragCoord, depth)} (that pixel's place in the world),
     * {@code ghost_color(pixel)} (the ghost's own colour there), the texture as {@code EffectSampler} (sampled with
     * repeating, nearest-pixel filtering), and {@code params} as {@code GhostParams0} to {@code GhostParams3}: up to
     * sixteen numbers of the mod's own, in order (where something is, how far along it is). Its output is blended over
     * the world by its alpha.
     *
     * @param shader  the fragment shader, such as {@code mymod:core/force_field}
     * @param texture a texture for it to sample, or null
     * @param params  up to sixteen numbers for it; reuse the array from frame to frame
     */
    public static void drawEffect(Collection<Placed> blocks, Show show, Light light, Identifier shader, @Nullable Identifier texture, float[] params) {
        GhostRenderer.drawEffect(blocks, show, light, shader, texture, params);
    }

    /** Forgets the block models made so far: call after the game's models are reloaded, so new ones are drawn. */
    public static void clearModels() {
        GhostRenderer.clearModels();
    }
}
