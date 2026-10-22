package net.ixdarklord.coolcatcanvas.internal.client.armature.io;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.ixdarklord.coolcatcanvas.api.client.armature.molang.MolangExpr;
import net.ixdarklord.coolcatcanvas.api.armature.LoopMode;
import net.ixdarklord.coolcatcanvas.internal.client.armature.ArmatureData;
import net.ixdarklord.coolcatcanvas.internal.client.armature.anim.ClipSource;
import net.ixdarklord.coolcatcanvas.internal.client.armature.anim.Track;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a Blockbench project ({@code .bbmodel}) as it is saved, so no export plugin is needed: groups become joints,
 * cubes and meshes become faces, locators and null objects become points, embedded textures come along, and every
 * animation with its sound, particle and timeline keyframes. Works for every Blockbench format (Bedrock, Java block,
 * modded entity, generic).
 */
public final class BbModelReader {
    private static final String ROOT = "__root__";

    private final JsonObject json;
    private final Identifier file;
    private final ArmatureData data = new ArmatureData();
    private final Map<String, JsonObject> elements = new HashMap<>();
    private final Map<String, JsonObject> groups = new HashMap<>();
    private final Map<String, Integer> jointByUuid = new HashMap<>();
    private final Map<String, Integer> textureByUuid = new HashMap<>();
    private final List<int[]> textureSizes = new ArrayList<>();
    private int rootJoint = -1;

    private BbModelReader(JsonObject json, Identifier file) {
        this.json = json;
        this.file = file;
    }

    public static ArmatureData read(JsonObject json, Identifier file) {
        BbModelReader reader = new BbModelReader(json, file);
        reader.readAll();
        return reader.data;
    }

    private void readAll() {
        JsonObject meta = this.json.has("meta") ? this.json.getAsJsonObject("meta") : new JsonObject();
        this.data.blockOrigin = "java_block".equals(JsonValues.string(meta, "model_format", ""));
        this.readTextures();
        if (this.json.has("elements")) {
            for (JsonElement element : this.json.getAsJsonArray("elements")) {
                JsonObject object = element.getAsJsonObject();
                this.elements.put(JsonValues.string(object, "uuid", ""), object);
            }
        }
        if (this.json.has("groups")) {
            for (JsonElement group : this.json.getAsJsonArray("groups")) {
                JsonObject object = group.getAsJsonObject();
                this.groups.put(JsonValues.string(object, "uuid", ""), object);
            }
        }
        if (this.json.has("outliner")) {
            for (JsonElement node : this.json.getAsJsonArray("outliner")) this.walk(node, -1);
        } else {
            // No outliner (a very old file): every element on one joint.
            for (JsonObject element : this.elements.values()) this.addElement(element, this.root());
        }
        int[] newIndex = this.data.sortJoints();
        this.jointByUuid.replaceAll((uuid, index) -> newIndex[index]);
        if (this.json.has("animations")) {
            for (JsonElement animation : this.json.getAsJsonArray("animations")) this.readAnimation(animation.getAsJsonObject());
        }
    }

    // ---- Textures ----

