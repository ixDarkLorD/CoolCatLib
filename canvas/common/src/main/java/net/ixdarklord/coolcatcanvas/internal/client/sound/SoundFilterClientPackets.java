package net.ixdarklord.coolcatcanvas.internal.client.sound;

import net.ixdarklord.coolcatcanvas.api.client.sound.SoundFilter;
import net.ixdarklord.coolcatcanvas.api.client.sound.SoundFilters;
import net.ixdarklord.coolcatcanvas.internal.sound.SoundFilterPayload;

// What the client does with the server's sound filter packets.
public final class SoundFilterClientPackets {
    private SoundFilterClientPackets() {}

    public static void handle(SoundFilterPayload payload) {
        // No fade from a server takes longer than a minute.
        int fade = Math.clamp(payload.fadeTicks(), 0, 1200);
        if (payload.clear()) SoundFilters.clear(payload.id(), fade);
        else SoundFilters.set(payload.id(), new SoundFilter(payload.gain(), payload.gainHF(), payload.distortion(), payload.reverb()), fade);
    }
}
