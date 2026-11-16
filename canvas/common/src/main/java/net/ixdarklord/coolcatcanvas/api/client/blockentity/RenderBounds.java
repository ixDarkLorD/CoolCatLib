package net.ixdarklord.coolcatcanvas.api.client.blockentity;

/**
 * The space a block entity draws in, in blocks from its block's corner (its own block is 0 to 1 on each axis). It is
 * what a {@link BlockRenderer} is culled by when it's out of view, so it should hold everything drawn: a floating item,
 * a beam, a model reaching past the block. One box is reused, so set it in place rather than keeping it.
 */
public final class RenderBounds {
    public float minX, minY, minZ, maxX, maxY, maxZ;

    /** Sets it to its own block. */
    public RenderBounds reset() {
        return this.set(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
    }

    public RenderBounds set(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        return this;
    }

    /** Grows it by this much on each side. */
    public RenderBounds grow(float x, float y, float z) {
        return this.set(this.minX - x, this.minY - y, this.minZ - z, this.maxX + x, this.maxY + y, this.maxZ + z);
    }

    /** Grows it by this much on every side. */
    public RenderBounds grow(float amount) {
        return this.grow(amount, amount, amount);
    }

    /** Raises its top by this much (or lowers its bottom, for a negative amount). */
    public RenderBounds up(float amount) {
        if (amount >= 0.0F) this.maxY += amount;
        else this.minY += amount;
        return this;
    }

    /** Grows it to take in this point. */
    public RenderBounds include(float x, float y, float z) {
        return this.set(Math.min(this.minX, x), Math.min(this.minY, y), Math.min(this.minZ, z),
                Math.max(this.maxX, x), Math.max(this.maxY, y), Math.max(this.maxZ, z));
    }

    /** Whether it reaches past its own block. */
    public boolean reachesOut() {
        return this.minX < 0.0F || this.minY < 0.0F || this.minZ < 0.0F || this.maxX > 1.0F || this.maxY > 1.0F || this.maxZ > 1.0F;
    }
}
