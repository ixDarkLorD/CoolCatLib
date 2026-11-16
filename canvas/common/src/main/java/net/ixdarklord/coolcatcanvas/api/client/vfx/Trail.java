package net.ixdarklord.coolcatcanvas.api.client.vfx;

import net.minecraft.world.phys.Vec3;

/**
 * The path something moved along over its last few ticks, drawn as a ribbon with {@link VfxDraw#trail}. Whoever owns
 * the moving thing keeps one and feeds it the thing's position every tick:
 * <pre>{@code
 * Trail trail = new Trail(12);
 * // every client tick
 * trail.add(bolt.position());
 * // every frame, in a VfxRenderer
 * frame.draw(STREAK).trail(trail, Curve.of(0.3F, 0.0F), 0xFFFFE9A0, 0x00FF7A2A);
 * }</pre>
 */
public final class Trail {
    private final double[] points;
    private final int capacity;
    private int start;
    private int size;

    /** A trail that remembers this many ticks of movement. */
    public Trail(int length) {
        if (length < 2) throw new IllegalArgumentException("A trail needs at least 2 points: " + length);
        this.capacity = length;
        this.points = new double[length * 3];
    }

    /** Adds the newest position, dropping the oldest once the trail is full. */
    public void add(double x, double y, double z) {
        int index;
        if (this.size < this.capacity) {
            index = (this.start + this.size++) % this.capacity;
        } else {
            index = this.start;
            this.start = (this.start + 1) % this.capacity;
        }
        this.points[index * 3] = x;
        this.points[index * 3 + 1] = y;
        this.points[index * 3 + 2] = z;
    }

    public void add(Vec3 position) {
        this.add(position.x, position.y, position.z);
    }

    /** Drops the oldest point: called every tick after the thing stopped, it lets the trail catch up and vanish. */
    public void shrink() {
        if (this.size > 0) {
            this.start = (this.start + 1) % this.capacity;
            this.size--;
        }
    }

    public void clear() {
        this.size = 0;
        this.start = 0;
    }

    public int size() {
        return this.size;
    }

    /** A coordinate (0 x, 1 y, 2 z) of a point, 0 being the oldest. */
    public double coordinate(int point, int axis) {
        if (point < 0 || point >= this.size) throw new IndexOutOfBoundsException(point);
        return this.points[(this.start + point) % this.capacity * 3 + axis];
    }
}
