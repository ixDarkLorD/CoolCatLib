package net.ixdarklord.coolcatcanvas.api.client.camera;

import net.ixdarklord.coolcatcanvas.internal.client.camera.CameraZoomManager;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Zooms the camera: a spyglass of a mod's own, a scope, a lens. The view narrows smoothly to the zoom asked for, and
 * the mouse slows by as much, so aiming stays as steady at 4x as at 1x.
 * <pre>{@code
 * CameraZoom.set(3.0F);        // three times closer; eases in
 * CameraZoom.reset();          // back to the player's own field of view
 *
 * // The wheel zooms while the lens is raised (and scrolls the hotbar as usual when it is not).
 * CameraZoom.scroll(() -> MyLens.isRaised(), 1.0F, 4.0F, 0.5F);
 * }</pre>
 * Zoom is client state. Whoever sets it resets it: an item raised sets it each tick it is raised and resets it when
 * lowered. It is cleared when the player leaves the world.
 */
public final class CameraZoom {
    private CameraZoom() {}

    /** Zooms to a factor: 1 is the player's own view, 2 shows half as wide a field. Eases there over a few ticks. */
    public static void set(float factor) {
        CameraZoomManager.set(factor);
    }

    /**
     * How quickly the view eases to the zoom asked for: the share of the way left that it covers each tick. 0.35 by
     * default (snappy); around 0.15 is a slow, heavy lens; 1 is instant.
     */
    public static void ease(float perTick) {
        CameraZoomManager.ease(perTick);
    }

    /** Back to the player's own view. */
    public static void reset() {
        CameraZoomManager.set(1.0F);
    }

    /** The zoom being eased towards. */
    public static float target() {
        return CameraZoomManager.target();
    }

    /** The zoom as drawn this tick, on its way to {@link #target()}. */
    public static float current() {
        return CameraZoomManager.current();
    }

    /**
     * Lets the mouse wheel zoom. While {@code active} says so (and no screen is open), a notch of the wheel changes
     * the zoom by {@code step} between {@code min} and {@code max}, and does nothing else. One wheel control at a
     * time: a second call replaces the first.
     *
     * @param active when the wheel zooms; null removes the control
     */
    public static void scroll(@Nullable BooleanSupplier active, float min, float max, float step) {
        CameraZoomManager.scroll(active, min, max, step);
    }
}
