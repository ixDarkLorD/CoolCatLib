package net.ixdarklord.coolcatcanvas.api.client.vfx;

import net.ixdarklord.coolcatcanvas.api.vfx.VfxParams;
import net.ixdarklord.coolcatcanvas.internal.client.vfx.VfxManager;
import net.minecraft.resources.ResourceLocation;

/**
 * World effects by name, so the server can start them with {@code VfxControl.play}. Register from a
 * {@code ClientModConstructor}:
 * <pre>{@code
 * VfxEffects.register(MyMod.id("impact"), (level, params) -> {
 *     WorldParticles.burst(EMBER, params.position(), Math.round(20 * params.scale()), 0.1, 0.15);
 *     WorldVfx.flash(VfxMaterial.additive(VfxMaterial.GLOW), params.position(), Curve.of(0.2F, 1.2F), 0xFF000000 | params.color(), 6);
 * });
 * }</pre>
 */
public final class VfxEffects {
    private VfxEffects() {}

    /** Registers an effect under an id; registering it again replaces it. */
    public static void register(ResourceLocation id, VfxEffect effect) {
        VfxManager.registerEffect(id, effect);
    }

    public static void unregister(ResourceLocation id) {
        VfxManager.unregisterEffect(id);
    }

    /** Plays a registered effect on this client alone. Returns false if nothing is registered under the id. */
    public static boolean play(ResourceLocation id, VfxParams params) {
        return VfxManager.playEffect(id, params);
    }
}
