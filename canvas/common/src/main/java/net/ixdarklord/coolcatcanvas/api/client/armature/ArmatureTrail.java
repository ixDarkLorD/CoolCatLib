package net.ixdarklord.coolcatcanvas.api.client.armature;

import net.ixdarklord.coolcatcanvas.api.client.vfx.Curve;
import net.ixdarklord.coolcatcanvas.api.client.vfx.Trail;
import net.ixdarklord.coolcatcanvas.api.client.vfx.VfxMaterial;
import net.ixdarklord.coolcatcanvas.internal.client.armature.ArmatureVfx;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.function.Predicate;

/**
 * A glowing ribbon left behind by a point of an armature as it moves: a sword's swing, a comet's tail, a spirit's
 * wake. It follows the point every tick while {@linkplain #when active}, and shrinks away when not. Drawn as a world
 * effect (see {@code WorldVfx}): unlit, over the scene, fading where it meets solid things.
 * <pre>{@code
 * animator.trail("blade_tip")
 *         .length(8)
 *         .width(Curve.of(0.35F, 0.0F))
 *         .colors(0xFFFFF2B0, 0x00FF6A00)
 *         .when(ctx -> animator.layer("action").isPlaying("slash"));
 * }</pre>
 * Items in slots and hands have no place in the world, so they leave no trail.
 */
public final class ArmatureTrail {
    private final String point;
    private Trail trail = new Trail(10);
    private VfxMaterial material = VfxMaterial.additive(VfxMaterial.STREAK);
    private Curve width = Curve.of(0.25F, 0.0F);
    private int head = 0xFFFFFFFF;
    private int tail = 0x00FFFFFF;
    private @Nullable Predicate<ArmatureContext> active;
    private long lastTick = Long.MIN_VALUE;

    ArmatureTrail(String point) {
        this.point = point;
        ArmatureVfx.track(this);
    }

    public String point() {
        return this.point;
    }

    /** How many ticks of path it keeps; 10 by default. */
    public ArmatureTrail length(int ticks) {
        this.trail = new Trail(Math.max(2, ticks));
        return this;
    }

    public ArmatureTrail material(VfxMaterial material) {
        this.material = material;
        return this;
    }

    /** Its width in blocks along its length, from the point (0) to the end (1). */
    public ArmatureTrail width(Curve width) {
        this.width = width;
        return this;
    }

    /** ARGB colours at the point and at the end; a clear end fades it out. */
    public ArmatureTrail colors(int head, int tail) {
        this.head = head;
        this.tail = tail;
        return this;
    }

    /** Draws it only while this holds, asked every tick; always by default. */
    public ArmatureTrail when(Predicate<ArmatureContext> active) {
        this.active = active;
        return this;
    }

    // ---- Animator's and renderer's side ----

    void feed(ArmatureFrame frame, ArmatureContext context) {
        Level level = context.level();
        if (level == null) return;
        long tick = level.getGameTime();
        if (tick == this.lastTick) return;
        this.lastTick = tick;
        Vector3f at = frame.worldPosition(this.point);
        if (at != null && (this.active == null || this.active.test(context))) this.trail.add(at.x, at.y, at.z);
        else this.trail.shrink();
    }

    /** Whether it was fed within the last two ticks: one off screen (or gone) is not drawn standing still. */
    public boolean fresh(long gameTime) {
        return gameTime - this.lastTick <= 2 && this.trail.size() > 1;
    }

    public Trail trail() {
        return this.trail;
    }

    public VfxMaterial material() {
        return this.material;
    }

    public Curve width() {
        return this.width;
    }

    public int headColor() {
        return this.head;
    }

    public int tailColor() {
        return this.tail;
    }
}
