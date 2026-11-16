package net.ixdarklord.coolcatcanvas.api.client.blockentity;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/**
 * What a {@link BlockRenderer} draws from, each frame: vanilla's own (place, block, light, crumbling) and what the
 * renderer works out for every block entity before its {@code extract} runs. Add the fields your renderer needs in a
 * subclass. States are reused from frame to frame (see {@link BlockRenderer#pooled}), so {@code extract} sets every
 * field it reads, and what a field holds is replaced rather than built anew where it can be (an
 * {@code ItemStackRenderState} made once in the state and updated, not a new one each frame).
 */
public class BlockRenderState extends BlockEntityRenderState {
    private static final Object[] NO_LAYERS = new Object[0];

    /** How much of it is drawn, by how far off it is: {@link Detail#FULL}, {@link Detail#REDUCED} or {@link Detail#MINIMAL}. */
    public int detail;
    /** Its block's middle's distance from the camera, squared. */
    public double distanceSq;
    /** How many times farther than usual it's drawn (its renderer's {@link BlockRenderer#reach}). */
    public float reach = 1.0F;
    public float partialTicks;
    /** The level's game time, and that plus the partial tick: smooth ticks to animate by. */
    public long gameTime;
    public double time;
    /** Which way its block faces (north for a renderer that doesn't turn to it). */
    public Direction facing = Direction.NORTH;
    /** Whether the player is looking at its block (only worked out for a renderer that {@link BlockRenderer#trackLookedAt}s). */
    public boolean lookedAt;

    boolean skip;
    // Turned to its facing when submitted (BlockRenderer.facing other than NONE).
    boolean posed;
    Object[] layers = NO_LAYERS;
    // Its bounds, for drawing them while hitboxes are shown (F3 + B).
    boolean showBounds;
    float minX, minY, minZ, maxX, maxY, maxZ;

    /** Draws nothing of it this frame, its layers included: for when {@code extract} finds nothing to show. */
    public void skip() {
        this.skip = true;
    }

    public boolean skipped() {
        return this.skip;
    }

    /** Whether it's drawn at this detail or more: {@code state.detailed(Detail.REDUCED)} is true at FULL and REDUCED. */
    public boolean detailed(int detail) {
        return this.detail <= detail;
    }

    /** {@link #time} as a float, wrapped every {@code period} ticks so it stays precise in an old world. */
    public float time(float period) {
        return (float) (this.time % period);
    }
}
