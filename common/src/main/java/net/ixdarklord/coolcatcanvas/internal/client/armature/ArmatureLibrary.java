package net.ixdarklord.coolcatcanvas.internal.client.armature;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.ixdarklord.coolcatcanvas.api.client.armature.Animator;
import net.ixdarklord.coolcatcanvas.api.client.armature.Armature;
import net.ixdarklord.coolcatcanvas.api.client.armature.BakedArmature;
import net.ixdarklord.coolcatcanvas.api.client.armature.Clip;
import net.ixdarklord.coolcatcanvas.api.client.armature.Fabric;
import net.ixdarklord.coolcatcanvas.api.client.armature.Joint;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureMaterial;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmaturePoint;
import net.ixdarklord.coolcatcanvas.api.client.armature.ObjProp;
import net.ixdarklord.coolcatcanvas.api.armature.Motion;
import net.ixdarklord.coolcatcanvas.api.armature.Transition;
import net.ixdarklord.coolcatcanvas.api.utils.Easing;
import net.ixdarklord.coolcatcanvas.api.utils.Easings;
import net.ixdarklord.coolcatcanvas.internal.client.armature.anim.ClipSource;
import net.ixdarklord.coolcatcanvas.internal.client.armature.io.BbModelReader;
import net.ixdarklord.coolcatcanvas.internal.client.armature.io.BedrockReader;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The armatures read so far, by id. An armature is read the first time it is asked for and again after resource packs reload
 * ({@code ShaderManagerMixin}); textures inside Blockbench files are uploaded as they are read and dropped on reload.
 */
public final class ArmatureLibrary {
    private static final String FOLDER = "armatures/";
    private static final Map<Identifier, BakedArmature> ARMATURES = new HashMap<>();
    private static final List<Identifier> UPLOADED = new ArrayList<>();

    private ArmatureLibrary() {
    }

    public static BakedArmature get(Identifier id) {
        if (id.equals(Armature.NONE.id())) return BakedArmature.EMPTY;
        BakedArmature armature = ARMATURES.get(id);
        if (armature == null) {
            armature = load(id);
            ARMATURES.put(id, armature);
        }
        return armature;
    }

    /** The armature if it has been read since the last reload, without reading it. */
    public static @Nullable BakedArmature getIfLoaded(Identifier id) {
        return ARMATURES.get(id);
    }

    public static void reload() {
        ARMATURES.clear();
        Minecraft minecraft = Minecraft.getInstance();
        for (Identifier texture : UPLOADED) minecraft.getTextureManager().release(texture);
        UPLOADED.clear();
    }

    // ---- Finding the files ----

