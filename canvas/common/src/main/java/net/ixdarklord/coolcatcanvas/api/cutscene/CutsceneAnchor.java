package net.ixdarklord.coolcatcanvas.api.cutscene;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** What a {@link Cutscene}'s positions are measured from, when it is played without an origin of its own. */
public enum CutsceneAnchor implements StringRepresentable {
    /** World coordinates: the cutscene belongs to one place. */
    WORLD("world"),
    /** Offsets from where the player's feet are as it starts: the cutscene plays wherever they stand. */
    PLAYER("player"),
    /**
     * Offsets from the player, turned with the way they face as it starts: +Z is ahead of them, +X to their left,
     * and a yaw of 0 looks the way they look.
     */
    PLAYER_FACING("player_facing");

    public static final Codec<CutsceneAnchor> CODEC = StringRepresentable.fromEnum(CutsceneAnchor::values);

    private final String name;

    CutsceneAnchor(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
