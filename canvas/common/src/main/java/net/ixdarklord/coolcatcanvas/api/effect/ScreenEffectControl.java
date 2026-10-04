package net.ixdarklord.coolcatcanvas.api.effect;

import net.ixdarklord.coolcatcore.api.network.Network;
import net.ixdarklord.coolcatcanvas.internal.effect.ScreenEffectPayload;
import net.ixdarklord.coolcatcanvas.internal.effect.ScreenEffectPayload.Action;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Drives a player's client-side screen effects from the server, by the id they were registered under with
 * {@code ScreenEffects.register}. Clients without that effect ignore the command.
 * <p>
 * Server state isn't kept: a client that relogs starts with its effects off, so resend what should persist (on join,
 * for instance).
 */
public final class ScreenEffectControl {
    private ScreenEffectControl() {}

    public static void enable(ServerPlayer player, ResourceLocation effect) {
        send(player, effect, Action.ENABLE, 0, "");
    }

    public static void disable(ServerPlayer player, ResourceLocation effect) {
        send(player, effect, Action.DISABLE, 0, "");
    }

    /** Fades in, stays for {@code ticks}, then fades out. */
    public static void enableFor(ServerPlayer player, ResourceLocation effect, int ticks) {
        send(player, effect, Action.ENABLE_FOR, ticks, "");
    }

    public static void enableInstantly(ServerPlayer player, ResourceLocation effect) {
        send(player, effect, Action.ENABLE_INSTANTLY, 0, "");
    }

    public static void disableInstantly(ServerPlayer player, ResourceLocation effect) {
        send(player, effect, Action.DISABLE_INSTANTLY, 0, "");
    }

    /** Moves the effect's manual strength to {@code strength} over {@code ticks}, 0 for instantly. */
    public static void setStrength(ServerPlayer player, ResourceLocation effect, float strength, int ticks) {
        send(player, effect, Action.SET_STRENGTH, ticks, "", strength);
    }

    /** Moves a uniform to {@code values} (at most 16) over {@code ticks}, 0 for instantly. */
    public static void setUniform(ServerPlayer player, ResourceLocation effect, String uniform, int ticks, float... values) {
        send(player, effect, Action.SET_UNIFORM, ticks, uniform, values);
    }

    public static void resetUniforms(ServerPlayer player, ResourceLocation effect) {
        send(player, effect, Action.RESET_UNIFORMS, 0, "");
    }

    /**
     * Tints the player's screen until {@link #disable}: the client makes the tint under {@code tint} if it has none
     * yet (see {@code ScreenEffects.tint}), so nothing needs registering on either side.
     *
     * @param argb the color; its alpha is how strong the tint is
     */
    public static void tint(ServerPlayer player, ResourceLocation tint, int argb) {
        tint(player, tint, argb, 0);
    }

    /** Tints the player's screen for {@code ticks}, fading in and out: a flash. 0 keeps it until {@link #disable}. */
    public static void tint(ServerPlayer player, ResourceLocation tint, int argb, int ticks) {
        send(player, tint, Action.TINT, ticks, "", (argb >> 16 & 0xFF) / 255.0F, (argb >> 8 & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F, (argb >>> 24) / 255.0F);
    }

    private static void send(ServerPlayer player, ResourceLocation effect, Action action, int ticks, String uniform, float... values) {
        Network.sendToPlayer(player, new ScreenEffectPayload(effect, action, Math.max(0, ticks), uniform, values));
    }
}
