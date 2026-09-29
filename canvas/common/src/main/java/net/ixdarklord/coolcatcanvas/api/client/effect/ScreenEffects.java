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
 * the finished frame, with uniforms that can change every frame.
 * <p>
 * CoolCatLib: Canvas ships no effects of its own: a mod brings its own {@code post_effect} JSON and fragment shaders
 * (or uses vanilla's, like {@code minecraft:invert}). Register effects once, from client code (a
 * {@code ClientModConstructor} is a good place), then turn them on and off as the game goes:
 * <pre>{@code
 * // assets/mymod/post_effect/desaturate.json, drawing assets/mymod/shaders/post/desaturate.fsh
 * ScreenEffect insanity = ScreenEffects.register(MyMod.id("insanity"), MyMod.id("desaturate"))
 *         .fade(40)
 *         .activeWhen(context -> context.inWorld() && Sanity.of(context.player()) > 0.4F)
 *         .strength(context -> Mth.inverseLerp(Sanity.of(context.player()), 0.4F, 0.8F))
 *         .setUniform("Contrast", 1.4F);
 * }</pre>
 * The server can drive registered effects too, through {@code ScreenEffectControl}.
 *
 * @see ScreenEffectDefinition
 */
public final class ScreenEffects {
    private ScreenEffects() {}

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