    private static BakedArmature load(Identifier id) {
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        try {
            String path = id.getPath();
            String base = path.startsWith(FOLDER) ? path : FOLDER + path;
            JsonObject definition = null;
            Identifier modelFile;
            if (path.endsWith(".bbmodel") || path.endsWith(".json")) {
                modelFile = id.withPath(base);
            } else {
                Identifier definitionFile = id.withPath(base + ".armature.json");
                definition = readJson(resources, definitionFile);
                modelFile = null;
                if (definition != null && definition.has("model")) modelFile = resolve(id, definitionFile, definition.get("model").getAsString());
                if (modelFile == null) {
                    Identifier bbmodel = id.withPath(base + ".bbmodel");
                    modelFile = resources.getResource(bbmodel).isPresent() ? bbmodel : id.withPath(base + ".geo.json");
                }
            }
            // Animations alone (an .animation.json with no model): their bones become joints, for clips that move
            // another model's parts (a vanilla mob's, say).
            Identifier animationsOnly = modelFile.getPath().endsWith(".animation.json") ? modelFile : null;
            JsonObject modelJson = animationsOnly != null ? null : readJson(resources, modelFile);
            if (modelJson == null && animationsOnly == null && resources.getResource(id.withPath(base + ".animation.json")).isPresent()) {
                animationsOnly = id.withPath(base + ".animation.json");
            }
            if (modelJson == null && animationsOnly == null) {
                CoolCatCanvas.LOGGER.error("No armature model for {} (looked for {})", id, modelFile);
                return BakedArmature.EMPTY;
            }
            ArmatureData data;
            List<ClipSource> clips = new ArrayList<>();
            if (animationsOnly != null) {
                data = new ArmatureData();
                clips.addAll(BedrockReader.readAnimations(readJson(resources, animationsOnly), animationsOnly));
            } else if (modelFile.getPath().endsWith(".bbmodel")) {
                data = BbModelReader.read(modelJson, modelFile);
            } else {
                data = BedrockReader.readGeometry(modelJson, modelFile);
                Identifier animations = modelFile.withPath(modelFile.getPath().replaceAll("\\.geo\\.json$|\\.json$", "") + ".animation.json");
                JsonObject animationJson = readJson(resources, animations);
                if (animationJson != null) clips.addAll(BedrockReader.readAnimations(animationJson, animations));
                if (data.textures.isEmpty()) {
                    String file = modelFile.getPath().startsWith(FOLDER) ? modelFile.getPath().substring(FOLDER.length()) : modelFile.getPath();
                    String stem = file.replaceAll("\\.geo\\.json$|\\.json$", "");
                    data.textures.add(new ArmatureData.TextureData(stem, null, modelFile.withPath("textures/armatures/" + stem + ".png"), 1, 1));
                }
            }
            clips.addAll(0, data.clips);
            if (definition != null && definition.has("animations")) {
                for (JsonElement extra : definition.getAsJsonArray("animations")) {
                    Identifier file = resolve(id, id.withPath(base + ".armature.json"), extra.getAsString());
                    JsonObject animationJson = file == null ? null : readJson(resources, file);
                    if (animationJson == null) {
                        CoolCatCanvas.LOGGER.warn("Armature {} names animation file {}, which isn't there", id, extra.getAsString());
                        continue;
                    }
                    clips.addAll(BedrockReader.readAnimations(animationJson, file));
                }
            }
            if (data.joints.isEmpty()) {
                // No model: one joint for every bone the clips name.
                for (ClipSource clip : clips) {
                    for (String bone : clip.joints.keySet()) {
                        if (!data.jointIndex().containsKey(bone)) data.addJoint(bone, -1, new Vector3f(), new Vector3f());
                    }
                }
            }
            return bake(id, data, clips, definition == null ? new JsonObject() : definition);
        } catch (Exception e) {
            CoolCatCanvas.LOGGER.error("Couldn't load armature {}", id, e);
            return BakedArmature.EMPTY;
        }
    }

    // A file a definition names: a whole id ("mymod:armatures/x.bbmodel"), or a name beside the definition ("x.bbmodel").
    private static @Nullable Identifier resolve(Identifier armature, Identifier definition, String name) {
        if (name.contains(":")) return Identifier.tryParse(name);
        String folder = definition.getPath().substring(0, definition.getPath().lastIndexOf('/') + 1);
        return Identifier.tryBuild(armature.getNamespace(), folder + name);
    }

    private static @Nullable JsonObject readJson(ResourceManager resources, Identifier file) throws Exception {
        Optional<Resource> resource = resources.getResource(file);
        if (resource.isEmpty()) return null;
        try (Reader reader = resource.get().openAsReader()) {
            JsonElement json = JsonParser.parseReader(reader);
            return json.isJsonObject() ? json.getAsJsonObject() : null;
        }
    }

    // ---- Baking ----

