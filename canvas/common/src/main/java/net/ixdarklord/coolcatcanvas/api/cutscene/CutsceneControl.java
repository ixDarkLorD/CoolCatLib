package net.ixdarklord.coolcatcanvas.api.cutscene;

import net.ixdarklord.coolcatcanvas.internal.cutscene.CutsceneNetwork;
import net.ixdarklord.coolcatcanvas.internal.cutscene.CutscenePayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Plays cutscenes on players' clients from the server.
 * <pre>{@code
 * CutsceneControl.play(player, MyMod.id("intro"));                    // one the client has registered or in its resources
 * CutsceneControl.play(player, MyMod.id("reveal"), cutscene);         // one only the server knows: sent whole
 * CutsceneControl.playAt(player, MyMod.id("orbit"), altar, 0.0F);     // its positions measured from the altar
 * CutsceneControl.stop(player);
 * }</pre>
 * {@code ServerCutsceneEvents.ENDED} tells when the player has seen it through, skipped it or left.
 * <p>
 * The player's client holds them still and hides their HUD, but the server does nothing to them: they can still be
 * hurt, pushed and targeted while they watch. Keep them safe yourself if the scene calls for it.
 */
public final class CutsceneControl {
    private CutsceneControl() {}

    /**
     * Plays a cutscene the player's client knows by id, placed by its own anchor.
     *
     * @return false when the player's client can't be told (no Canvas, or a fake player)
     */
    public static boolean play(ServerPlayer player, Identifier cutscene) {
        return CutsceneNetwork.play(player, cutscene, new CutscenePayload(Optional.of(cutscene), Optional.empty(), Optional.empty(), 0.0F));
    }

    /**
     * Sends a cutscene whole and plays it: nothing needs to be registered on the client.
     *
     * @param id the name it is reported under when it ends
     * @throws IllegalArgumentException if one of its easings has no name in {@code Easings}
     */
    public static boolean play(ServerPlayer player, Identifier id, Cutscene cutscene) {
        requirePortable(cutscene);
        return CutsceneNetwork.play(player, id, new CutscenePayload(Optional.of(id), Optional.of(cutscene), Optional.empty(), 0.0F));
    }

    /**
     * Plays a cutscene the client knows with its positions measured from a point, whatever its own anchor says.
     *
     * @param yaw the turn of the cutscene's space around the vertical, in degrees; 0 leaves it as written
     */
    public static boolean playAt(ServerPlayer player, Identifier cutscene, Vec3 origin, float yaw) {
        return CutsceneNetwork.play(player, cutscene, new CutscenePayload(Optional.of(cutscene), Optional.empty(), Optional.of(origin), yaw));
    }

    /** Sends a cutscene whole and plays it with its positions measured from a point. */
    public static boolean playAt(ServerPlayer player, Identifier id, Cutscene cutscene, Vec3 origin, float yaw) {
        requirePortable(cutscene);
        return CutsceneNetwork.play(player, id, new CutscenePayload(Optional.of(id), Optional.of(cutscene), Optional.of(origin), yaw));
    }

    private static void requirePortable(Cutscene cutscene) {
        if (!cutscene.isPortable()) throw new IllegalArgumentException("This cutscene uses an easing without a name; name it with Easings.register on both sides");
    }

    /** Stops whatever cutscene the player is watching, the server's or their client's own. */
    public static void stop(ServerPlayer player) {
        CutsceneNetwork.stop(player);
    }

    /** The cutscene the server started for this player that hasn't ended yet, or null. */
    public static @Nullable Identifier watching(ServerPlayer player) {
        return CutsceneNetwork.watching(player);
    }
}