    private void readTextures() {
        int defaultWidth = 16, defaultHeight = 16;
        if (this.json.has("resolution")) {
            JsonObject resolution = this.json.getAsJsonObject("resolution");
            defaultWidth = (int) JsonValues.number(resolution, "width", 16);
            defaultHeight = (int) JsonValues.number(resolution, "height", 16);
        }
        if (!this.json.has("textures")) return;
        JsonArray textures = this.json.getAsJsonArray("textures");
        for (int i = 0; i < textures.size(); i++) {
            JsonObject texture = textures.get(i).getAsJsonObject();
            String name = JsonValues.string(texture, "name", "texture_" + i);
            int width = (int) JsonValues.number(texture, "uv_width", defaultWidth);
            int height = (int) JsonValues.number(texture, "uv_height", defaultHeight);
            byte[] png = null;
            String source = JsonValues.string(texture, "source", "");
            int comma = source.indexOf(',');
            if (source.startsWith("data:") && comma > 0) {
                try {
                    png = Base64.getDecoder().decode(source.substring(comma + 1));
                } catch (IllegalArgumentException ignored) {
                }
            }
            Identifier location = null;
            if (png == null) {
                // Not embedded: a texture of the same name beside the model's own textures.
                String stem = name.toLowerCase(Locale.ROOT).replaceAll("\\.png$", "").replaceAll("[^a-z0-9/._-]", "_");
                location = Identifier.tryBuild(this.file.getNamespace(), "textures/armatures/" + stem + ".png");
            }
            this.data.textures.add(new ArmatureData.TextureData(name, png, location, Math.max(1, width), Math.max(1, height)));
            this.textureSizes.add(new int[]{Math.max(1, width), Math.max(1, height)});
            this.textureByUuid.put(JsonValues.string(texture, "uuid", "#" + i), i);
            if (texture.has("id")) this.textureByUuid.putIfAbsent(JsonValues.string(texture, "id", ""), i);
        }
    }

