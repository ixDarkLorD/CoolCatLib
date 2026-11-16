package net.ixdarklord.coolcatcanvas.api.client.armature;

import java.util.Locale;

/**
 * What a cloth is made of, for {@link Spring#fabric}: how heavy it is, how it hangs, how it moves through the air, how
 * it gives and how it folds, all at once. A fabric sets a spring's {@link Spring#mass}, {@link Spring#damping},
 * {@link Spring#gravity}, {@link Spring#airDrag}, {@link Spring#flutter}, {@link Spring#stretch}, {@link Spring#bend},
 * {@link Spring#linkStiffness} and {@link Spring#follow} (lighter, looser cloth trails its wearer more); set any of
 * them after it to change just that. How stiffly the cloth holds to its animated place ({@link Spring#stiffness}) and
 * how far it swings ({@link Spring#maxAngle}) are left to the cloth's own cut: a cape's top lengths stiffer than its
 * hem, say.
 * <pre>{@code
 * animator.spring("cape_0_2").fabric(Fabric.SILK).stiffness(25);
 * }</pre>
 * In an armature file, {@code "fabric": "silk"} on a spring.
 */
public enum Fabric {
    /** Light and slippery: flows far behind, ripples at any pace, folds sharply. A veil, a ribbon, a ghost's shroud. */
    SILK(0.4F, 3.0F, 3.0F, 0.09F, 0.7F, 0.02F, 0.0F, 0.25F, 0.6F),
    /** Light and soft: a nightgown, a shirt, a summer dress. */
    LINEN(0.7F, 4.5F, 3.8F, 0.06F, 0.35F, 0.03F, 1.5F, 0.3F, 0.7F),
    /** The everyday middle: a skirt, an apron, a tunic. */
    COTTON(0.9F, 5.5F, 4.2F, 0.05F, 0.22F, 0.025F, 2.5F, 0.35F, 0.75F),
    /** Heavy and warm: swings slowly and keeps swinging, hardly ripples. A cloak, a hood, a shawl. */
    WOOL(1.3F, 6.5F, 5.0F, 0.035F, 0.1F, 0.02F, 4.0F, 0.45F, 0.8F),
    /** Stiff and heavy: hangs in long curves, drags. A traveller's robe, a sail, a sack. */
    CANVAS(1.6F, 7.5F, 6.0F, 0.03F, 0.05F, 0.01F, 8.0F, 0.55F, 0.85F),
    /** Heaviest and stiffest: barely ripples, folds in wide arcs. A coat's skirts, an apron of hide, armour's straps. */
    LEATHER(2.0F, 8.5F, 7.0F, 0.02F, 0.0F, 0.0F, 14.0F, 0.7F, 0.9F),
    /** Torn and loose: flaps and tangles, gives easily. Tatters, a shroud worn to threads. */
    RAGS(0.7F, 3.5F, 4.5F, 0.07F, 0.55F, 0.05F, 0.0F, 0.2F, 0.5F),
    /** A cloak of leaves: light, rustling, a little springy. */
    LEAVES(0.6F, 4.0F, 3.5F, 0.07F, 0.45F, 0.04F, 1.0F, 0.3F, 0.65F),
    /** A string or a thin cord, as a chain of springs: nearly weightless, doesn't stretch, bends anywhere and trails
     * what it hangs from. A balloon's string (with {@link Spring#buoyancy} for its pull), a kite's line, a necklace. */
    CORD(0.25F, 3.0F, 4.0F, 0.04F, 0.15F, 0.0F, 0.0F, 0.3F, 0.55F);

    final float mass, damping, gravity, airDrag, flutter, stretch, bend, linkStiffness, follow;

    Fabric(float mass, float damping, float gravity, float airDrag, float flutter, float stretch, float bend, float linkStiffness, float follow) {
        this.mass = mass;
        this.damping = damping;
        this.gravity = gravity;
        this.airDrag = airDrag;
        this.flutter = flutter;
        this.stretch = stretch;
        this.bend = bend;
        this.linkStiffness = linkStiffness;
        this.follow = follow;
    }

    /** A fabric by name ("silk", "wool"...), or null. */
    public static Fabric byName(String name) {
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
