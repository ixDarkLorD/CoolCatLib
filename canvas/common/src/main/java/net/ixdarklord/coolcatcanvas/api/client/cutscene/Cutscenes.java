package net.ixdarklord.coolcatcanvas.api.client.cutscene;

import net.ixdarklord.coolcatcanvas.api.cutscene.Cutscene;
import net.ixdarklord.coolcatcanvas.internal.client.cutscene.CutsceneManager;
import net.ixdarklord.coolcatcanvas.internal.client.cutscene.editor.CutsceneEditor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Cutscenes on the client: the ones it knows by id, and the one playing.
 * <p>
 * A cutscene gets an id in one of three ways: {@link #register} from code, a file at
 * {@code assets/<namespace>/cutscene/<path>.json} (id {@code <namespace>:<path>}; resource packs can add and replace
 * them, and they reload with resources), or saved from the in-game editor (id {@code coolcatcanvas:saved/<name>}).
 * Registered ones win over files of the same id.
 * <pre>{@code
 * Cutscenes.register(MyMod.id("intro"), intro);
 * Cutscenes.play(MyMod.id("intro"));
 *
 * Cutscenes.play(Cutscene.builder()...build());      // or with no id at all
 * if (Cutscenes.isPlaying()) Cutscenes.stop();
 * }</pre>
 * While one plays, the camera follows it, and by its own settings the HUD and hand are hidden and the player can't
 * move, look or act. The player can pause the game as usual, and hold the skip key to end a skippable one.
 * <p>
 * {@code CutsceneEvents} reports starts, ends and markers; the server plays them through {@code CutsceneControl}.
 */
public final class Cutscenes {
    private Cutscenes() {}

    /** Gives a cutscene an id, replacing any registered under it. */
    public static void register(Identifier id, Cutscene cutscene) {
        CutsceneManager.register(id, cutscene);
    }

    public static void unregister(Identifier id) {
        CutsceneManager.unregister(id);
    }

    /** The cutscene with this id, from code, resources or the editor's saves; null when there is none. */
    public static @Nullable Cutscene get(Identifier id) {
        return CutsceneManager.get(id);
    }

    /** Every id a cutscene can be played by right now. */
    public static Set<Identifier> ids() {
        return CutsceneManager.ids();
    }

    /**
     * Plays a cutscene by id, in place of any playing.
     *
     * @return the playback, or null when there is no such cutscene or the player isn't in a world
     */
    public static @Nullable CutscenePlayback play(Identifier id) {
        return CutsceneManager.play(id, null, 0.0F, false);
    }

    /** Plays a cutscene; null when the player isn't in a world. */
    public static @Nullable CutscenePlayback play(Cutscene cutscene) {
        return CutsceneManager.play(null, cutscene, null, 0.0F, false);
    }

    /**
     * Plays a cutscene by id with its positions measured from a point, whatever its own anchor says.
     *
     * @param yaw the turn of the cutscene's space around the vertical, in degrees; 0 leaves it as written
     */
    public static @Nullable CutscenePlayback playAt(Identifier id, Vec3 origin, float yaw) {
        return CutsceneManager.play(id, origin, yaw, false);
    }

    public static @Nullable CutscenePlayback playAt(Cutscene cutscene, Vec3 origin, float yaw) {
        return CutsceneManager.play(null, cutscene, origin, yaw, false);
    }

    /** The cutscene playing now, or null. */
    public static @Nullable CutscenePlayback current() {
        return CutsceneManager.current();
    }

    public static boolean isPlaying() {
        return CutsceneManager.current() != null;
    }

    /** Stops the cutscene playing, if any. */
    public static void stop() {
        CutscenePlayback playback = CutsceneManager.current();
        if (playback != null) playback.stop();
    }

    /**
     * Opens the in-game cutscene editor: a timeline of keyframes placed from where the player flies, previewed live
     * through the camera, and saved as JSON under {@code config/coolcatcanvas/cutscenes/}. Players reach it with a
     * key that is unbound by default. Does nothing outside a world.
     */
    public static void openEditor() {
        CutsceneEditor.open();
    }
}
