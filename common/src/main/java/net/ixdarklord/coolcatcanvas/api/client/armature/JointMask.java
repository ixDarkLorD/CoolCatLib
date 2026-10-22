package net.ixdarklord.coolcatcanvas.api.client.armature;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The joints a layer may move. Masks let one layer wave the arms while another walks the legs, each with its own clip.
 * <pre>{@code
 * JointMask.of("arm_left", "arm_right")      // these joints and everything on them
 * JointMask.only("head")                     // this joint alone, not its children
 * JointMask.all().except("tail")             // everything but the tail and what hangs from it
 * }</pre>
 */
public final class JointMask {
    private static final JointMask ALL = new JointMask(List.of(), false, List.of());

    private final List<String> roots;
    private final boolean exact;
    private final List<String> excluded;
    // The last armature this was worked out for; a mask is usually used with one armature.
    private BakedArmature cachedArmature;
    private boolean[] cached;

    private JointMask(List<String> roots, boolean exact, List<String> excluded) {
        this.roots = roots;
        this.exact = exact;
        this.excluded = excluded;
    }

    /** Every joint. */
    public static JointMask all() {
        return ALL;
    }

    /** These joints and every joint below them. */
    public static JointMask of(String... joints) {
        return new JointMask(List.of(joints), false, List.of());
    }

    /** These joints alone. */
    public static JointMask only(String... joints) {
        return new JointMask(List.of(joints), true, List.of());
    }

    /** This mask without these joints and what is below them. */
    public JointMask except(String... joints) {
        List<String> excluded = new java.util.ArrayList<>(this.excluded);
        excluded.addAll(Arrays.asList(joints));
        return new JointMask(this.roots, this.exact, List.copyOf(excluded));
    }

    /** Whether each joint of the armature is in the mask. */
    public synchronized boolean[] resolve(BakedArmature armature) {
        if (armature == this.cachedArmature) return this.cached;
        int n = armature.jointCount();
        boolean[] in = new boolean[n];
        Set<String> roots = Set.copyOf(this.roots);
        for (int j = 0; j < n; j++) {
            if (roots.isEmpty()) {
                in[j] = true;
            } else if (this.exact) {
                in[j] = roots.contains(armature.joint(j).name());
            } else {
                for (int at = j; at >= 0; at = armature.joint(at).parent()) {
                    if (roots.contains(armature.joint(at).name())) {
                        in[j] = true;
                        break;
                    }
                }
            }
        }
        for (String name : this.excluded) {
            int excluded = armature.jointIndex(name);
            if (excluded < 0) continue;
            for (int j = 0; j < n; j++) {
                if (armature.isWithin(j, excluded)) in[j] = false;
            }
        }
        this.cachedArmature = armature;
        this.cached = in;
        return in;
    }
}
