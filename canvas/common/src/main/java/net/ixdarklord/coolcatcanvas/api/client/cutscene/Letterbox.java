package net.ixdarklord.coolcatcanvas.api.client.cutscene;

import net.ixdarklord.coolcatcanvas.api.cutscene.Cutscene;
import net.ixdarklord.coolcatcanvas.internal.client.cutscene.LetterboxManager;

/**
 * Letterbox bars on their own, without a cutscene: for a boss entrance, a scripted moment the player still controls,
 * a photo mode. They slide in and out, sit under the HUD, and frame an aspect ratio: top and bottom on a screen
 * taller than it, left and right on a wider one.
 * <pre>{@code
 * Letterbox.show(20);                 // cinema bars, in over a second
 * Letterbox.show(1.85F, 10);
 * Letterbox.hide(20);
 * }</pre>
 * A cutscene with a letterbox of its own draws its bars regardless of these.
 */
public final class Letterbox {
    private Letterbox() {}

    /** Slides in bars framing the cinema ratio ({@link Cutscene#CINEMATIC_ASPECT}) over some ticks. */
    public static void show(int ticks) {
        show(Cutscene.CINEMATIC_ASPECT, ticks);
    }

    /**
     * Slides in bars framing an aspect ratio.
     *
     * @param aspect width over height, above 0
     * @param ticks  how long they take to arrive; 0 puts them there at once
     */
    public static void show(float aspect, int ticks) {
        if (!(aspect > 0.0F)) throw new IllegalArgumentException("Aspect ratio must be positive: " + aspect);
        LetterboxManager.show(aspect, ticks);
    }

    /** Slides the bars out over some ticks; 0 removes them at once. */
    public static void hide(int ticks) {
        LetterboxManager.hide(ticks);
    }

    /** Whether the bars are asked for; they may still be sliding in. */
    public static boolean isShown() {
        return LetterboxManager.isShown();
    }
}
