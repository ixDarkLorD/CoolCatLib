package net.ixdarklord.coolcatcanvas.api.client.effect;

import net.ixdarklord.coolcatcanvas.api.utils.Easing;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A full-screen post-processing effect, made with {@link ScreenEffects#register}. Effects start disabled; any number
 * can be active at once, drawn within their scope in {@linkplain ScreenEffects#layers() layer order}.
 * <p>
 * An effect's drawn strength, from 0 to 1, is the product of:
 * <ul>
 *     <li>its fade, which eases towards 1 while enabled and towards 0 while disabled;</li>
 *     <li>its {@linkplain #setStrength manual strength}, 1 unless changed;</li>
 *     <li>its {@linkplain #strength(EffectContext.StrengthFunction) strength function}, if it has one.</li>
 * </ul>
 * A strength of 0 skips the effect entirely. Shaders read it as {@code Strength} from
 * {@code #moj_import <coolcatcanvas:screen_effect.glsl>}, and should scale their work by it; {@link #autoBlend} does that
 * for shaders that don't.
 * <p>
 * Durations are in ticks of real time (20 per second), so effects keep animating while the game is paused. Only use an
 * effect from the render thread.
 */
public interface ScreenEffect {
    Identifier id();

    /** The {@code post_effect} JSON it's loaded from, or null for a definition built in code. */
    @Nullable Identifier definitionId();

    // ---- Presentation, for the effects screen (chainable) ----

    /** The name shown in the effects screen: by default {@code screen_effect.<namespace>.<path>}, else the path. */
    Component displayName();

    ScreenEffect displayName(Component name);

    /** A line or two shown in the effects screen, or null. */
    @Nullable Component description();

    ScreenEffect description(@Nullable Component description);

    /**
     * Whether players can turn it on and off from the {@linkplain ScreenEffects#openScreen effects screen}, where their
     * choice is remembered. True by default; turn it off for effects the mod drives on its own (an effect with an
     * {@link #activeWhen} condition is listed but can't be toggled there).
     */
    boolean isSelectable();

    ScreenEffect selectable(boolean selectable);

    // ---- Configuration (chainable) ----

    ScreenEffectScope scope();

    /** Where in the frame it's drawn. Defaults to {@link ScreenEffectScope#WORLD}. */
    ScreenEffect scope(ScreenEffectScope scope);

    int priority();

    /**
     * Where the effect starts in the {@linkplain ScreenEffects#layers() layer order}: lower priorities are drawn
     * first, so later effects process their output. Defaults to 0. Once the order is rearranged (by code or by a
     * player), it's kept until {@link ScreenEffectLayers#resetOrder()}.
     */
    ScreenEffect priority(int priority);

    /** Whether it has an {@link #activeWhen} condition deciding when it's on. */
    boolean isAutomatic();

    /** How it fades in and out. Both default to 10 ticks with {@link Easing#SINE_IN_OUT}. */
    ScreenEffect fade(int fadeInTicks, int fadeOutTicks, Easing easing);

    default ScreenEffect fade(int ticks) {
        return this.fade(ticks, ticks, Easing.SINE_IN_OUT);
    }

    /**
     * Blends the effect's output with the unprocessed image by its strength, so any shader fades smoothly, including
     * vanilla-style ones that ignore {@code Strength}. Costs three extra full-screen copies. Off by default.
     */
    ScreenEffect autoBlend(boolean autoBlend);

    /**
     * Enables the effect when the condition starts holding and disables it when it stops, checked every frame. In
     * between, the effect can still be toggled by hand (or vetoed through {@code ScreenEffectEvents.BEFORE_TOGGLE}),
     * and stays so until the condition changes again. Pass null to stop.
     */
    ScreenEffect activeWhen(@Nullable Predicate<EffectContext> condition);

    /** Scales the strength by a function of the frame, clamped to {@code [0, 1]}; null to stop. */
    ScreenEffect strength(EffectContext.@Nullable StrengthFunction function);

    /** Runs every frame before drawing, while the effect is visible: a place to drive uniforms. */
    ScreenEffect onFrame(@Nullable Consumer<EffectContext> callback);

    // ---- State ----

    /** Fades in, or keeps the effect on if it's already. Cancels a pending {@link #enableFor} timeout. */
    ScreenEffect enable();

    /** Fades out. */
    ScreenEffect disable();

    default ScreenEffect setEnabled(boolean enabled) {
        return enabled ? this.enable() : this.disable();
    }

    default ScreenEffect toggle() {
        return this.setEnabled(!this.isEnabled());
    }

    /** Fades in, stays for {@code ticks}, then fades out: a flash, a pulse, a hit. */
    ScreenEffect enableFor(int ticks);

    /** Shows the effect at full fade right away, skipping the fade-in. */
    ScreenEffect enableInstantly();

    /** Hides the effect right away, skipping the fade-out. */
    ScreenEffect disableInstantly();

    /** Whether it's enabled; it may still be fading in. */
    boolean isEnabled();

    /** Whether it's drawn this frame: enabled or still fading out, with a strength above 0. */
    boolean isVisible();

    /** Sets the manual strength, clamped to {@code [0, 1]}. */
    ScreenEffect setStrength(float strength);

    /** Moves the manual strength to {@code target} over {@code ticks}. */
    ScreenEffect animateStrength(float target, int ticks, Easing easing);

    /** The strength the effect is drawn with this frame. */
    float strength();

    // ---- Uniforms ----

    /** The uniform named {@code name} in any of the definition's uniform blocks. */
    EffectUniform uniform(String name);

    default ScreenEffect setUniform(String name, float... values) {
        this.uniform(name).set(values);
        return this;
    }

    default ScreenEffect setUniformInt(String name, int... values) {
        this.uniform(name).setInt(values);
        return this;
    }

    default ScreenEffect bindUniform(String name, EffectUniform.FloatBinding binding) {
        this.uniform(name).bind(binding);
        return this;
    }

    default ScreenEffect bindUniform(String name, EffectUniform.VectorBinding binding) {
        this.uniform(name).bind(binding);
        return this;
    }

    /** Resets every uniform to its definition's default. */
    ScreenEffect resetUniforms();

    // ---- Loading ----

    /** Whether its shaders compiled and it can be drawn. False until the first resource load, and after errors. */
    boolean isLoaded();

    /** Why it failed to load or draw, if it did. Fixed by a resource reload. */
    @Nullable String error();
}