    private int textureIndex(@Nullable JsonElement reference) {
        if (reference == null) return 0;
        if (reference.isJsonNull()) return -1;
        if (reference.isJsonPrimitive()) {
            if (reference.getAsJsonPrimitive().isBoolean()) return reference.getAsBoolean() ? 0 : -1;
            if (reference.getAsJsonPrimitive().isNumber()) return reference.getAsInt();
            Integer byUuid = this.textureByUuid.get(reference.getAsString());
            if (byUuid != null) return byUuid;
            try {
                return Integer.parseInt(reference.getAsString());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private int[] textureSize(int texture) {
        if (texture >= 0 && texture < this.textureSizes.size()) return this.textureSizes.get(texture);
        if (this.json.has("resolution")) {
            JsonObject resolution = this.json.getAsJsonObject("resolution");
            return new int[]{(int) JsonValues.number(resolution, "width", 16), (int) JsonValues.number(resolution, "height", 16)};
        }
        return new int[]{16, 16};
    }

    // ---- The outliner: groups (joints) holding elements and more groups ----

    private int root() {
        if (this.rootJoint < 0) this.rootJoint = this.data.addJoint(ROOT, -1, new Vector3f(), new Vector3f());
        return this.rootJoint;
    }

    private void walk(JsonElement node, int parent) {
        if (node.isJsonPrimitive()) {
            JsonObject element = this.elements.get(node.getAsString());
            if (element != null) this.addElement(element, parent >= 0 ? parent : this.root());
            return;
        }
        JsonObject group = node.getAsJsonObject();
        String uuid = JsonValues.string(group, "uuid", "");
        JsonObject described = group.has("name") ? group : this.groups.getOrDefault(uuid, group);
        if (!JsonValues.bool(described, "export", true)) return;
        String name = JsonValues.string(described, "name", "group");
        Vector3f pivot = JsonValues.vec(described.get("origin"), 0.0F);
        Vector3f rotation = JsonValues.vec(described.get("rotation"), 0.0F).mul(Mth.DEG_TO_RAD);
        int joint = this.data.addJoint(name, parent, pivot, rotation);
        this.jointByUuid.put(uuid, joint);
        JsonElement children = group.has("children") ? group.get("children") : described.get("children");
        if (children != null && children.isJsonArray()) {
            for (JsonElement child : children.getAsJsonArray()) this.walk(child, joint);
        }
    }

    private void addElement(JsonObject element, int joint) {
        if (!JsonValues.bool(element, "export", true)) return;
        String type = JsonValues.string(element, "type", "cube");
        switch (type) {
            case "cube" -> this.addCube(element, joint);
            case "mesh" -> this.addMesh(element, joint);
            case "locator", "null_object" -> {
                Vector3f position = JsonValues.vec(element.has("position") ? element.get("position") : element.get("from"), 0.0F);
                Vector3f rotation = JsonValues.vec(element.get("rotation"), 0.0F).mul(Mth.DEG_TO_RAD);
                this.data.points.add(new ArmatureData.PointData(JsonValues.string(element, "name", type), joint, position, rotation));
            }
            default -> {
            }
        }
    }

    private void addCube(JsonObject cube, int joint) {
        Vector3f from = JsonValues.vec(cube.get("from"), 0.0F);
        Vector3f to = JsonValues.vec(cube.get("to"), 0.0F);
        Vector3f origin = JsonValues.vec(cube.get("origin"), 0.0F);
        Vector3f rotation = JsonValues.vec(cube.get("rotation"), 0.0F).mul(Mth.DEG_TO_RAD);
        float inflate = JsonValues.number(cube, "inflate", 0.0F);
        Map<Direction, ArmatureData.CubeFace> faces = new EnumMap<>(Direction.class);
        JsonObject faceJson = cube.has("faces") ? cube.getAsJsonObject("faces") : new JsonObject();
        boolean boxUv = JsonValues.bool(cube, "box_uv", false);
        for (Direction side : Direction.values()) {
            String key = side.getSerializedName();
            JsonObject face = faceJson.has(key) ? faceJson.getAsJsonObject(key) : null;
            if (face == null && !boxUv) continue;
            int texture = face == null ? 0 : this.textureIndex(face.get("texture"));
            if (texture < 0) continue;
            int[] size = this.textureSize(texture);
            float[] uv;
            if (face != null && face.has("uv")) {
                Vector3f a = JsonValues.vec(face.get("uv"), 0.0F);
                JsonArray array = face.getAsJsonArray("uv");
                float u1 = array.size() > 2 ? JsonValues.number(array.get(2), 0.0F) : a.x;
                float v1 = array.size() > 3 ? JsonValues.number(array.get(3), 0.0F) : a.y;
                uv = new float[]{a.x, a.y, u1, v1};
            } else {
                uv = BoxUv.face(side, JsonValues.vec(cube.get("uv_offset"), 0.0F), new Vector3f(to).sub(from).absolute(), JsonValues.bool(cube, "mirror_uv", false));
            }
            int turns = face == null ? 0 : Math.round(JsonValues.number(face, "rotation", 0.0F) / 90.0F);
            faces.put(side, new ArmatureData.CubeFace(texture, uv[0] / size[0], uv[1] / size[1], uv[2] / size[0], uv[3] / size[1], turns));
        }
        this.data.addCube(joint, from, to, inflate, origin, rotation, faces);
    }

    private void addMesh(JsonObject mesh, int joint) {
        Vector3f origin = JsonValues.vec(mesh.get("origin"), 0.0F);
        Vector3f rotation = JsonValues.vec(mesh.get("rotation"), 0.0F).mul(Mth.DEG_TO_RAD);
        Matrix4f place = new Matrix4f().translate(origin).rotateZYX(rotation.z, rotation.y, rotation.x);
        Map<String, Vector3f> vertices = new HashMap<>();
        if (mesh.has("vertices")) {
            for (Map.Entry<String, JsonElement> entry : mesh.getAsJsonObject("vertices").entrySet()) {
                vertices.put(entry.getKey(), place.transformPosition(JsonValues.vec(entry.getValue(), 0.0F)));
            }
        }
        if (!mesh.has("faces")) return;
        for (Map.Entry<String, JsonElement> entry : mesh.getAsJsonObject("faces").entrySet()) {
            JsonObject face = entry.getValue().getAsJsonObject();
            int texture = this.textureIndex(face.get("texture"));
            if (texture < 0 || !face.has("vertices")) continue;
            List<String> keys = new ArrayList<>();
            for (JsonElement key : face.getAsJsonArray("vertices")) {
                if (vertices.containsKey(key.getAsString())) keys.add(key.getAsString());
            }
            if (keys.size() < 3) continue;
            if (keys.size() == 4) keys = sortQuad(keys, vertices);
            JsonObject uvs = face.has("uv") ? face.getAsJsonObject("uv") : new JsonObject();
            int[] size = this.textureSize(texture);
            // Faces with more than four corners are cut into a fan of quads and triangles.
            for (int start = 1; start + 1 < keys.size(); start += 2) {
                List<String> corners = new ArrayList<>(List.of(keys.getFirst(), keys.get(start), keys.get(start + 1)));
                corners.add(start + 2 < keys.size() ? keys.get(start + 2) : keys.get(start + 1));
                float[] positions = new float[12], uv = new float[8];
                for (int c = 0; c < 4; c++) {
                    Vector3f p = vertices.get(corners.get(c));
                    positions[c * 3] = p.x;
                    positions[c * 3 + 1] = p.y;
                    positions[c * 3 + 2] = p.z;
                    Vector3f t = JsonValues.vec(uvs.get(corners.get(c)), 0.0F);
                    uv[c * 2] = t.x / size[0];
                    uv[c * 2 + 1] = t.y / size[1];
                }
                Vector3f a = vertices.get(corners.get(0)), b = vertices.get(corners.get(1)), c = vertices.get(corners.get(2));
                Vector3f normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
                if (normal.lengthSquared() < 1.0E-12F) normal.set(0.0F, 1.0F, 0.0F);
                this.data.faces.add(new ArmatureData.Face(joint, texture, positions, uv, normal.normalize()));
            }
        }
    }

    // Blockbench keeps a quad's corners in any order; put them around the face, turning the way the first three do.
    private static List<String> sortQuad(List<String> keys, Map<String, Vector3f> vertices) {
        Vector3f a = vertices.get(keys.get(0)), b = vertices.get(keys.get(1)), c = vertices.get(keys.get(2));
        Vector3f normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
        if (normal.lengthSquared() < 1.0E-12F) return keys;
        normal.normalize();
        Vector3f centre = new Vector3f();
        for (String key : keys) centre.add(vertices.get(key));
        centre.div(keys.size());
        Vector3f axisU = new Vector3f(a).sub(centre).normalize();
        Vector3f axisV = new Vector3f(normal).cross(axisU);
        List<String> sorted = new ArrayList<>(keys);
        sorted.sort((k1, k2) -> {
            Vector3f d1 = new Vector3f(vertices.get(k1)).sub(centre), d2 = new Vector3f(vertices.get(k2)).sub(centre);
            double angle1 = Math.atan2(d1.dot(axisV), d1.dot(axisU)), angle2 = Math.atan2(d2.dot(axisV), d2.dot(axisU));
            return Double.compare(angle1 < -1.0E-6 ? angle1 + Math.PI * 2 : angle1, angle2 < -1.0E-6 ? angle2 + Math.PI * 2 : angle2);
        });
        return sorted;
    }

    // ---- Animations ----

    private void readAnimation(JsonObject animation) {
        String name = JsonValues.string(animation, "name", "animation");
        String where = this.file + " animation " + name;
        ClipSource clip = new ClipSource(name);
        clip.length = JsonValues.number(animation, "length", -1.0F);
        LoopMode loop = LoopMode.byName(JsonValues.string(animation, "loop", "once"));
        clip.loop = loop == null ? LoopMode.ONCE : loop;
        clip.animTimeUpdate = JsonValues.optionalExpr(animation, "anim_time_update", where);
        clip.blendWeight = JsonValues.optionalExpr(animation, "blend_weight", where);
        clip.startDelay = JsonValues.number(animation, "start_delay", 0.0F);
        clip.loopDelay = JsonValues.number(animation, "loop_delay", 0.0F);
        if (animation.has("animators")) {
            for (Map.Entry<String, JsonElement> entry : animation.getAsJsonObject("animators").entrySet()) {
                JsonObject animator = entry.getValue().getAsJsonObject();
                if (!animator.has("keyframes")) continue;
                String type = JsonValues.string(animator, "type", "bone");
                if (entry.getKey().equals("effects") || type.equals("effect")) {
                    this.readEffects(animator.getAsJsonArray("keyframes"), clip, where);
                } else if (type.equals("bone")) {
                    Integer joint = this.jointByUuid.get(entry.getKey());
                    String jointName = joint != null ? this.data.joints.get(joint).name : JsonValues.string(animator, "name", entry.getKey());
                    this.readBone(animator.getAsJsonArray("keyframes"), clip.joint(jointName), where + " " + jointName);
                }
            }
        }
        this.data.clips.add(clip);
    }

    private void readBone(JsonArray keyframes, ClipSource.Channels channels, String where) {
        Map<String, List<Track.Key>> byChannel = new LinkedHashMap<>();
        for (JsonElement element : keyframes) {
            JsonObject keyframe = element.getAsJsonObject();
            String channel = JsonValues.string(keyframe, "channel", "rotation");
            float fallback = channel.equals("scale") ? 1.0F : 0.0F;
            JsonArray points = keyframe.has("data_points") ? keyframe.getAsJsonArray("data_points") : new JsonArray();
            if (points.isEmpty()) continue;
            MolangExpr[] pre = point(points.get(0).getAsJsonObject(), fallback, where);
            MolangExpr[] post = points.size() > 1 ? point(points.get(1).getAsJsonObject(), fallback, where) : pre;
            Track.Interp interp = Track.Interp.byName(JsonValues.string(keyframe, "interpolation", "linear"));
            byChannel.computeIfAbsent(channel, c -> new ArrayList<>()).add(new Track.Key(JsonValues.number(keyframe, "time", 0.0F), pre, post, interp,
                    JsonValues.floats3(keyframe.get("bezier_left_time")), JsonValues.floats3(keyframe.get("bezier_left_value")),
                    JsonValues.floats3(keyframe.get("bezier_right_time")), JsonValues.floats3(keyframe.get("bezier_right_value"))));
        }
        byChannel.forEach((channel, keys) -> {
            Track track = new Track(keys);
            switch (channel) {
                case "rotation" -> channels.rotation = track;
                case "position" -> channels.position = track;
                case "scale" -> channels.scale = track;
                case "bend" -> channels.bend = track.bendShaped();
                default -> {
                }
            }
        });
    }

    private static MolangExpr[] point(JsonObject point, float fallback, String where) {
        return new MolangExpr[]{JsonValues.expr(point.get("x"), fallback, where), JsonValues.expr(point.get("y"), fallback, where), JsonValues.expr(point.get("z"), fallback, where)};
    }

    private void readEffects(JsonArray keyframes, ClipSource clip, String where) {
        for (JsonElement element : keyframes) {
            JsonObject keyframe = element.getAsJsonObject();
            float time = JsonValues.number(keyframe, "time", 0.0F);
            String channel = JsonValues.string(keyframe, "channel", "timeline");
            JsonArray points = keyframe.has("data_points") ? keyframe.getAsJsonArray("data_points") : new JsonArray();
            for (JsonElement p : points) {
                JsonObject point = p.getAsJsonObject();
                String script = JsonValues.string(point, "script", "");
                MolangExpr compiled = script.isBlank() ? null : JsonValues.expr(point.get("script"), 0.0F, where);
                switch (channel) {
                    case "sound" -> clip.events.add(new ClipSource.Event(time, ClipSource.EventKind.SOUND, JsonValues.string(point, "effect", ""), null, null));
                    case "particle" -> {
                        String locator = JsonValues.string(point, "locator", "");
                        clip.events.add(new ClipSource.Event(time, ClipSource.EventKind.PARTICLE, JsonValues.string(point, "effect", ""), locator.isEmpty() ? null : locator, compiled));
                    }
                    default -> clip.events.add(new ClipSource.Event(time, ClipSource.EventKind.INSTRUCTION, script.trim(), null, compiled));
                }
            }
        }
    }
}
