package net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla;

import net.ixdarklord.coolcatcanvas.api.client.armature.BakedArmature;
import net.ixdarklord.coolcatcanvas.internal.mixin.client.ModelPartAccessor;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A vanilla model's parts by name, and the path from the root to each. Names are matched loosely ({@code right_arm},
 * {@code rightArm} and {@code RightArm} are one), so clips made on Blockbench's Bedrock or Java player models fit.
 * Everything is worked out once per model (and once per armature drawn on it, see {@link Binding}), so posing a model
 * every frame looks nothing up by name.
 */
final class ModelParts {
    private static final Map<Model<?>, ModelParts> CACHE = new WeakHashMap<>();

    private final Map<String, ModelPart> byName = new HashMap<>();
    private final Map<String, List<ModelPart>> paths = new HashMap<>();
    // Names as callers spell them, so a modifier's "rightArm" is normalized once, not every frame.
    private final Map<String, ModelPart> byRawName = new HashMap<>();
    // Weak: a resource reload replaces armatures.
    private final Map<BakedArmature, Map<Boolean, Binding>> bindings = new WeakHashMap<>();

    private ModelParts(ModelPart root) {
        this.walk(root, new ArrayList<>(List.of(root)));
    }

    static ModelParts of(Model<?> model) {
        return CACHE.computeIfAbsent(model, m -> new ModelParts(m.root()));
    }

    static String normalize(String name) {
        StringBuilder out = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c != '_' && c != '-' && c != ' ' && c != '.') out.append(Character.toLowerCase(c));
        }
        return out.toString();
    }

    private void walk(ModelPart part, List<ModelPart> path) {
        for (Map.Entry<String, ModelPart> child : ((ModelPartAccessor) (Object) part).coolcatlib$children().entrySet()) {
            String key = normalize(child.getKey());
            List<ModelPart> childPath = new ArrayList<>(path);
            childPath.add(child.getValue());
            this.byName.putIfAbsent(key, child.getValue());
            this.paths.putIfAbsent(key, Collections.unmodifiableList(childPath));
            this.walk(child.getValue(), childPath);
        }
    }

    @Nullable ModelPart part(String name) {
        ModelPart part = this.byRawName.get(name);
        if (part == null && !this.byRawName.containsKey(name)) {
            part = this.byName.get(normalize(name));
            this.byRawName.put(name, part);
        }
        return part;
    }

    /** From the root down to the part, both included; null if there is no such part. */
    @Nullable List<ModelPart> path(String name) {
        return this.paths.get(normalize(name));
    }

    /** Where the part's pivot is at rest, in model pixels (vanilla's axes: Y down). */
    @Nullable Vector3f restPivot(String name) {
        List<ModelPart> path = this.path(name);
        if (path == null) return null;
        Vector3f at = new Vector3f();
        for (ModelPart part : path) at.add(part.getInitialPose().x(), part.getInitialPose().y(), part.getInitialPose().z());
        return at;
    }

    /** An armature's joints matched to this model's parts, mirrored or not; made once and kept. */
    Binding binding(BakedArmature armature, boolean torsoJointNames) {
        return this.bindings.computeIfAbsent(armature, a -> new HashMap<>(2)).computeIfAbsent(torsoJointNames, p -> new Binding(this, armature, p));
    }

    /**
     * For each joint of an armature: the part it moves, the part it moves when mirrored (left and right swapped), and
     * the part's normalized name (for other animation libraries' parts); plus the joint that moves the whole model, if
     * any. With {@code torsoJointNames}, joints use torso joint names: {@code torso} is the body
     * part and {@code body} the whole model.
     */
    static final class Binding {
        final ModelPart[] parts;
        final ModelPart[] mirrored;
        final String[] names;
        final String[] mirroredNames;
        final int root;

        Binding(ModelParts model, BakedArmature armature, boolean torsoJointNames) {
            int n = armature.jointCount();
            this.parts = new ModelPart[n];
            this.mirrored = new ModelPart[n];
            this.names = new String[n];
            this.mirroredNames = new String[n];
            int root = -1;
            for (int j = 0; j < n; j++) {
                String name = normalize(armature.joint(j).name());
                if (name.equals("root") || torsoJointNames && name.equals("body")) {
                    if (root < 0) root = j;
                    continue;
                }
                if (torsoJointNames && name.equals("torso")) name = "body";
                String mirror = mirror(name);
                this.names[j] = name;
                this.mirroredNames[j] = mirror;
                this.parts[j] = model.byName.get(name);
                this.mirrored[j] = model.byName.get(mirror);
            }
            this.root = root;
        }

        private static String mirror(String name) {
            if (name.startsWith("left")) return "right" + name.substring(4);
            if (name.startsWith("right")) return "left" + name.substring(5);
            return name;
        }
    }
}
