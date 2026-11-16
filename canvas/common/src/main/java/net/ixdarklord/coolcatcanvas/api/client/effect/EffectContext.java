package net.ixdarklord.coolcatcanvas.api.client.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * What a screen effect's callbacks see of the current frame.
 *
 * @param effect      the effect being updated
 * @param player      the local player, if in a world
 * @param level       the client level, if in a world
 * @param partialTick the partial tick of the frame
 * @param time        seconds of real time since the game started, wrapping every hour
 * @param deltaTime   seconds of real time since the previous frame
 * @param age         seconds since the effect last became visible, 0 while hidden
 * @param strength    the effect's strength from fading and {@link ScreenEffect#setStrength}, before its
 *                    {@linkplain ScreenEffect#strength(StrengthFunction) strength function}
 * @param width       the framebuffer's width in pixels
 * @param height      the framebuffer's height in pixels
 */
public record EffectContext(
        ScreenEffect effect,
        @Nullable LocalPlayer player,
        @Nullable ClientLevel level,
        float partialTick,
        float time,
        float deltaTime,
        float age,
        float strength,
        int width,
        int height
) {
    public Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    public boolean inWorld() {
        return this.player != null && this.level != null;
    }

    /** A function of the frame deciding how strong an effect is, from 0 to 1. */
    @FunctionalInterface
    public interface StrengthFunction {
        float strength(EffectContext context);
    }
}
