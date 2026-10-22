package net.ixdarklord.coolcatcanvas.api.client.armature;

import net.ixdarklord.coolcatcanvas.internal.client.armature.ArmatureLibrary;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * An armature by name: a modelled, animated thing drawn from a Blockbench or Bedrock file. Keep these in fields and constants;
 * {@link #get()} hands out the loaded model, which a resource pack reload replaces.
 *
 * <p>{@code Armature.of("mymod", "wisp")} looks under {@code assets/mymod/armatures/}, first for {@code wisp.armature.json} (a
 * definition naming the model, extra animation files, textures, materials, springs and head tracking), then for
 * {@code wisp.bbmodel} (a Blockbench project, with its animations and textures inside it), then for
 * {@code wisp.geo.json} with {@code wisp.animation.json} beside it (Bedrock exports, textured by
 * {@code assets/mymod/textures/armatures/wisp.png}). Sub-folders work: {@code Armature.of("mymod", "mobs/wisp")}.</p>
 *
 * <p>With no model at all, {@code wisp.animation.json} alone is enough: its bones become joints. That suits clips meant
 * for another model, such as emotes for the vanilla player (see {@code VanillaModels}).</p>

 * <p>With no model at all, {@code wisp.animation.json} alone is enough: its bones become joints. That suits clips meant
 * for another model, such as emotes for the vanilla player (see {@code VanillaModels}).</p>
 */
public final class Armature {
    /** No model and no clips: an animator that only runs code (modifiers, variables), never logged as missing. */
    public static final Armature NONE = new Armature(Identifier.fromNamespaceAndPath("coolcatlib", "none"));

    private final Identifier id;

    private Armature(Identifier id) {
        this.id = id;
    }

    public static Armature of(Identifier id) {
        return new Armature(id);
    }

    public static Armature of(String namespace, String path) {
        return new Armature(Identifier.fromNamespaceAndPath(namespace, path));
    }

    public Identifier id() {
        return this.id;
    }

    /**
     * The loaded armature; read on first use and after every resource reload. An armature that can't be loaded is logged once and
     * gives {@link BakedArmature#EMPTY}. Client thread only.
     */
    public BakedArmature get() {
        return ArmatureLibrary.get(this.id);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof Armature armature && this.id.equals(armature.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    @Override
    public String toString() {
        return "Armature[" + this.id + "]";
    }
}
