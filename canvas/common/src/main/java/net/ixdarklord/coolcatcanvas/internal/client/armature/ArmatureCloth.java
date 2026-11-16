package net.ixdarklord.coolcatcanvas.internal.client.armature;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.ixdarklord.coolcatcanvas.api.client.armature.Joint;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cloth: joints laid out as a sheet (an armature file's {@code "cloth"}), rows down it and columns across, whose faces
 * are skinned to their neighbours, so the sheet bends smoothly between them and stays whole: a corner where one length
 * of a cape meets the next, or where two lengths side by side meet, is moved by both, the same on either side, so no
 * seam opens or folds over as the joints swing (their springs). Each corner of a sheet's faces is weighed between its
 * own joint, the one below it, and the one beside it nearest that corner (and the one below that), bilinearly by how
 * far down its length and how far across toward its edge on that side (where its faces end) it lies: on the edge, half
 * to each, so neighbours of different widths still meet. And first its seams are welded: where a corner of one joint's
 * faces and a corner of a neighbour's (beside, below or diagonally) are each other's nearest and nearly meet (within
 * {@link #WELD} pixels), both are moved to the same point, their middle (or the middle of three or four, where a row's
 * seam meets a column's), so boxes cut square that cannot quite meet (the panels of a skirt flaring out to its hem,
 * whose edges part as they go down) close into one smooth sheet: each panel drawn as the tapered piece it stands for.
 * <pre>{@code
 * "cloth": [{"joints": [["cape_0_0", "cape_1_0"], ["cape_0_1", "cape_1_1"], ["cape_0_2", "cape_1_2"]]},
 *           {"joints": [["skirt_front_0", "skirt_side_l_0", "skirt_back_0", "skirt_side_r_0"], ...], "wrap": true}]
 * }</pre>
 * Rows go down the sheet, each a list of its columns across; {@code "wrap"} joins the last column to the first (a skirt).
 */
final class ArmatureCloth {
    // Per corner: four influences of (joint, weight, x, y, z), the corner placed in that joint's rest space.
    static final int INFLUENCES = 4;
    static final int CORNER = INFLUENCES * 5;
    static final int FACE = CORNER * 4;
    // How near two neighbours' corners must be to be welded into one, pixels.
    static final float WELD = 1.0F;

    private ArmatureCloth() {
    }

    record Sheet(int[][] grid, boolean wrap) {
    }

    static List<Sheet> read(Identifier id, JsonObject definition, Map<String, Integer> index) {
        List<Sheet> sheets = new ArrayList<>();
        if (!definition.has("cloth")) return sheets;
        for (JsonElement element : definition.getAsJsonArray("cloth")) {
            JsonObject sheet = element.getAsJsonObject();
            List<int[]> rows = new ArrayList<>();
            for (JsonElement row : sheet.getAsJsonArray("joints")) {
                int[] columns = new int[row.getAsJsonArray().size()];
                int c = 0;
                for (JsonElement name : row.getAsJsonArray()) {
                    Integer joint = index.get(name.getAsString());
                    if (joint == null) CoolCatCanvas.LOGGER.warn("Armature {}: cloth names no joint called {}", id, name.getAsString());
                    columns[c++] = joint == null ? -1 : joint;
                }
                rows.add(columns);
            }
            if (!rows.isEmpty()) sheets.add(new Sheet(rows.toArray(int[][]::new), sheet.has("wrap") && sheet.get("wrap").getAsBoolean()));
        }
        return sheets;
    }

    /** Each joint's place at rest, model pixels: its parents' and its own rest turns about their pivots. */
    static Matrix4f[] bind(List<Joint> joints) {
        Matrix4f[] bind = new Matrix4f[joints.size()];
        for (Joint joint : joints) {
            Matrix4f m = joint.parent() >= 0 ? new Matrix4f(bind[joint.parent()]) : new Matrix4f();
            float px = joint.pivot().x(), py = joint.pivot().y(), pz = joint.pivot().z();
            m.translate(px, py, pz).rotateZYX(joint.restRotation().z(), joint.restRotation().y(), joint.restRotation().x()).translate(-px, -py, -pz);
            bind[joint.index()] = m;
        }
        return bind;
    }

    /**
     * The skin of one mesh group's faces ({@link ArmatureMesh#STRIDE} floats each, by joint): for each joint of a sheet,
     * {@link #FACE} floats per face; null for the others, or null altogether with no sheets.
     */
    static float @Nullable [][] skin(List<Joint> joints, Matrix4f[] bind, List<Sheet> sheets, float[][] packed) {
        if (sheets.isEmpty()) return null;
        Matrix4f[] inverse = new Matrix4f[bind.length];
        for (int j = 0; j < bind.length; j++) inverse[j] = new Matrix4f(bind[j]).invert();
        float[][] out = new float[packed.length][];
        Welded welded = weld(bind, sheets, packed);
        float[][] rest = welded.rest;
        boolean any = false;
        Vector3f point = new Vector3f(), local = new Vector3f(), down = new Vector3f(), toward = new Vector3f();
        int[] who = new int[INFLUENCES];
        float[] weight = new float[INFLUENCES];
        for (Sheet sheet : sheets) {
            int[][] grid = sheet.grid;
            for (int r = 0; r < grid.length; r++) {
                for (int c = 0; c < grid[r].length; c++) {
                    int j = grid[r][c];
                    if (j < 0 || packed[j] == null || out[j] != null) continue;
                    Vector3f pivot = bind[j].transformPosition(new Vector3f(joints.get(j).pivot()));
                    int below = r + 1 < grid.length && c < grid[r + 1].length ? grid[r + 1][c] : -1;
                    float length = 0.0F;
                    if (below >= 0) {
                        bind[below].transformPosition(down.set(joints.get(below).pivot())).sub(pivot);
                        length = down.length();
                        if (length > 1.0E-4F) down.div(length);
                        else below = -1;
                    }
                    int left = c > 0 ? grid[r][c - 1] : sheet.wrap ? grid[r][grid[r].length - 1] : -1;
                    int right = c + 1 < grid[r].length ? grid[r][c + 1] : sheet.wrap ? grid[r][0] : -1;
                    if (left == j) left = -1;
                    if (right == j) right = -1;
                    float[] faces = packed[j];
                    int count = faces.length / ArmatureMesh.STRIDE;
                    float[] skin = new float[count * FACE];
                    // How far its faces reach toward each neighbour across (from its pivot): its edge on that side. A
                    // corner on that edge is weighed half to each, and the neighbour weighs its own edge the same, so
                    // the two meet whatever their widths (a narrow side panel beside a wide front one).
                    float[] reach = new float[2];
                    for (int side = 0; side < 2; side++) {
                        int other = side == 0 ? left : right;
                        if (other < 0) continue;
                        bind[other].transformPosition(toward.set(joints.get(other).pivot())).sub(pivot);
                        if (toward.length() < 1.0E-4F) continue;
                        toward.normalize();
                        for (int corner = 0; corner < count * 4; corner++) {
                            point.set(rest[j][corner * 3], rest[j][corner * 3 + 1], rest[j][corner * 3 + 2]);
                            reach[side] = Math.max(reach[side], local.set(point).sub(pivot).dot(toward));
                        }
                    }
                    for (int f = 0; f < count; f++) {
                        for (int k = 0; k < 4; k++) {
                            int at = (f * 4 + k) * 3;
                            point.set(rest[j][at], rest[j][at + 1], rest[j][at + 2]);
                            local.set(point).sub(pivot);
                            // How far down its length (to the joint below), and how far toward each neighbour across.
                            float a = below >= 0 ? Mth.clamp(local.dot(down) / length, 0.0F, 1.0F) : 0.0F;
                            // A corner welded to the joint below lies on that seam: all the way down.
                            int root = welded.root[j][f * 4 + k];
                            if (below >= 0 && welded.shares(below, root)) a = 1.0F;
                            float b = 0.0F;
                            int beside = -1;
                            for (int side = 0; side < 2; side++) {
                                int other = side == 0 ? left : right;
                                if (other < 0) continue;
                                bind[other].transformPosition(toward.set(joints.get(other).pivot())).sub(pivot);
                                float gap = toward.length();
                                if (gap < 1.0E-4F) continue;
                                float edge = reach[side] > 1.0E-4F ? reach[side] : gap * 0.5F;
                                // Welded to it: on their seam, half each (as it weighs the same corner), so the two meet exactly.
                                float lean = welded.shares(other, root) ? 0.5F : Mth.clamp(local.dot(toward.div(gap)) / edge, 0.0F, 1.0F) * 0.5F;
                                if (lean > b) {
                                    b = lean;
                                    beside = other;
                                }
                            }
                            int besideBelow = -1;
                            if (beside >= 0 && below >= 0) {
                                for (int cc = 0; cc < grid[r].length; cc++) {
                                    if (grid[r][cc] == beside && r + 1 < grid.length && cc < grid[r + 1].length) besideBelow = grid[r + 1][cc];
                                }
                            }
                            who[0] = j;
                            weight[0] = (1.0F - a) * (1.0F - b);
                            who[1] = below;
                            weight[1] = a * (1.0F - b);
                            who[2] = beside;
                            weight[2] = (1.0F - a) * b;
                            who[3] = besideBelow >= 0 ? besideBelow : below;
                            weight[3] = a * b;
                            int o = (f * 4 + k) * CORNER;
                            for (int i = 0; i < INFLUENCES; i++) {
                                int joint = who[i];
                                if (joint < 0 || weight[i] <= 0.0F) {
                                    // Nothing there: its share stays with the corner's own joint.
                                    if (weight[i] > 0.0F) weight[0] += weight[i];
                                    weight[i] = 0.0F;
                                    joint = j;
                                }
                                inverse[joint].transformPosition(local.set(point));
                                skin[o + i * 5] = joint;
                                skin[o + i * 5 + 1] = weight[i];
                                skin[o + i * 5 + 2] = local.x;
                                skin[o + i * 5 + 3] = local.y;
                                skin[o + i * 5 + 4] = local.z;
                            }
                            // The first influence was written before any share moved to it: write its weight again.
                            skin[o + 1] = weight[0];
                        }
                    }
                    out[j] = skin;
                    any = true;
                }
            }
        }
        return any ? out : null;
    }

    /**
     * Each sheet joint's face corners at rest (model pixels, 3 floats a corner, in its faces' order), with its seams
     * welded: corners of neighbouring joints in a sheet (beside, below, diagonally; round a wrapped one) that are each
     * other's nearest and within {@link #WELD} are joined, through every neighbour they are joined to, at their middle.
     */
    /** The welded rest corners, each face corner's weld (its group's id, or -1), and the welds each joint has a corner in. */
    record Welded(float[][] rest, int[][] root, Set<Integer>[] roots) {
        boolean shares(int joint, int root) {
            return root >= 0 && this.roots[joint] != null && this.roots[joint].contains(root);
        }
    }

    @SuppressWarnings("unchecked")
    static Welded weld(Matrix4f[] bind, List<Sheet> sheets, float[][] packed) {
        float[][] rest = new float[packed.length][];
        // Each joint's distinct corners (a box's eight, shared by its faces), and which of them each face corner is.
        float[][] unique = new float[packed.length][];
        int[][] which = new int[packed.length][];
        Vector3f point = new Vector3f();
        for (Sheet sheet : sheets) {
            for (int[] row : sheet.grid) {
                for (int j : row) {
                    if (j < 0 || packed[j] == null || rest[j] != null) continue;
                    int corners = packed[j].length / ArmatureMesh.STRIDE * 4;
                    rest[j] = new float[corners * 3];
                    which[j] = new int[corners];
                    float[] distinct = new float[corners * 3];
                    int n = 0;
                    for (int c = 0; c < corners; c++) {
                        int at = (c / 4) * ArmatureMesh.STRIDE + (c % 4) * 5;
                        bind[j].transformPosition(point.set(packed[j][at], packed[j][at + 1], packed[j][at + 2]));
                        rest[j][c * 3] = point.x;
                        rest[j][c * 3 + 1] = point.y;
                        rest[j][c * 3 + 2] = point.z;
                        int found = -1;
                        for (int u = 0; u < n && found < 0; u++) {
                            if (point.distanceSquared(distinct[u * 3], distinct[u * 3 + 1], distinct[u * 3 + 2]) < 1.0E-8F) found = u;
                        }
                        if (found < 0) {
                            found = n++;
                            distinct[found * 3] = point.x;
                            distinct[found * 3 + 1] = point.y;
                            distinct[found * 3 + 2] = point.z;
                        }
                        which[j][c] = found;
                    }
                    unique[j] = Arrays.copyOf(distinct, n * 3);
                }
            }
        }
        // Distinct corners numbered across the joints, joined (union-find) where neighbours' corners meet.
        int[] base = new int[packed.length + 1];
        for (int j = 0; j < packed.length; j++) base[j + 1] = base[j] + (unique[j] == null ? 0 : unique[j].length / 3);
        int[] parent = new int[base[packed.length]];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        boolean joined = false;
        for (Sheet sheet : sheets) {
            int[][] grid = sheet.grid;
            for (int r = 0; r < grid.length; r++) {
                for (int c = 0; c < grid[r].length; c++) {
                    int j = grid[r][c];
                    if (j < 0 || unique[j] == null) continue;
                    // Its neighbours forward (right, below, and both diagonals below); the others reach it from theirs.
                    for (int[] step : NEIGHBOURS) {
                        int rr = r + step[0], cc = c + step[1];
                        if (rr >= grid.length) continue;
                        int width = grid[rr].length;
                        if (cc < 0 || cc >= width) {
                            if (!sheet.wrap || width == 0) continue;
                            cc = Math.floorMod(cc, width);
                        }
                        int other = grid[rr][cc];
                        if (other < 0 || other == j || unique[other] == null) continue;
                        joined |= join(unique, base, parent, j, other);
                    }
                }
            }
        }
        int[][] roots = new int[packed.length][];
        Set<Integer>[] sets = new Set[packed.length];
        if (!joined) {
            for (int j = 0; j < packed.length; j++) if (which[j] != null) Arrays.fill(roots[j] = new int[which[j].length], -1);
            return new Welded(rest, roots, sets);
        }
        // Each group of joined corners moved to its middle.
        float[] sum = new float[parent.length * 3];
        int[] members = new int[parent.length];
        for (int j = 0; j < packed.length; j++) {
            if (unique[j] == null) continue;
            for (int u = 0; u < unique[j].length / 3; u++) {
                int root = find(parent, base[j] + u);
                sum[root * 3] += unique[j][u * 3];
                sum[root * 3 + 1] += unique[j][u * 3 + 1];
                sum[root * 3 + 2] += unique[j][u * 3 + 2];
                members[root]++;
            }
        }
        for (int j = 0; j < packed.length; j++) {
            if (unique[j] == null) continue;
            roots[j] = new int[which[j].length];
            sets[j] = new HashSet<>();
            for (int c = 0; c < which[j].length; c++) {
                int root = find(parent, base[j] + which[j][c]);
                roots[j][c] = members[root] < 2 ? -1 : root;
                if (members[root] < 2) continue;
                sets[j].add(root);
                rest[j][c * 3] = sum[root * 3] / members[root];
                rest[j][c * 3 + 1] = sum[root * 3 + 1] / members[root];
                rest[j][c * 3 + 2] = sum[root * 3 + 2] / members[root];
            }
        }
        return new Welded(rest, roots, sets);
    }

    private static final int[][] NEIGHBOURS = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

    // Joins the corners of two neighbours that are each other's nearest and within WELD; whether any were.
    private static boolean join(float[][] unique, int[] base, int[] parent, int a, int b) {
        boolean any = false;
        int na = unique[a].length / 3, nb = unique[b].length / 3;
        for (int i = 0; i < na; i++) {
            int near = nearest(unique[b], nb, unique[a], i);
            if (near < 0 || nearest(unique[a], na, unique[b], near) != i) continue;
            float dx = unique[a][i * 3] - unique[b][near * 3], dy = unique[a][i * 3 + 1] - unique[b][near * 3 + 1],
                    dz = unique[a][i * 3 + 2] - unique[b][near * 3 + 2];
            if (dx * dx + dy * dy + dz * dz > WELD * WELD) continue;
            int x = find(parent, base[a] + i), y = find(parent, base[b] + near);
            if (x != y) parent[x] = y;
            any = true;
        }
        return any;
    }

    // Which of the n corners in `in` is nearest corner i of `of`.
    private static int nearest(float[] in, int n, float[] of, int i) {
        int best = -1;
        float bestD = Float.MAX_VALUE;
        for (int u = 0; u < n; u++) {
            float dx = in[u * 3] - of[i * 3], dy = in[u * 3 + 1] - of[i * 3 + 1], dz = in[u * 3 + 2] - of[i * 3 + 2];
            float d = dx * dx + dy * dy + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = u;
            }
        }
        return best;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }
}
