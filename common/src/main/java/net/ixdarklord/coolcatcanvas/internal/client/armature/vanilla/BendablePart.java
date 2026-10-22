package net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla;

import net.minecraft.client.model.geom.ModelPart;

import java.util.List;

/** A vanilla model part that can bend along its length (ModelPartMixin). */
public interface BendablePart {
    /**
     * Bends the part from {@code start} to {@code end} along its Y (model pixels, in its own space) by {@code amount}
     * radians, toward {@code direction} radians round the length (0 forward). An amount of 0 straightens it.
     */
    void coolcatlib$setBend(float amount, float direction, float start, float end);

    float coolcatlib$bendAmount();

    float coolcatlib$bendDirection();

    float coolcatlib$bendStart();

    float coolcatlib$bendEnd();

    List<ModelPart.Cube> coolcatlib$cubes();

    static BendablePart of(ModelPart part) {
        return (BendablePart) (Object) part;
    }
}
