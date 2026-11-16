package net.ixdarklord.coolcatcanvas.api.client.sky;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * What a skybox's callbacks see of the current frame.
 *
 * @param skybox      the skybox being updated
 * @param player      the local player, if in a world
 * @param level       the client level, if in a world
 * @param partialTick the partial tick of the frame
 * @param time        seconds of real time since the game started, wrapping every hour
 * @param deltaTime   seconds of real time since the previous frame
 * @param age         seconds since the skybox last became visible, 0 while hidden
 * @param visibility  how visible the skybox is, from its fade: 0 to 1
 * @param dayTime     ticks into the day of the dimension's clock, 0 to 24000 (0 is sunrise)
 * @param rain        the rain level, 0 to 1
 * @param thunder     the thunder level, 0 to 1
 */
public record SkyContext(
        Skybox skybox,
        @Nullable LocalPlayer player,
        @Nullable ClientLevel level,
        float partialTick,
        float time,
        float deltaTime,
        float age,
        float visibility,
        float dayTime,
        float rain,
        float thunder
) {
    public Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    public boolean inWorld() {
        return this.player != null && this.level != null;
    }

    public boolean inDimension(ResourceKey<Level> dimension) {
        return this.level != null && this.level.dimension() == dimension;
    }

    /** Whether it's night: between dusk (13000) and dawn (23000). */
    public boolean isNight() {
        return this.dayTime >= 13000.0F && this.dayTime < 23000.0F;
    }
}
