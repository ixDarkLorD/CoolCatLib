package net.ixdarklord.coolcatcanvas.api.client.armature.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import java.util.function.Function;

/**
 * An item shown at a point of an armature-driven mob: a held sword, a hat, a lantern on a belt. The stack is asked for each
 * frame, so it follows the entity's equipment.
 * <pre>{@code
 * renderer.socket(new ItemSocket<Golem>("back", golem -> golem.getCarriedItem(), ItemDisplayContext.FIXED)
 *         .offset(0, 2, 1).rotate(0, 180, 0).scale(0.75F));
 * }</pre>
 */
public final class ItemSocket<T> {
    private final String point;
    private final Function<T, ItemStack> stack;
    private final ItemDisplayContext display;
    private boolean held;
    private final Vector3f offset = new Vector3f();
    private final Vector3f rotation = new Vector3f();
    private float scale = 1.0F;

    public ItemSocket(String point, Function<T, ItemStack> stack, ItemDisplayContext display) {
        this.point = point;
        this.stack = stack;
        this.display = display;
    }

    /** Holds it as a vanilla hand does, for a point at the hand of an arm hanging down at rest. */
    public ItemSocket<T> held() {
        this.held = true;
        return this;
    }

    /** Moves it from the point, in pixels along the point's own axes. */
    public ItemSocket<T> offset(float x, float y, float z) {
        this.offset.set(x, y, z);
        return this;
    }

    /** Turns it at the point, in degrees. */
    public ItemSocket<T> rotate(float x, float y, float z) {
        this.rotation.set(x, y, z);
        return this;
    }

    public ItemSocket<T> scale(float scale) {
        this.scale = scale;
        return this;
    }

    public String point() {
        return this.point;
    }

    public Function<T, ItemStack> stack() {
        return this.stack;
    }

    public ItemDisplayContext display() {
        return this.display;
    }

    void transform(PoseStack poseStack) {
        poseStack.translate(this.offset.x / 16.0F, this.offset.y / 16.0F, this.offset.z / 16.0F);
        if (this.rotation.z != 0.0F) poseStack.rotate(Axis.ZP.rotationDegrees(this.rotation.z));
        if (this.rotation.y != 0.0F) poseStack.rotate(Axis.YP.rotationDegrees(this.rotation.y));
        if (this.rotation.x != 0.0F) poseStack.rotate(Axis.XP.rotationDegrees(this.rotation.x));
        if (this.scale != 1.0F) poseStack.scale(this.scale, this.scale, this.scale);
        if (this.held) ArmatureMobRenderer.handTransform(poseStack);
    }
}
