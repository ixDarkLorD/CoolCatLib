package net.ixdarklord.coolcatcanvas.api.client.armature;

import net.ixdarklord.coolcatcanvas.internal.client.armature.ArmatureMesh;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * An armature as loaded from its files: joints, faces, points, clips and textures. Get it from {@link Armature#get()} each time
 * rather than keeping it, since resource packs reloading replaces it.
 */
public final class BakedArmature {
    public static final BakedArmature EMPTY = new BakedArmature(Identifier.fromNamespaceAndPath("coolcatlib", "empty"), List.of(), ArmatureMesh.EMPTY,
            List.of(), Map.of(), new Identifier[0], new Identifier[0], new AABB(0, 0, 0, 0, 0, 0), new Vector3f(), 1.0F, List.of(), List.of(), true);

    private final Identifier id;
    private final Joint[] joints;
    private final Map<String, Integer> jointIndex = new HashMap<>();
    private final ArmatureMesh mesh;
    private final ArmaturePoint[] points;
    private final Map<String, ArmaturePoint> pointByName = new HashMap<>();
    private final Map<String, Clip> clips;
    private final Identifier[] textures;
    private final @Nullable Identifier[] glowTextures;
    private final AABB bounds;
    private final Vector3f offset;
    private final float scale;
    private final List<Consumer<Animator>> defaults;
    private final List<ObjProp> props;
    private final boolean empty;

    /** Built by CoolCatLib's loader; mods get armatures from {@link Armature#get()}. */
    public BakedArmature(Identifier id, List<Joint> joints, ArmatureMesh mesh, List<ArmaturePoint> points, Map<String, Clip> clips,
                    Identifier[] textures, @Nullable Identifier[] glowTextures, AABB bounds, Vector3fc offset, float scale,
                    List<Consumer<Animator>> defaults, List<ObjProp> props, boolean empty) {
        this.id = id;
        this.joints = joints.toArray(Joint[]::new);
        this.mesh = mesh;
        this.points = points.toArray(ArmaturePoint[]::new);
        this.clips = Collections.unmodifiableMap(new LinkedHashMap<>(clips));
        this.textures = textures;
        this.glowTextures = glowTextures;
        this.bounds = bounds;
        this.offset = new Vector3f(offset);
        this.scale = scale;
        this.defaults = List.copyOf(defaults);
        this.props = List.copyOf(props);
        this.empty = empty;
        List<List<Integer>> children = new ArrayList<>();
        for (Joint joint : this.joints) {
            this.jointIndex.putIfAbsent(joint.name(), joint.index());
            children.add(new ArrayList<>());
        }
        for (Joint joint : this.joints) {
            if (joint.parent() >= 0) children.get(joint.parent()).add(joint.index());
        }
        for (Joint joint : this.joints) {
            joint.setChildren(children.get(joint.index()).stream().mapToInt(Integer::intValue).toArray());
        }
        for (ArmatureMesh.Group group : mesh.groups()) {
            for (int j = 0; j < group.byJoint().length; j++) {
                if (group.byJoint()[j] != null) this.joints[j].setGeometry(true);
            }
        }
        for (ArmaturePoint point : this.points) this.pointByName.putIfAbsent(point.name(), point);
    }

    public Identifier id() {
        return this.id;
    }

    /** Whether nothing could be loaded: a missing or broken file. It draws nothing and plays nothing. */
    public boolean isEmpty() {
        return this.empty;
    }

    // ---- Joints ----

    /** Every joint, parents before their children. */
    public List<Joint> joints() {
        return List.of(this.joints);
    }

    public int jointCount() {
        return this.joints.length;
    }

    public Joint joint(int index) {
        return this.joints[index];
    }

    public @Nullable Joint joint(String name) {
        Integer index = this.jointIndex.get(name);
        return index == null ? null : this.joints[index];
    }

    /** A joint's index by name, or -1 if there is no such joint. */
    public int jointIndex(String name) {
        Integer index = this.jointIndex.get(name);
        return index == null ? -1 : index;
    }

    /** Whether {@code joint} is {@code ancestor} or somewhere below it. */
    public boolean isWithin(int joint, int ancestor) {
        for (int at = joint; at >= 0; at = this.joints[at].parent()) {
            if (at == ancestor) return true;
        }
        return false;
    }

    // ---- Points ----

    public List<ArmaturePoint> points() {
        return List.of(this.points);
    }

    public @Nullable ArmaturePoint point(String name) {
        return this.pointByName.get(name);
    }

    // ---- Clips ----

    public Collection<Clip> clips() {
        return this.clips.values();
    }

    public @Nullable Clip clip(String name) {
        Clip clip = this.clips.get(name);
        if (clip == null && !name.startsWith("animation.")) {
            // Bedrock names clips "animation.<model>.<name>"; asking for "<name>" finds it too.
            for (Map.Entry<String, Clip> entry : this.clips.entrySet()) {
                String key = entry.getKey();
                if (key.endsWith("." + name)) return entry.getValue();
            }
        }
        return clip;
    }

    public boolean hasClip(String name) {
        return this.clip(name) != null;
    }

    // ---- Looks ----

    /** The texture of each slot (a Blockbench model can have several). */
    public Identifier texture(int slot) {
        return this.textures.length == 0 ? Identifier.withDefaultNamespace("missingno") : this.textures[Math.min(slot, this.textures.length - 1)];
    }

    public int textureCount() {
        return this.textures.length;
    }

    /** The glow layer of a texture slot, drawn on top at full brightness; null if it has none. */
    public @Nullable Identifier glowTexture(int slot) {
        return slot < this.glowTextures.length ? this.glowTextures[slot] : null;
    }

    /** The space the faces fill at rest, in model pixels. */
    public AABB bounds() {
        return this.bounds;
    }

    /** Model pixels moved before drawing: (-8, 0, -8) for a Java block model, whose origin is a corner. */
    public Vector3fc offset() {
        return this.offset;
    }

    /** The size its {@code .armature.json} asks for, 1 if it doesn't. */
    public float scale() {
        return this.scale;
    }

    /** The {@code .obj} models its {@code .armature.json} puts on its joints and points. */
    public List<ObjProp> props() {
        return this.props;
    }

    /** The faces, by texture and material. Used by CoolCatLib's renderers. */
    public ArmatureMesh mesh() {
        return this.mesh;
    }

    /** Animator setup its {@code .armature.json} asks for (springs, head tracking, an idle clip), run on every new animator. */
    List<Consumer<Animator>> defaults() {
        return this.defaults;
    }

    @Override
    public String toString() {
        return "BakedArmature[" + this.id + ", " + this.joints.length + " joints, " + this.clips.size() + " clips]";
    }
}
