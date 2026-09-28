package net.ixdarklord.coolcatcanvas.api.client.sky;

import net.ixdarklord.coolcatcanvas.internal.client.sky.SkyboxManager;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Optional;

/**
 * Custom skies: images wrapped around the sky (cubemaps and panoramas), images placed in it (sprites), gradients and
 * shaders, each able to rotate, animate frame by frame and fade by time of day, weather or code; and parts of vanilla's
 * sky hidden while they show.
 * <p>
 * Register skyboxes once, from client code (a {@code ClientModConstructor} is a good place):
 * <pre>{@code
 * Skybox bloodMoon = Skyboxes.register(MyMod.id("blood_moon"), SkyboxDefinition.builder()
 *         .hide(VanillaSky.MOON)
 *         .layer(SkyLayerDefinition.gradient(0xFF300000, 0xFF801010, 0xFF100000, 2.0F).blend(SkyBlend.MULTIPLY))
 *         .layer(SkyLayerDefinition.sprite(MyMod.id("textures/sky/red_moon.png"), 25.0F)
 *                 .name("moon")
 *                 .rotation(SkyLayerDefinition.Rotation.withMoon())
 *                 .stage(SkyLayerStage.ABOVE_CELESTIALS))
 *         .build())
 *         .fade(100)
 *         .activeWhen(context -> context.inWorld() && context.isNight() && BloodMoon.isActive(context.level()));
 * }</pre>
 * Resource packs can add skyboxes without code, in {@code assets/<namespace>/skybox/<path>.json} with a list of
 * {@code dimensions}; see {@link SkyboxDefinition}. The server can drive registered skyboxes through
 * {@code SkyboxControl}.
 * <p>
 * Cost: a skybox that isn't visible costs nothing. A visible one draws each visible layer with one draw call, from
 * shared static meshes, with no allocation per frame.
 */
public final class Skyboxes {
    private Skyboxes() {}

    /**
     * Registers a skybox from a definition built in code. Its textures and shaders still come from resources.
     *
     * @throws IllegalArgumentException if the id is taken by another registered skybox
     */
    public static Skybox register(ResourceLocation id, SkyboxDefinition definition) {
        return SkyboxManager.register(id, null, definition);
    }

    /**
     * Registers a skybox loaded from {@code assets/<namespace>/skybox/<path>.json}. It's loaded with the game's
     * resources, and reloaded with them.
     *
     * @throws IllegalArgumentException if the id is taken by another registered skybox
     */
    public static Skybox register(ResourceLocation id, ResourceLocation definition) {
        return SkyboxManager.register(id, definition, null);
    }

    /** A registered skybox, or one a resource pack added (once resources have loaded). */
    public static Optional<Skybox> get(ResourceLocation id) {
        return Optional.ofNullable(SkyboxManager.get(id));
    }

    public static Collection<Skybox> all() {
        return SkyboxManager.all();
    }

    /** Removes a skybox and frees what it holds. */
    public static boolean unregister(ResourceLocation id) {
        return SkyboxManager.unregister(id);
    }

    /** Hides every skybox at once, back to vanilla's sky. */
    public static void disableAllInstantly() {
        for (Skybox skybox : all()) skybox.disableInstantly();
    }
}
