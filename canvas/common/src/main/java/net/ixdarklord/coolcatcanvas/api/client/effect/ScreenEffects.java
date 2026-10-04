package net.ixdarklord.coolcatcanvas.api.client.effect;

import net.ixdarklord.coolcatcanvas.internal.client.effect.ScreenEffectManager;
import net.ixdarklord.coolcatcanvas.internal.client.effect.gui.ScreenEffectsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;

/**
 * Full-screen post-processing effects: color grading, distortion, blur, and whatever else a fragment shader can do to
 * the finished frame, with uniforms that can change every frame. Every effect is a {@link ScreenEffect}: it fades in
 * and out, can be pulsed, driven by a condition or by the server, and stacks with any number of others.
 *
 * <h2>A tint, with no assets</h2>
 * The one effect CoolCatLib: Canvas ships. Call {@link #tint} right where it's needed:
 * <pre>{@code
 * ScreenEffects.tint(MyMod.id("hurt"), 0x59FF0000).enableFor(5);    // a red flash
 * ScreenEffects.tint(MyMod.id("frost"), 0x593366FF).enable();       // stays until disable()
 * }</pre>
 *
 * <h2>A vanilla effect</h2>
 * Vanilla's post effects register by their id. They ignore the effect's strength, so {@code autoBlend} fades them:
 * <pre>{@code
 * ScreenEffect invert = ScreenEffects.register(MyMod.id("invert"), Identifier.withDefaultNamespace("invert")).autoBlend(true);
 * invert.enableFor(40);
 * }</pre>
 *
 * <h2>Your own shader</h2>
 * A mod brings its own {@code post_effect} JSON and fragment shader (see {@link ScreenEffectDefinition}). Register it
 * once, from client code (a {@code ClientModConstructor} is a good place), then turn it on and off as the game goes,
 * or let it run itself:
 * <pre>{@code
 * // assets/mymod/post_effect/desaturate.json, drawing assets/mymod/shaders/post/desaturate.fsh
 * ScreenEffect insanity = ScreenEffects.register(MyMod.id("insanity"), MyMod.id("desaturate"))
 *         .fade(40)
 *         .activeWhen(context -> context.inWorld() && Sanity.of(context.player()) > 0.4F)
 *         .strength(context -> Mth.inverseLerp(Sanity.of(context.player()), 0.4F, 0.8F))
 *         .setUniform("Contrast", 1.4F);
 * }</pre>
 * Effects start disabled, and draw over the world unless moved to {@link ScreenEffectScope#SCREEN}. The server can
 * drive a player's effects too, tints included, through {@code ScreenEffectControl}.
 *
 * @see ScreenEffect
 * @see ScreenEffectDefinition
 */
public final class ScreenEffects {
    /**
     * The built-in tint definition: washes the frame in its {@code Color} uniform, a vec4 whose alpha is how far the
     * frame is mixed towards the color (scaled by the effect's strength). See {@link #tint}.
     */
    public static final Identifier TINT = Identifier.fromNamespaceAndPath("coolcatcanvas", "tint");
    /** The {@link #TINT} definition's color uniform. */
    public static final String TINT_COLOR = "Color";

    private ScreenEffects() {}

    /**
     * A tint over the screen, with nothing to set up: the first call for an id makes the tint, later ones recolor it
     * and hand back the same effect, so this can be called right where the tint is needed.
     * <pre>{@code
     * ScreenEffects.tint(MyMod.id("hurt"), 0x59FF0000).enableFor(5);    // a red flash
     * ScreenEffects.tint(MyMod.id("frost"), 0x593366FF).enable();       // stays until disable()
     *
     * // Over the HUD and menus too, with a slow fade.
     * ScreenEffects.tint(MyMod.id("blackout"), 0xE6000000).scope(ScreenEffectScope.SCREEN).fade(40).enable();
     *
     * // Set up once, then it runs by itself: redder the lower the player's health.
     * ScreenEffects.tint(MyMod.id("low_health"), 0x66FF0000)
     *         .activeWhen(context -> context.inWorld() && context.player().getHealth() < 8.0F)
     *         .strength(context -> 1.0F - context.player().getHealth() / 8.0F);
     * }</pre>
     * Each id is its own effect, with its own color and state. It's a {@link ScreenEffect} like any other (fades,
     * strength, conditions), drawn over the world unless moved to {@link ScreenEffectScope#SCREEN}. Unlike registered
     * effects it starts out of the effects screen; {@code selectable(true)} lists it. To change the color smoothly
     * instead of at once, animate its {@link #TINT_COLOR} uniform.
     *
     * @param argb the color; its alpha is how strong the tint is at full strength
     * @throws IllegalArgumentException if the id belongs to an effect that isn't a tint
     */
    public static ScreenEffect tint(Identifier id, int argb) {
        ScreenEffect effect = ScreenEffectManager.get(id);
        if (effect == null) {
            effect = register(id, TINT).selectable(false);
        } else if (!TINT.equals(effect.definitionId())) {
            throw new IllegalArgumentException("Screen effect " + id + " isn't a tint");
        }
        effect.uniform(TINT_COLOR).setColor(argb);
        return effect;
    }

    /**
     * Registers an effect loaded from {@code assets/<namespace>/post_effect/<path>.json}, a vanilla one included. It's
     * loaded with the game's resources, and reloaded with them.
     *
     * @throws IllegalArgumentException if the id is taken
     */
    public static ScreenEffect register(Identifier id, Identifier definition) {
        return ScreenEffectManager.register(id, definition, null);
    }

    /**
     * Registers an effect from a definition built in code. Its shaders still come from resources.
     *
     * @throws IllegalArgumentException if the id is taken
     */
    public static ScreenEffect register(Identifier id, ScreenEffectDefinition definition) {
        return ScreenEffectManager.register(id, null, definition);
    }

    public static Optional<ScreenEffect> get(Identifier id) {
        return Optional.ofNullable(ScreenEffectManager.get(id));
    }

    public static Collection<ScreenEffect> all() {
        return ScreenEffectManager.all();
    }

    /** Removes an effect and frees its GPU resources. */
    public static boolean unregister(Identifier id) {
        return ScreenEffectManager.unregister(id);
    }

    /** Hides every effect at once, e.g. for a cutscene or a clean screenshot. */
    public static void disableAllInstantly() {
        for (ScreenEffect effect : all()) effect.disableInstantly();
    }

    /** The order effects are drawn in. */
    public static ScreenEffectLayers layers() {
        return ScreenEffectManager.layers();
    }

    /**
     * The effects screen: every {@linkplain ScreenEffect#isSelectable() selectable} effect with a switch, the active
     * ones as reorderable layers with a strength slider each, previewed live over the game. Players' choices there are
     * remembered across sessions. Also bound to a key (unset by default), and in a development environment {@code /coolcatcanvas_client effects}.
     */
    public static Screen createScreen(@Nullable Screen parent) {
        return new ScreenEffectsScreen(parent);
    }

    /** Opens {@link #createScreen} over the current screen. */
    public static void openScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(createScreen(minecraft.screen));
    }
}
