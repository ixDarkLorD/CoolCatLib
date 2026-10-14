package net.ixdarklord.coolcatcanvas.api.client.vfx;

import net.minecraft.world.phys.Vec3;

/** A rift in the world, from {@link WorldRifts#open}. It opens by itself; it closes when told to, or when its time is up. */
public interface Rift {
    enum Phase {
        /** Tearing open. */
        OPENING,
        /** Open, humming. */
        OPEN,
        /** Closing. */
        CLOSING,
        /** Closed; it draws nothing and is forgotten. */
        GONE
    }

    RiftSpec spec();

    /** Its middle. */
    Vec3 position();

    Phase phase();

    /** How open it is, 0 to 1. */
    float openness(float partialTick);

    /** Begins closing it. Does nothing to a rift already closing or gone. */
    void close();

    /** Removes it at once, with no closing. */
    void remove();
}
