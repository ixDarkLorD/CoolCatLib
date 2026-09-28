package net.ixdarklord.coolcatcanvas.api.client.sky;

import net.ixdarklord.coolcatcanvas.api.utils.Easing;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A custom sky, made with {@link Skyboxes#register}: layers of images, gradients and shaders drawn into vanilla's sky,
 * and parts of vanilla's sky hidden. Any number can show at once, drawn by ascending {@linkplain #priority priority}.
 * <p>
 * A skybox shows while it's enabled, fading in and out. Unless told otherwise with {@link #activeWhen}, one whose
 * definition lists {@code dimensions} enables itself in those; others wait for {@link #enable()} (or the server's
 * {@code SkyboxControl}).
 * <p>
 * Settings made here override the definition's, and survive resource reloads. Durations are in ticks of real time
 * (20 per second). Only use a skybox from the render thread.
 */
public interface Skybox {
    ResourceLocation id();

    /** The {@code skybox} JSON it's loaded from, or null for a definition built in code. */
    @Nullable ResourceLocation definitionId();

    // ---- Configuration (chainable) ----

    int priority();

    /** Lower priorities are drawn first, so higher ones end up on top. Defaults to the definition's. */
    Skybox priority(int priority);

    /** How it fades in and out. Defaults to the definition's durations, with {@link Easing#SINE_IN_OUT}. */
    Skybox fade(int fadeInTicks, int fadeOutTicks, Easing easing);

    default Skybox fade(int ticks) {
        return this.fade(ticks, ticks, Easing.SINE_IN_OUT);
    }

    /**
     * Enables the skybox whenever the condition holds and disables it otherwise, checked every frame. Replaces the
     * definition's {@code dimensions}; pass null to leave it to {@link #enable()} and {@link #disable()}.
     */
    Skybox activeWhen(@Nullable Predicate<SkyContext> condition);

    /** Runs every frame while the skybox is visible, before it's drawn: a place to drive its layers. */
    Skybox onFrame(@Nullable Consumer<SkyContext> callback);

    /** Replaces the parts of vanilla's sky it hides. Defaults to the definition's {@code hide}. */
    Skybox hide(VanillaSky... parts);

    /** The parts of vanilla's sky it hides. */
    Set<VanillaSky> hidden();

    // ---- State ----

    /** Fades in, or keeps the skybox on if it's already. Cancels a pending {@link #enableFor} timeout. */
    Skybox enable();

    /** Fades out. */
    Skybox disable();

    default Skybox setEnabled(boolean enabled) {
        return enabled ? this.enable() : this.disable();
    }

    default Skybox toggle() {
        return this.setEnabled(!this.isEnabled());
    }

    /** Fades in, stays for {@code ticks}, then fades out. */
    Skybox enableFor(int ticks);

    /** Shows the skybox fully right away, skipping the fade-in. */
    Skybox enableInstantly();

    /** Hides the skybox right away, skipping the fade-out. */
    Skybox disableInstantly();

    /** Whether it's enabled; it may still be fading in. */
    boolean isEnabled();

    /** Whether it's drawn this frame: enabled, or still fading out. */
    boolean isVisible();

    /** How visible it is this frame, from its fade: 0 to 1. */
    float visibility();

    // ---- Layers ----

    /**
     * The layer named {@code name} in the definition (unnamed layers are {@code layer_0}, {@code layer_1}, ...). The
     * handle can be taken before the definition has loaded.
     */
    SkyLayer layer(String name);

    /** The layers of the loaded definition, in draw order; empty before it has loaded. */
    Collection<SkyLayer> layers();

    // ---- Loading ----

    /** Whether its definition and shaders loaded, so it can be drawn. False until the first resource load, and after errors. */
    boolean isLoaded();

    /** Why it failed to load or draw, if it did. Fixed by a resource reload. */
    @Nullable String error();
}
