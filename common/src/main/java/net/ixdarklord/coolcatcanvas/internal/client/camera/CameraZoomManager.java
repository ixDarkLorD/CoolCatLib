package net.ixdarklord.coolcatcanvas.internal.client.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;

// Camera zoom: the factor asked for, the factor shown (eased towards it each tick), and the wheel control if a mod
// set one. CameraMixin narrows the field of view by it; MouseHandlerMixin slows the mouse by it and hands it the wheel.
public final class CameraZoomManager {
    private static final float MAX = 64.0F;
    private static float target = 1.0F;
    private static float current = 1.0F;
    private static float previous = 1.0F;
    private static @Nullable BooleanSupplier scrollActive;
    private static float scrollMin = 1.0F;
    private static float scrollMax = 4.0F;
    private static float scrollStep = 0.5F;
    private static float ease = 0.35F;

    private CameraZoomManager() {}

    public static void set(float factor) {
        target = Mth.clamp(factor, 1.0F, MAX);
    }

    public static void ease(float perTick) {
        ease = Mth.clamp(perTick, 0.01F, 1.0F);
    }

    public static float target() {
        return target;
    }

    public static float current() {
        return current;
    }

    public static void scroll(@Nullable BooleanSupplier active, float min, float max, float step) {
        scrollActive = active;
        scrollMin = Math.max(1.0F, min);
        scrollMax = Math.max(scrollMin, max);
        scrollStep = Math.max(0.01F, step);
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null) {
            target = current = previous = 1.0F;
            return;
        }
        previous = current;
        current += (target - current) * ease;
        if (Math.abs(target - current) < 0.002F) current = target;
    }

    /** Whether the view is zoomed at all right now. */
    public static boolean active() {
        return current != 1.0F || previous != 1.0F;
    }

    private static float at(float partialTick) {
        return Mth.lerp(partialTick, previous, current);
    }

    /** The field of view, narrowed: the tangent of the half angle shrinks by the zoom, as through a real lens. */
    public static float fov(float vanilla, float partialTick) {
        float zoom = at(partialTick);
        if (zoom == 1.0F) return vanilla;
        double half = Math.toRadians(vanilla * 0.5);
        return (float) Math.toDegrees(2.0 * Math.atan(Math.tan(half) / zoom));
    }

    /** What the mouse's turning is multiplied by. */
    public static double sensitivity() {
        return 1.0 / current;
    }

    /** A notch of the wheel. Returns whether it was taken for zooming. */
    public static boolean onScroll(double amount) {
        Minecraft minecraft = Minecraft.getInstance();
        if (scrollActive == null || amount == 0.0 || minecraft.gui.screen() != null || minecraft.player == null) return false;
        boolean zooming;
        try {
            zooming = scrollActive.getAsBoolean();
        } catch (RuntimeException e) {
            scrollActive = null;
            return false;
        }
        if (!zooming) return false;
        target = Mth.clamp(target + (float) Math.signum(amount) * scrollStep, scrollMin, scrollMax);
        return true;
    }
}
