package net.ixdarklord.coolcatcanvas.api.client.vfx;

import net.ixdarklord.coolcatcanvas.api.vfx.VfxParams;
import net.minecraft.client.multiplayer.ClientLevel;

/** A named world effect a client can play: usually a few {@link WorldParticles} and {@link WorldVfx} calls. */
@FunctionalInterface
public interface VfxEffect {
    void play(ClientLevel level, VfxParams params);
}
