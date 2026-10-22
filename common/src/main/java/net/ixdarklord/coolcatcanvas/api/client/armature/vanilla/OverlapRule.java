package net.ixdarklord.coolcatcanvas.api.client.armature.vanilla;

/**
 * What a vanilla model's clips do on a part another mod is moving at that moment: a part another animation mod is
 * moving, such as a weapon mod's attack swing. They are found by {@link AnimationOverlap}s.
 */
public enum OverlapRule {
    /** The other mod keeps the part; the clips leave it alone. The default: an attack swing beats an idle emote. */
    YIELD,
    /** The clips take the part over, as they would from vanilla. */
    OVERRIDE,
    /** The clips' movement is added on top of the other mod's. */
    ADD
}