    private static BakedArmature bake(Identifier id, ArmatureData data, List<ClipSource> clipSources, JsonObject definition) {
        int n = data.joints.size();
        List<Joint> joints = new ArrayList<>(n);
        for (int j = 0; j < n; j++) {
            ArmatureData.JointData joint = data.joints.get(j);
            joints.add(new Joint(joint.name, j, joint.parent, joint.pivot, joint.rotation));
        }

        // Materials: named in the definition, passed down to children.
        Map<String, ArmatureMaterial> named = new HashMap<>();
        if (definition.has("materials")) {
            for (Map.Entry<String, JsonElement> entry : definition.getAsJsonObject("materials").entrySet()) {
                ArmatureMaterial material = ArmatureMaterial.byName(entry.getValue().getAsString());
                if (material == null) CoolCatCanvas.LOGGER.warn("Armature {}: no material called {}", id, entry.getValue().getAsString());
                else named.put(entry.getKey(), material);
            }
        }
        ArmatureMaterial[] materials = new ArmatureMaterial[n];
        for (int j = 0; j < n; j++) {
            ArmatureMaterial own = named.get(data.joints.get(j).name);
            int parent = data.joints.get(j).parent;
            materials[j] = own != null ? own : parent >= 0 ? materials[parent] : ArmatureMaterial.CUTOUT;
        }

        // Faces, grouped by texture and material.
        Map<Long, List<ArmatureData.Face>[]> grouped = new LinkedHashMap<>();
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (ArmatureData.Face face : data.faces) {
            long key = ((long) face.texture << 8) | materials[face.joint].ordinal();
            @SuppressWarnings("unchecked")
            List<ArmatureData.Face>[] byJoint = grouped.computeIfAbsent(key, k -> new List[n]);
            if (byJoint[face.joint] == null) byJoint[face.joint] = new ArrayList<>();
            byJoint[face.joint].add(face);
            for (int c = 0; c < 4; c++) {
                float x = face.positions[c * 3], y = face.positions[c * 3 + 1], z = face.positions[c * 3 + 2];
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);
            }
        }
        List<ArmatureMesh.Group> groups = new ArrayList<>();
        // Cloth: sheets of joints whose faces are skinned to their neighbours, so they bend as one.
        List<ArmatureCloth.Sheet> sheets = ArmatureCloth.read(id, definition, data.jointIndex());
        Matrix4f[] bind = sheets.isEmpty() ? null : ArmatureCloth.bind(joints);
        grouped.forEach((key, byJoint) -> {
            float[][] packed = new float[n][];
            for (int j = 0; j < n; j++) {
                if (byJoint[j] == null) continue;
                float[] out = new float[byJoint[j].size() * ArmatureMesh.STRIDE];
                int at = 0;
                for (ArmatureData.Face face : byJoint[j]) {
                    for (int c = 0; c < 4; c++) {
                        out[at++] = face.positions[c * 3];
                        out[at++] = face.positions[c * 3 + 1];
                        out[at++] = face.positions[c * 3 + 2];
                        out[at++] = face.uvs[c * 2];
                        out[at++] = face.uvs[c * 2 + 1];
                    }
                    out[at++] = face.normal.x;
                    out[at++] = face.normal.y;
                    out[at++] = face.normal.z;
                }
                packed[j] = out;
            }
            float[][] skin = bind == null ? null : ArmatureCloth.skin(joints, bind, sheets, packed);
            groups.add(new ArmatureMesh.Group((int) (key >> 8), ArmatureMaterial.values()[(int) (key & 0xFF)], packed, skin));
        });
        AABB bounds = data.faces.isEmpty() ? new AABB(0, 0, 0, 0, 0, 0) : new AABB(minX, minY, minZ, maxX, maxY, maxZ);

        // Points.
        List<ArmaturePoint> points = new ArrayList<>();
        for (ArmatureData.PointData point : data.points) points.add(new ArmaturePoint(point.name, point.joint, point.position, point.rotation));

        // Clips, bound to these joints; a later clip of the same name replaces an earlier one.
        Map<String, Integer> jointIndex = data.jointIndex();
        Map<String, Clip> clips = new LinkedHashMap<>();
        for (ClipSource source : clipSources) clips.put(source.name, Clip.bind(source, jointIndex, n));

        // Textures.
        Identifier[] textures = new Identifier[data.textures.size()];
        Identifier[] glow = new Identifier[data.textures.size()];
        for (int slot = 0; slot < textures.length; slot++) textures[slot] = texture(id, slot, data.textures.get(slot));
        for (int slot = 0; slot < textures.length; slot++) {
            String name = stem(data.textures.get(slot).name());
            for (String suffix : new String[]{"_glow", "_emissive", "_e"}) {
                if (!name.endsWith(suffix)) continue;
                String baseName = name.substring(0, name.length() - suffix.length());
                for (int other = 0; other < textures.length; other++) {
                    if (other != slot && stem(data.textures.get(other).name()).equals(baseName)) glow[other] = textures[slot];
                }
            }
        }
        readTextureList(definition, "textures", textures);
        readTextureList(definition, "glow_textures", glow);
        if (textures.length == 0 && definition.has("textures")) {
            textures = new Identifier[1];
            readTextureList(definition, "textures", textures);
        }

