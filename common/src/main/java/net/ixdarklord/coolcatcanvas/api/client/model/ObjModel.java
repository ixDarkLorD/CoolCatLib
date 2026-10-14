package net.ixdarklord.coolcatcanvas.api.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A model read from a Wavefront {@code .obj} file: triangles with positions, texture coordinates and normals, in
 * named {@linkplain Part parts}. Get one from {@link ObjModels#get} (a resource, cached and reloaded with resource
 * packs) or {@link #parse} (any text).
 *
 * <p>What is read: {@code v}, {@code vt}, {@code vn} and {@code f} (faces of any number of corners, cut into
 * triangles as a fan; indices counted from the end; corners with no texture coordinate or no normal), and {@code o},
 * {@code g} and {@code usemtl}, which begin a new part. A face with no normals gets its own flat one. Materials
 * ({@code .mtl}) are not read: a part carries its material's name and the caller picks the texture, so one model can
 * be drawn in any render type. Everything else in the file is passed over.</p>
 *
 * <p>Units are the file's own: one unit is one block unless the pose is scaled. Texture coordinates are turned to
 * the game's (the file's {@code v} runs up, the game's down).</p>
 *
 * <p>Drawing goes through the game's own renderers, so a model is lit, fogged and sorted as anything else is:
 * {@link #submit} from an entity or block entity renderer, or {@link #render} into any vertex consumer. Both are made
 * for render types that draw quads with the entity vertex format ({@code RenderTypes.entityCutout(texture)},
 * {@code entityTranslucent}, {@code eyes}...): each triangle is sent as a quad with its last corner twice.</p>
 */
public final class ObjModel {
    /** A model with nothing in it: what a missing or broken file gives. */
    public static final ObjModel EMPTY = new ObjModel(List.of());
    /** Floats to a corner: position, texture coordinate, normal. */
    private static final int STRIDE = 8;

    private final List<Part> parts;
    private final Map<String, Part> byName = new LinkedHashMap<>();
    private final Vector3f min = new Vector3f(), max = new Vector3f();
    private final int triangles;

    private ObjModel(List<Part> parts) {
        this.parts = List.copyOf(parts);
        int count = 0;
        boolean first = true;
        for (Part part : this.parts) {
            this.byName.putIfAbsent(part.name(), part);
            count += part.triangles();
            for (int at = 0; at < part.corners.length; at += STRIDE) {
                float x = part.corners[at], y = part.corners[at + 1], z = part.corners[at + 2];
                if (first) {
                    this.min.set(x, y, z);
                    this.max.set(x, y, z);
                    first = false;
                } else {
                    this.min.min(new Vector3f(x, y, z));
                    this.max.max(new Vector3f(x, y, z));
                }
            }
        }
        this.triangles = count;
    }

    /** The model's parts, in the file's order. */
    public List<Part> parts() {
        return this.parts;
    }

    /** The first part of this name (an {@code o} or {@code g} line's), or null. */
    public Part part(String name) {
        return this.byName.get(name);
    }

    public boolean isEmpty() {
        return this.triangles == 0;
    }

    public int triangles() {
        return this.triangles;
    }

    /** The least corner of the box the model fills. */
    public Vector3f min() {
        return new Vector3f(this.min);
    }

    /** The greatest corner of the box the model fills. */
    public Vector3f max() {
        return new Vector3f(this.max);
    }

    /** Every part, white, with no overlay. */
    public void submit(PoseStack pose, SubmitNodeCollector collector, RenderType type, int light) {
        this.submit(pose, collector, type, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
    }

    /**
     * Hands every part to the game to draw in one render type, from a renderer's {@code submit}.
     *
     * @param light   packed light, as renderers are given it ({@code 0xF000F0} for a thing that glows)
     * @param overlay packed overlay ({@code OverlayTexture.NO_OVERLAY})
     * @param argb    a tint, {@code 0xAARRGGBB}; alpha only shows in a translucent render type
     */
    public void submit(PoseStack pose, SubmitNodeCollector collector, RenderType type, int light, int overlay, int argb) {
        if (!this.isEmpty()) {
            collector.submitCustomGeometry(pose, type, (last, consumer) -> this.render(last, consumer, light, overlay, argb));
        }
    }

    /** Writes every part into a vertex consumer. */
    public void render(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int argb) {
        for (Part part : this.parts) {
            part.render(pose, consumer, light, overlay, argb);
        }
    }

    /** Reads a model from the text of an {@code .obj} file. */
    public static ObjModel parse(String text) {
        try {
            return parse(new StringReader(text));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Reads a model from an {@code .obj} file.
     *
     * @throws IllegalArgumentException if a line that is read cannot be understood (the message names the line)
     */
    public static ObjModel parse(Reader reader) throws IOException {
        List<float[]> positions = new ArrayList<>(), coordinates = new ArrayList<>(), normals = new ArrayList<>();
        List<Part> parts = new ArrayList<>();
        String name = "default", material = "";
        FloatList corners = new FloatList();
        BufferedReader lines = reader instanceof BufferedReader buffered ? buffered : new BufferedReader(reader);
        String line;
        int number = 0;
        while ((line = lines.readLine()) != null) {
            number++;
            int comment = line.indexOf('#');
            line = (comment >= 0 ? line.substring(0, comment) : line).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] words = line.split("\\s+");
            try {
                switch (words[0]) {
                    case "v" -> positions.add(new float[]{Float.parseFloat(words[1]), Float.parseFloat(words[2]), Float.parseFloat(words[3])});
                    case "vt" -> coordinates.add(new float[]{Float.parseFloat(words[1]), words.length > 2 ? Float.parseFloat(words[2]) : 0.0F});
                    case "vn" -> normals.add(new float[]{Float.parseFloat(words[1]), Float.parseFloat(words[2]), Float.parseFloat(words[3])});
                    case "o", "g", "usemtl" -> {
                        if (corners.size > 0) {
                            parts.add(new Part(name, material, corners.toArray()));
                            corners = new FloatList();
                        }
                        String given = words.length > 1 ? line.substring(words[0].length()).trim() : "";
                        if (words[0].equals("usemtl")) {
                            material = given;
                        } else {
                            name = given.isEmpty() ? "default" : given;
                        }
                    }
                    case "f" -> face(words, positions, coordinates, normals, corners);
                    default -> {
                    }
                }
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("line " + number + " (\"" + line + "\"): " + e, e);
            }
        }
        if (corners.size > 0) {
            parts.add(new Part(name, material, corners.toArray()));
        }
        return parts.isEmpty() ? EMPTY : new ObjModel(parts);
    }

    private static void face(String[] words, List<float[]> positions, List<float[]> coordinates, List<float[]> normals, FloatList out) {
        int count = words.length - 1;
        if (count < 3) {
            return;
        }
        float[][] corner = new float[count][];
        boolean flat = false;
        for (int i = 0; i < count; i++) {
            String[] indices = words[i + 1].split("/", -1);
            float[] position = at(positions, indices[0]);
            float[] coordinate = indices.length > 1 && !indices[1].isEmpty() ? at(coordinates, indices[1]) : null;
            float[] normal = indices.length > 2 && !indices[2].isEmpty() ? at(normals, indices[2]) : null;
            flat |= normal == null;
            corner[i] = new float[]{position[0], position[1], position[2], coordinate == null ? 0.0F : coordinate[0], coordinate == null ? 0.0F : 1.0F - coordinate[1],
                    normal == null ? 0.0F : normal[0], normal == null ? 1.0F : normal[1], normal == null ? 0.0F : normal[2]};
        }
        if (flat) {
            // No normals given: the face's own, from its first three corners (counter-clockwise faces look outward).
            Vector3f a = new Vector3f(corner[0][0], corner[0][1], corner[0][2]);
            Vector3f normal = new Vector3f(corner[1][0], corner[1][1], corner[1][2]).sub(a).cross(new Vector3f(corner[2][0], corner[2][1], corner[2][2]).sub(a));
            if (normal.lengthSquared() > 0.0F) {
                normal.normalize();
            } else {
                normal.set(0.0F, 1.0F, 0.0F);
            }
            for (float[] each : corner) {
                each[5] = normal.x;
                each[6] = normal.y;
                each[7] = normal.z;
            }
        }
        for (int i = 1; i + 1 < count; i++) {
            out.add(corner[0]);
            out.add(corner[i]);
            out.add(corner[i + 1]);
        }
    }

    /** An entry by an .obj index: counted from 1, or from the end if negative. */
    private static float[] at(List<float[]> list, String index) {
        int i = Integer.parseInt(index);
        return list.get(i > 0 ? i - 1 : list.size() + i);
    }

    /**
     * A stretch of a model drawn as one: the faces under one {@code o}, {@code g} or {@code usemtl} line. Draw parts
     * one at a time to give each its own texture, colour, light or pose.
     */
    public static final class Part {
        private final String name, material;
        private final float[] corners;

        private Part(String name, String material, float[] corners) {
            this.name = name;
            this.material = material;
            this.corners = corners;
        }

        /** The object or group it belongs to ({@code "default"} if the file names none). */
        public String name() {
            return this.name;
        }

        /** Its {@code usemtl} name, or an empty string. */
        public String material() {
            return this.material;
        }

        public int triangles() {
            return this.corners.length / (STRIDE * 3);
        }

        /** Hands this part to the game to draw; see {@link ObjModel#submit(PoseStack, SubmitNodeCollector, RenderType, int, int, int)}. */
        public void submit(PoseStack pose, SubmitNodeCollector collector, RenderType type, int light, int overlay, int argb) {
            collector.submitCustomGeometry(pose, type, (last, consumer) -> this.render(last, consumer, light, overlay, argb));
        }

        /** Writes this part into a vertex consumer: each triangle as a quad with its last corner twice. */
        public void render(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int argb) {
            float[] corners = this.corners;
            for (int triangle = 0; triangle < corners.length; triangle += STRIDE * 3) {
                vertex(pose, consumer, corners, triangle, light, overlay, argb);
                vertex(pose, consumer, corners, triangle + STRIDE, light, overlay, argb);
                vertex(pose, consumer, corners, triangle + STRIDE * 2, light, overlay, argb);
                vertex(pose, consumer, corners, triangle + STRIDE * 2, light, overlay, argb);
            }
        }

        private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, float[] corners, int at, int light, int overlay, int argb) {
            consumer.addVertex(pose, corners[at], corners[at + 1], corners[at + 2])
                    .setColor(argb)
                    .setUv(corners[at + 3], corners[at + 4])
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(pose, corners[at + 5], corners[at + 6], corners[at + 7]);
        }
    }

    private static final class FloatList {
        private float[] values = new float[STRIDE * 96];
        private int size;

        void add(float[] corner) {
            if (this.size + corner.length > this.values.length) {
                this.values = java.util.Arrays.copyOf(this.values, this.values.length * 2);
            }
            System.arraycopy(corner, 0, this.values, this.size, corner.length);
            this.size += corner.length;
        }

        float[] toArray() {
            return java.util.Arrays.copyOf(this.values, this.size);
        }
    }
}
