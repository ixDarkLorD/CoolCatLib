package net.ixdarklord.coolcatcanvas.internal.client.cutscene;

import net.ixdarklord.coolcatcanvas.api.cutscene.Cutscene;
import net.ixdarklord.coolcatcanvas.api.utils.Easing;
import net.minecraft.util.Mth;

// The letterbox bars asked for through the Letterbox API: how far in they are, sliding between shown and hidden.
// CutsceneOverlay draws them, together with a playing cutscene's own.
public final class LetterboxManager {
    private static float aspect = Cutscene.CINEMATIC_ASPECT;
    private static boolean shown;
    private static float from;
    private static float amount;
    private static float oldAmount;
    private static int age;
    private static int ticks;

    private LetterboxManager() {}

    public static void show(float newAspect, int slideTicks) {
        aspect = newAspect;
        slide(true, slideTicks);
    }

    public static void hide(int slideTicks) {
        slide(false, slideTicks);
    }

    private static void slide(boolean show, int slideTicks) {
        shown = show;
        from = amount;
        age = 0;
        ticks = Math.max(0, slideTicks);
        if (ticks == 0) amount = oldAmount = show ? 1.0F : 0.0F;
    }

    public static boolean isShown() {
        return shown;
    }

    static void reset() {
        shown = false;
        amount = oldAmount = 0.0F;
        ticks = 0;
    }

    static void tick() {
        oldAmount = amount;
        float target = shown ? 1.0F : 0.0F;
        if (age < ticks) {
            age++;
            amount = Mth.lerp(Easing.SINE_IN_OUT.apply((float) age / ticks), from, target);
        } else {
            amount = target;
        }
    }

    static float amount(float partialTick) {
        return Mth.lerp(partialTick, oldAmount, amount);
    }

    static float aspect() {
        return aspect;
    }
}
