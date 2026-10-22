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
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads Bedrock's model and animation files ({@code .geo.json}, {@code .animation.json}), the ones Blockbench exports
 * and other tools share. Bedrock mirrors the X axis against Blockbench's, so positions and pivots flip X and turns flip
 * their X and Y as they are read; animation values keep Bedrock's axes, as Blockbench's own do.
 */
public final class BedrockReader {
    private BedrockReader() {
    }

    // ---- Geometry ----

    /** The first model in a geometry file. */
    public static ArmatureData readGeometry(JsonObject json, Identifier file) {
        ArmatureData data = new ArmatureData();
        JsonObject geometry = null;
        float textureWidth = 64, textureHeight = 64;
        if (json.has("minecraft:geometry")) {
            JsonArray all = json.getAsJsonArray("minecraft:geometry");
            if (!all.isEmpty()) {
                geometry = all.get(0).getAsJsonObject();
                JsonObject description = geometry.has("description") ? geometry.getAsJsonObject("description") : new JsonObject();
                textureWidth = JsonValues.number(description, "texture_width", 64);
                textureHeight = JsonValues.number(description, "texture_height", 64);
            }
        } else {
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                if (entry.getKey().startsWith("geometry.") && entry.getValue().isJsonObject()) {
                    geometry = entry.getValue().getAsJsonObject();
                    textureWidth = JsonValues.number(geometry, "texturewidth", 64);
                    textureHeight = JsonValues.number(geometry, "textureheight", 64);
                    break;
                }
            }
        }
        if (geometry == null || !geometry.has("bones")) return data;

