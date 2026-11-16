package net.ixdarklord.coolcatcanvas.api.client.armature;

import org.joml.Vector3fc;

/**
 * A named point that moves with a joint: a Blockbench locator or null object, or a Bedrock locator. Sockets hang items
 * on them, and particles from keyframes come out of them.
 *
 * @param joint    the index of the joint it moves with
 * @param position where it is at rest, in model pixels
 * @param rotation its own turn, in radians (Z, then Y, then X, as joints turn)
 */
public record ArmaturePoint(String name, int joint, Vector3fc position, Vector3fc rotation) {
}
