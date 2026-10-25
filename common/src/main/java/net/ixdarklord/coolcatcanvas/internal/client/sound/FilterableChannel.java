package net.ixdarklord.coolcatcanvas.internal.client.sound;

/** A sound channel the filters can be applied to again (the game's {@code Channel}, through {@code ChannelMixin}). */
public interface FilterableChannel {
    /** Asks the providers again for this channel's sound and applies their filter. On the sound thread. */
    void coolcatlib$refilter();
}
