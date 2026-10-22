package net.ixdarklord.coolcatcanvas.api.client.armature;

import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * One joint of an armature: a Blockbench group or a Bedrock bone. Positions are in model pixels (16 to a block) with Y up and
 * the model facing north (-Z), as Blockbench shows it; rotations are radians.
 */
public final class Joint {
    private final String name;
    private final int index;
    private final int parent;
    private final Vector3f pivot;
    private final Vector3f rest;
    private int[] children = new int[0];
    private boolean geometry;

    public Joint(String name, int index, int parent, Vector3fc pivot, Vector3fc rest) {
        this.name = name;
        this.index = index;
        this.parent = parent;
        this.pivot = new Vector3f(pivot);
        this.rest = new Vector3f(rest);
    }

    public String name() {
        return this.name;
    }

    /** Its place in {@link BakedArmature#joints()}; parents always come before their children. */
    public int index() {
        return this.index;
    }

    /** The parent's index, or -1 for a root joint. */
    public int parent() {
        return this.parent;
    }

    /** The point it turns around. */
    public Vector3fc pivot() {
        return this.pivot;
    }

    /** Its rotation at rest, before any animation. */
    public Vector3fc restRotation() {
        return this.rest;
    }

    public int[] children() {
        return this.children.clone();
    }

    /** Whether it has cubes or meshes of its own (not counting its children's). */
    public boolean hasGeometry() {
        return this.geometry;
    }

    void setChildren(int[] children) {
        this.children = children;
    }

    void setGeometry(boolean geometry) {
        this.geometry = geometry;
    }

    @Override
    public String toString() {
        return "Joint[" + this.name + "]";
    }
}