        Map<String, Integer> byName = new HashMap<>();
        List<String> parents = new ArrayList<>();
        List<JsonObject> bones = new ArrayList<>();
        for (JsonElement element : geometry.getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            String name = JsonValues.string(bone, "name", "bone" + bones.size());
            Vector3f pivot = flipPosition(JsonValues.vec(bone.get("pivot"), 0.0F));
            Vector3f rotation = flipRotation(JsonValues.vec(bone.has("rotation") ? bone.get("rotation") : bone.get("bind_pose_rotation"), 0.0F));
            byName.put(name, data.addJoint(name, -1, pivot, rotation));
            parents.add(JsonValues.string(bone, "parent", ""));
            bones.add(bone);
        }
        for (int j = 0; j < bones.size(); j++) {
            Integer parent = byName.get(parents.get(j));
            data.joints.get(j).parent = parent == null || parent == j ? -1 : parent;
        }
        for (int j = 0; j < bones.size(); j++) {
            JsonObject bone = bones.get(j);
            boolean boneMirror = JsonValues.bool(bone, "mirror", false);
            float boneInflate = JsonValues.number(bone, "inflate", 0.0F);
            if (bone.has("cubes")) {
                for (JsonElement cube : bone.getAsJsonArray("cubes")) addCube(data, j, cube.getAsJsonObject(), boneMirror, boneInflate, textureWidth, textureHeight);
            }
            if (bone.has("locators")) {
                for (Map.Entry<String, JsonElement> locator : bone.getAsJsonObject("locators").entrySet()) {
                    JsonElement value = locator.getValue();
                    Vector3f position, rotation = new Vector3f();
                    if (value.isJsonObject()) {
                        position = JsonValues.vec(value.getAsJsonObject().get("offset"), 0.0F);
                        rotation = flipRotation(JsonValues.vec(value.getAsJsonObject().get("rotation"), 0.0F));
                    } else {
                        position = JsonValues.vec(value, 0.0F);
                    }
                    data.points.add(new ArmatureData.PointData(locator.getKey(), j, flipPosition(position), rotation));
                }
            }
        }
        data.sortJoints();
        return data;
    }

    private static void addCube(ArmatureData data, int joint, JsonObject cube, boolean boneMirror, float boneInflate, float textureWidth, float textureHeight) {
        Vector3f origin = JsonValues.vec(cube.get("origin"), 0.0F);
        Vector3f size = JsonValues.vec(cube.get("size"), 0.0F);
        Vector3f from = new Vector3f(-(origin.x + size.x), origin.y, origin.z);
        Vector3f to = new Vector3f(from).add(size);
        Vector3f pivot = cube.has("pivot") ? flipPosition(JsonValues.vec(cube.get("pivot"), 0.0F)) : new Vector3f(from).add(to).mul(0.5F);
        Vector3f rotation = flipRotation(JsonValues.vec(cube.get("rotation"), 0.0F));
        boolean mirror = cube.has("mirror") ? JsonValues.bool(cube, "mirror", false) : boneMirror;
        float inflate = cube.has("inflate") ? JsonValues.number(cube, "inflate", 0.0F) : boneInflate;
        Map<Direction, ArmatureData.CubeFace> faces = new EnumMap<>(Direction.class);
        JsonElement uv = cube.get("uv");
        if (uv != null && uv.isJsonObject()) {
            JsonObject perFace = uv.getAsJsonObject();
            for (Direction side : Direction.values()) {
                if (!perFace.has(side.getSerializedName())) continue;
                JsonObject face = perFace.getAsJsonObject(side.getSerializedName());
                Vector3f at = JsonValues.vec(face.get("uv"), 0.0F);
                Vector3f extent = face.has("uv_size") ? JsonValues.vec(face.get("uv_size"), 0.0F) : new Vector3f();
                float u0 = at.x, v0 = at.y, u1 = at.x + extent.x, v1 = at.y + extent.y;
                if (side.getAxis() == Direction.Axis.Y) {
                    // Bedrock writes top and bottom faces turned half round from Blockbench's.
                    float swapU = u0, swapV = v0;
                    u0 = u1;
                    v0 = v1;
                    u1 = swapU;
                    v1 = swapV;
                }
                int turns = Math.round(JsonValues.number(face, "uv_rotation", 0.0F) / 90.0F);
                faces.put(side, new ArmatureData.CubeFace(0, u0 / textureWidth, v0 / textureHeight, u1 / textureWidth, v1 / textureHeight, turns));
            }
        } else {
            Vector3f offset = JsonValues.vec(uv, 0.0F);
            for (Direction side : Direction.values()) {
                float[] rect = BoxUv.face(side, offset, size, mirror);
                faces.put(side, new ArmatureData.CubeFace(0, rect[0] / textureWidth, rect[1] / textureHeight, rect[2] / textureWidth, rect[3] / textureHeight, 0));
            }
        }
        data.addCube(joint, from, to, inflate, pivot, rotation, faces);
    }

    private static Vector3f flipPosition(Vector3f bedrock) {
        return bedrock.set(-bedrock.x, bedrock.y, bedrock.z);
    }

    private static Vector3f flipRotation(Vector3f bedrockDegrees) {
        return bedrockDegrees.set(-bedrockDegrees.x, -bedrockDegrees.y, bedrockDegrees.z).mul(Mth.DEG_TO_RAD);
    }

    // ---- Animations ----

    /** Every animation in an animation file. */
    public static List<ClipSource> readAnimations(JsonObject json, Identifier file) {
        List<ClipSource> clips = new ArrayList<>();
        if (!json.has("animations")) return clips;
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("animations").entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            clips.add(readAnimation(entry.getKey(), entry.getValue().getAsJsonObject(), file + " animation " + entry.getKey()));
        }
        return clips;
    }

    private static ClipSource readAnimation(String name, JsonObject animation, String where) {
        ClipSource clip = new ClipSource(name);
        JsonElement loop = animation.get("loop");
        if (loop != null && loop.isJsonPrimitive()) {
            LoopMode mode = LoopMode.byName(loop.getAsString());
            clip.loop = mode == null ? LoopMode.ONCE : mode;
        }
        clip.length = JsonValues.number(animation, "animation_length", -1.0F);
        clip.animTimeUpdate = JsonValues.optionalExpr(animation, "anim_time_update", where);
        clip.blendWeight = JsonValues.optionalExpr(animation, "blend_weight", where);
        clip.startDelay = JsonValues.number(animation, "start_delay", 0.0F);
        clip.loopDelay = JsonValues.number(animation, "loop_delay", 0.0F);
        if (animation.has("bones")) {
            for (Map.Entry<String, JsonElement> bone : animation.getAsJsonObject("bones").entrySet()) {
                if (!bone.getValue().isJsonObject()) continue;
                JsonObject channels = bone.getValue().getAsJsonObject();
                ClipSource.Channels target = clip.joint(bone.getKey());
                String at = where + " " + bone.getKey();
                if (channels.has("rotation")) target.rotation = track(channels.get("rotation"), 0.0F, at);
                if (channels.has("position")) target.position = track(channels.get("position"), 0.0F, at);
                if (channels.has("scale")) target.scale = track(channels.get("scale"), 1.0F, at);
                // Not Bedrock's own: a bend, one value (degrees) or [amount, direction], as player emote files have.
                if (channels.has("bend")) target.bend = bendTrack(channels.get("bend"), at);
            }
        }
        readTimed(animation, "sound_effects", (time, value) -> clip.events.add(new ClipSource.Event(time, ClipSource.EventKind.SOUND, JsonValues.string(value.getAsJsonObject(), "effect", ""), null, null)));
        readTimed(animation, "particle_effects", (time, value) -> {
            JsonObject effect = value.getAsJsonObject();
            String locator = JsonValues.string(effect, "locator", "");
            MolangExpr script = JsonValues.optionalExpr(effect, "pre_effect_script", where);
            clip.events.add(new ClipSource.Event(time, ClipSource.EventKind.PARTICLE, JsonValues.string(effect, "effect", ""), locator.isEmpty() ? null : locator, script));
        });
        readTimed(animation, "timeline", (time, value) -> {
            String script = value.getAsString();
            clip.events.add(new ClipSource.Event(time, ClipSource.EventKind.INSTRUCTION, script.trim(), null, JsonValues.expr(value, 0.0F, where)));
        });
        return clip;
    }

    // A channel: one value for the whole clip, or keyframes by time ("0.5": [..] or {"pre", "post", "lerp_mode"}).
    private static Track track(JsonElement channel, float fallback, String where) {
        List<Track.Key> keys = new ArrayList<>();
        if (channel.isJsonObject() && !isVectorObject(channel.getAsJsonObject())) {
            for (Map.Entry<String, JsonElement> entry : channel.getAsJsonObject().entrySet()) {
                float time;
                try {
                    time = Float.parseFloat(entry.getKey());
                } catch (NumberFormatException e) {
                    continue;
                }
                JsonElement value = entry.getValue();
                if (value.isJsonObject()) {
                    JsonObject keyframe = value.getAsJsonObject();
                    MolangExpr[] pre = keyframe.has("pre") ? JsonValues.exprs(keyframe.get("pre"), fallback, where) : null;
                    MolangExpr[] post = keyframe.has("post") ? JsonValues.exprs(keyframe.get("post"), fallback, where) : null;
                    if (pre == null) pre = post;
                    if (post == null) post = pre;
                    if (pre == null) continue;
                    Track.Interp interp = Track.Interp.byName(JsonValues.string(keyframe, "lerp_mode", "linear"));
                    keys.add(new Track.Key(time, pre, post, interp, null, null, null, null));
                } else {
                    keys.add(Track.Key.of(time, JsonValues.exprs(value, fallback, where), Track.Interp.LINEAR));
                }
            }
        } else {
            JsonElement value = channel.isJsonObject() ? channel.getAsJsonObject().get("vector") : channel;
            keys.add(Track.Key.of(0.0F, JsonValues.exprs(value, fallback, where), Track.Interp.LINEAR));
        }
        return new Track(keys);
    }

    // A bend channel: a number is the amount alone (direction forward), an array [amount, direction].
    private static Track bendTrack(JsonElement channel, String where) {
        Track track = track(channel, 0.0F, where);
        return track.bendShaped();
    }

    private static boolean isVectorObject(JsonObject object) {
        return object.has("vector");
    }

    private static void readTimed(JsonObject animation, String key, java.util.function.BiConsumer<Float, JsonElement> reader) {
        if (!animation.has(key) || !animation.get(key).isJsonObject()) return;
        for (Map.Entry<String, JsonElement> entry : animation.getAsJsonObject(key).entrySet()) {
            float time;
            try {
                time = Float.parseFloat(entry.getKey());
            } catch (NumberFormatException e) {
                continue;
            }
            JsonElement value = entry.getValue();
            if (value.isJsonArray()) {
                for (JsonElement each : value.getAsJsonArray()) reader.accept(time, each);
            } else {
                reader.accept(time, value);
            }
        }
    }
}
