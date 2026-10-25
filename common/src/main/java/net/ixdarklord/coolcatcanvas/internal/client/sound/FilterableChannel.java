package net.ixdarklord.coolcatcanvas.internal.client.sound;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

/** A sound channel the filters can be applied to again (the game's {@code Channel}, through {@code ChannelMixin}). */
public interface FilterableChannel {
    /** Which sound the channel plays, set as it starts ({@code SoundEngineMixin}). On the sound thread. */
    void coolcatlib$setSound(Identifier sound, SoundSource source);

    /** Asks the providers again for this channel's sound and applies their filter. On the sound thread. */
    void coolcatlib$refilter();
}