        Vector3f offset = data.blockOrigin ? new Vector3f(-8.0F, 0.0F, -8.0F) : new Vector3f();
        String origin = definition.has("origin") ? definition.get("origin").getAsString() : "";
        if (origin.equals("corner")) offset.set(-8.0F, 0.0F, -8.0F);
        else if (origin.equals("center") || origin.equals("centre")) offset.zero();
        float scale = definition.has("scale") ? definition.get("scale").getAsFloat() : 1.0F;

        return new BakedArmature(id, joints, new ArmatureMesh(groups.toArray(ArmatureMesh.Group[]::new)), points, clips, textures, glow, bounds,
                offset, scale, defaults(id, definition), props(id, definition), false);
    }

    private static String stem(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("\\.png$", "");
    }

    // Slot textures from a definition: a list by slot, or an object of slot numbers.
    private static void readTextureList(JsonObject definition, String key, Identifier[] into) {
        if (!definition.has(key)) return;
        JsonElement value = definition.get(key);
        if (value.isJsonArray()) {
            for (int slot = 0; slot < value.getAsJsonArray().size() && slot < into.length; slot++) {
                Identifier texture = Identifier.tryParse(value.getAsJsonArray().get(slot).getAsString());
                if (texture != null) into[slot] = texture;
            }
        } else if (value.isJsonPrimitive() && into.length > 0) {
            Identifier texture = Identifier.tryParse(value.getAsString());
            if (texture != null) into[0] = texture;
        } else if (value.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                try {
                    int slot = Integer.parseInt(entry.getKey());
                    Identifier texture = Identifier.tryParse(entry.getValue().getAsString());
                    if (slot >= 0 && slot < into.length && texture != null) into[slot] = texture;
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    private static Identifier texture(Identifier armature, int slot, ArmatureData.TextureData texture) {
        if (texture.png() == null) return texture.location() != null ? texture.location() : Identifier.withDefaultNamespace("missingno");
        Identifier id = CoolCatCanvas.rl("armatures/" + armature.getNamespace() + "/" + armature.getPath() + "/" + slot);
        try {
            NativeImage image = NativeImage.read(texture.png());
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
            UPLOADED.add(id);
            return id;
        } catch (Exception e) {
            CoolCatCanvas.LOGGER.error("Armature {}: couldn't read its texture {}", armature, texture.name(), e);
            return Identifier.withDefaultNamespace("missingno");
        }
    }

    // OBJ models the definition puts on joints and points.
    private static List<ObjProp> props(Identifier id, JsonObject definition) {
        List<ObjProp> props = new ArrayList<>();
        if (!definition.has("props")) return props;
        for (JsonElement element : definition.getAsJsonArray("props")) {
            JsonObject json = element.getAsJsonObject();
            Identifier model = json.has("model") ? Identifier.tryParse(json.get("model").getAsString()) : null;
            Identifier texture = json.has("texture") ? Identifier.tryParse(json.get("texture").getAsString()) : null;
            if (!json.has("point") || model == null || texture == null) {
                CoolCatCanvas.LOGGER.warn("Armature {}: a prop needs \"point\", \"model\" and \"texture\"", id);
                continue;
            }
            ObjProp prop = ObjProp.of(json.get("point").getAsString(), model, texture);
            if (json.has("offset")) {
                var offset = json.getAsJsonArray("offset");
                prop = prop.offset(offset.get(0).getAsFloat(), offset.get(1).getAsFloat(), offset.get(2).getAsFloat());
            }
            if (json.has("rotation")) {
                var rotation = json.getAsJsonArray("rotation");
                prop = prop.rotate(rotation.get(0).getAsFloat(), rotation.get(1).getAsFloat(), rotation.get(2).getAsFloat());
            }
            if (json.has("scale")) prop = prop.scale(json.get("scale").getAsFloat());
            if (json.has("part")) prop = prop.part(json.get("part").getAsString());
            if (json.has("material")) {
                ArmatureMaterial material = ArmatureMaterial.byName(json.get("material").getAsString());
                if (material != null) prop = prop.material(material);
            }
            props.add(prop);
        }
        return props;
    }

    private static Transition transition(Identifier id, JsonObject json) {
        Transition transition = Transition.DEFAULT;
        if (json.has("mode")) {
            String mode = json.get("mode").getAsString().toUpperCase(Locale.ROOT);
            try {
                Transition.Mode parsed = Transition.Mode.valueOf(mode);
                transition = new Transition(transition.duration(), transition.easing(), parsed);
            } catch (IllegalArgumentException e) {
                CoolCatCanvas.LOGGER.warn("Armature {}: no transition mode called {}", id, mode);
            }
        }
        if (json.has("duration")) transition = json.get("duration").getAsFloat() <= 0.0F ? Transition.CUT : transition.withDuration(json.get("duration").getAsFloat());
        if (json.has("easing")) {
            Easing easing = Easings.byName(json.get("easing").getAsString());
            if (easing == null) CoolCatCanvas.LOGGER.warn("Armature {}: no easing called {}", id, json.get("easing").getAsString());
            else transition = transition.withEasing(easing);
        }
        return transition;
    }

    // What a definition asks every animator of the armature to do.
    private static List<Consumer<Animator>> defaults(Identifier id, JsonObject definition) {
        List<Consumer<Animator>> defaults = new ArrayList<>();
        if (definition.has("idle")) {
            Motion idle = Motion.loop(definition.get("idle").getAsString());
            defaults.add(animator -> animator.layer(Animator.BASE).drive(ctx -> idle));
        }
        if (definition.has("springs")) {
            for (Map.Entry<String, JsonElement> entry : definition.getAsJsonObject("springs").entrySet()) {
                JsonObject spring = entry.getValue().isJsonObject() ? entry.getValue().getAsJsonObject() : new JsonObject();
                String joint = entry.getKey();
                defaults.add(animator -> {
                    var s = animator.spring(joint);
                    // A fabric first, so the keys after it change just what they name.
                    if (spring.has("fabric")) {
                        Fabric fabric = Fabric.byName(spring.get("fabric").getAsString());
                        if (fabric == null) CoolCatCanvas.LOGGER.warn("Armature {}: no fabric called {}", id, spring.get("fabric").getAsString());
                        else s.fabric(fabric);
                    }
                    if (spring.has("stiffness")) s.stiffness(spring.get("stiffness").getAsFloat());
                    if (spring.has("damping")) s.damping(spring.get("damping").getAsFloat());
                    if (spring.has("gravity")) s.gravity(spring.get("gravity").getAsFloat());
                    if (spring.has("max_angle")) s.maxAngle(spring.get("max_angle").getAsFloat());
                    if (spring.has("length")) s.length(spring.get("length").getAsFloat());
                    if (spring.has("weight")) s.weight(spring.get("weight").getAsFloat());
                    // A free part: "buoyancy" lifts it, "rope" lets its string go slack, "wind" drifts it, "twist" spins it.
                    if (spring.has("buoyancy")) s.buoyancy(spring.get("buoyancy").getAsFloat());
                    if (spring.has("rope")) s.rope(spring.get("rope").getAsBoolean());
                    if (spring.has("pulls")) s.pulls(spring.get("pulls").getAsBoolean());
                    if (spring.has("wind")) s.wind(spring.get("wind").getAsFloat());
                    if (spring.has("twist")) s.twist(spring.get("twist").getAsFloat());
                    if (spring.has("simulation_distance")) s.simulationDistance(spring.get("simulation_distance").getAsFloat());
                    if (spring.has("bump")) s.bump(spring.get("bump").getAsBoolean());
                    if (spring.has("spacing")) s.spacing(spring.get("spacing").getAsFloat());
                    // Cloth: "links" weaves it to other springs' joints, "link_stiffness" how softly they bunch.
                    if (spring.has("links")) for (JsonElement link : spring.getAsJsonArray("links")) s.link(link.getAsString());
                    if (spring.has("link_stiffness")) s.linkStiffness(spring.get("link_stiffness").getAsFloat());
                    // What the cloth is: "mass", "air_drag", "flutter", "stretch", "bend", and a steady "wind_direction".
                    if (spring.has("mass")) s.mass(spring.get("mass").getAsFloat());
                    if (spring.has("air_drag")) s.airDrag(spring.get("air_drag").getAsFloat());
                    if (spring.has("flutter")) s.flutter(spring.get("flutter").getAsFloat());
                    if (spring.has("stretch")) s.stretch(spring.get("stretch").getAsFloat());
                    if (spring.has("bend")) s.bend(spring.get("bend").getAsFloat());
                    if (spring.has("follow")) s.follow(spring.get("follow").getAsFloat());
                    if (spring.has("wind_direction")) {
                        var direction = spring.getAsJsonArray("wind_direction");
                        s.windDirection(direction.get(0).getAsFloat(), direction.get(1).getAsFloat(), direction.get(2).getAsFloat());
                    }
                    if (spring.has("radius")) s.collisionRadius(spring.get("radius").getAsFloat());
                    // "collide": true (blocks and entities), or {"blocks", "entities", "radius" (pixels), "bounce", "friction"}.
                    if (spring.has("collide")) {
                        JsonElement collide = spring.get("collide");
                        if (collide.isJsonPrimitive()) {
                            s.collideBlocks(collide.getAsBoolean()).collideEntities(collide.getAsBoolean());
                        } else if (collide.isJsonObject()) {
                            JsonObject c = collide.getAsJsonObject();
                            s.collideBlocks(!c.has("blocks") || c.get("blocks").getAsBoolean());
                            s.collideEntities(!c.has("entities") || c.get("entities").getAsBoolean());
                            if (c.has("radius")) s.collisionRadius(c.get("radius").getAsFloat());
                            if (c.has("bounce")) s.bounce(c.get("bounce").getAsFloat());
                            if (c.has("friction")) s.friction(c.get("friction").getAsFloat());
                        }
                    }
                    if (spring.has("colliders")) {
                        // [{"point": "<locator>", "radius": <pixels>}, ...]: balls the joint's weight is kept out of.
                        for (JsonElement collider : spring.getAsJsonArray("colliders")) {
                            JsonObject c = collider.getAsJsonObject();
                            if (c.has("point")) s.collider(c.get("point").getAsString(), c.has("radius") ? c.get("radius").getAsFloat() : 2.0F);
                        }
                    }
                });
            }
        }
        if (definition.has("look_at")) {
            for (Map.Entry<String, JsonElement> entry : definition.getAsJsonObject("look_at").entrySet()) {
                JsonObject look = entry.getValue().isJsonObject() ? entry.getValue().getAsJsonObject() : new JsonObject();
                String joint = entry.getKey();
                defaults.add(animator -> {
                    var l = animator.lookAt(joint);
                    if (look.has("weight")) l.weight(look.get("weight").getAsFloat());
                    if (look.has("max_yaw")) l.maxYaw(look.get("max_yaw").getAsFloat());
                    if (look.has("max_pitch")) l.maxPitch(look.get("max_pitch").getAsFloat());
                    if (look.has("smoothing")) l.smoothing(look.get("smoothing").getAsFloat());
                    if (look.has("range")) l.range(look.get("range").getAsDouble());
                });
            }
        }
        // "transition": {"duration": 0.3, "easing": "cubic_out", "mode": "freeze"} for every layer, and
        // "transitions": [{"from": "run", "to": "idle", "duration": 0.4, "layer": "base"}] for pairs of clips.
        if (definition.has("transition")) {
            Transition transition = transition(id, definition.getAsJsonObject("transition"));
            defaults.add(animator -> animator.transition(transition));
        }
        if (definition.has("transitions")) {
            for (JsonElement element : definition.getAsJsonArray("transitions")) {
                JsonObject rule = element.getAsJsonObject();
                Transition transition = transition(id, rule);
                String from = rule.has("from") ? rule.get("from").getAsString() : "*";
                String to = rule.has("to") ? rule.get("to").getAsString() : "*";
                String layer = rule.has("layer") ? rule.get("layer").getAsString() : Animator.BASE;
                defaults.add(animator -> animator.layer(layer).transition(from, to, transition));
            }
        }
        if (definition.has("hidden")) {
            for (JsonElement joint : definition.getAsJsonArray("hidden")) {
                String name = joint.getAsString();
                defaults.add(animator -> animator.hide(name));
            }
        }
        if (definition.has("glowing")) {
            for (JsonElement joint : definition.getAsJsonArray("glowing")) {
                String name = joint.getAsString();
                defaults.add(animator -> animator.glow(name, true));
            }
        }
        return defaults;
    }
}
