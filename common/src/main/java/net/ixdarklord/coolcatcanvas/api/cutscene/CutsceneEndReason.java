package net.ixdarklord.coolcatcanvas.api.cutscene;

/** Why a cutscene stopped playing. */
public enum CutsceneEndReason {
    /** It ran to its end. */
    FINISHED,
    /** The player skipped it. */
    SKIPPED,
    /** Code or the server stopped it. */
    STOPPED,
    /** Another cutscene started in its place. */
    REPLACED,
    /** The player left the world. */
    LEFT_WORLD;

    /** Whether the player saw it through. */
    public boolean completed() {
        return this == FINISHED;
    }
}
