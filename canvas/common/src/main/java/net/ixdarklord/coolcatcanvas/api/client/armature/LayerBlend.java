package net.ixdarklord.coolcatcanvas.api.client.armature;

/** How a layer's clips combine with the layers under it. */
public enum LayerBlend {
    /** Takes over the joints its clips move, by the layer's weight: a 0.5 weight is halfway between. */
    OVERRIDE,
    /** Adds its movement on top of whatever the layers under it do: breathing over walking, a flinch over anything. */
    ADDITIVE
}
