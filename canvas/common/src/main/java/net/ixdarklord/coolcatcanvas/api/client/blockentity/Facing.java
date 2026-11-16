package net.ixdarklord.coolcatcanvas.api.client.blockentity;

/**
 * Whether a {@link BlockRenderer} turns what it draws to its block's facing, so a model made facing north (its front
 * toward -Z, as Blockbench shows it) faces the way the block does.
 */
public enum Facing {
    /** Not turned: drawn as it's made. */
    NONE,
    /** Turned about the upright axis to the block's {@code horizontal_facing} (or {@code facing}, if it's sideways). */
    HORIZONTAL,
    /** Turned to the block's {@code facing}, up and down included (or its {@code horizontal_facing}). */
    ALL
}
