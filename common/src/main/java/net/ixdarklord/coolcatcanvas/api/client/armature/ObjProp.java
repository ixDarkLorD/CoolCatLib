package net.ixdarklord.coolcatcanvas.api.client.armature;

import net.ixdarklord.coolcatcanvas.api.client.model.ObjModels;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * An {@code .obj} model carried by an armature: a sword in a hand, a lantern on a belt, a detailed head on a simple
 * body. It sits on a joint or point and moves with it, and is hidden, tinted and lit up with its joint. Use one for
 * shapes Blockbench's cubes do badly: smooth, high-detail or sculpted parts.
 *
 * <p>Give them in the armature's {@code .armature.json}, for every animator:</p>
 * <pre>{@code
 * "props": [
 *   { "point": "right_hand", "model": "mymod:models/obj/sword.obj", "texture": "mymod:textures/obj/sword.png",
 *     "offset": [0, -1, 0], "rotation": [90, 0, 0], "scale": 0.8, "material": "cutout" }
 * ]
 * }</pre>
 * or in code, for one animator ({@link Animator#prop}), such as the item an entity holds:
 * <pre>{@code
 * animator.prop(ObjProp.of("right_hand", MyMod.id("models/obj/torch.obj"), MyMod.id("textures/obj/torch.png")).scale(0.5F));
 * }</pre>
 * Positions are in pixels from the point and turns in degrees, on the point's own axes (Blockbench's: Y up). The
 * model's units are its file's, one to a block, before {@link #scale}.
 *
 * @param part only this part of the file (an {@code o}, {@code g} or {@code usemtl} name); null for all of it
 */
public record ObjProp(String point, Identifier model, Identifier texture, ArmatureMaterial material, Vector3fc offset,
                      Vector3fc rotation, float scale, @Nullable String part) {
    public static ObjProp of(String point, Identifier model, Identifier texture) {
        return new ObjProp(point, model, texture, ArmatureMaterial.CUTOUT, new Vector3f(), new Vector3f(), 1.0F, null);
    }

    public ObjProp offset(float x, float y, float z) {
        return new ObjProp(this.point, this.model, this.texture, this.material, new Vector3f(x, y, z), this.rotation, this.scale, this.part);
    }

    public ObjProp rotate(float x, float y, float z) {
        return new ObjProp(this.point, this.model, this.texture, this.material, this.offset, new Vector3f(x, y, z), this.scale, this.part);
    }

    public ObjProp scale(float scale) {
        return new ObjProp(this.point, this.model, this.texture, this.material, this.offset, this.rotation, scale, this.part);
    }

    public ObjProp material(ArmatureMaterial material) {
        return new ObjProp(this.point, this.model, this.texture, material, this.offset, this.rotation, this.scale, this.part);
    }

    public ObjProp part(@Nullable String part) {
        return new ObjProp(this.point, this.model, this.texture, this.material, this.offset, this.rotation, this.scale, part);
    }

    /** Whether the model file can be read (logged once if not; {@link ObjModels} explains). */
    public boolean loaded() {
        return !ObjModels.get(this.model).isEmpty();
    }
}
