package net.ixdarklord.coolcatcanvas.api.client.armature.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.ixdarklord.coolcatcanvas.api.client.armature.Animator;
import net.ixdarklord.coolcatcanvas.api.client.armature.Armature;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * An armature worn on a part of a vanilla model: wings on a zombie's body, a tail on a player, a hat on a villager's
 * head. It moves with the part (vanilla's animation, clips, {@link VanillaPose} changes), and has its own animator, so
 * it can play clips, swing on springs and react to the entity. Model it in Blockbench with its origin where it meets the
 * part; then move it with {@link #offset}.
 * <pre>{@code
 * VanillaModels.player().attach("body", Armature.of("mymod", "wings"))
 *         .offset(0, -2, 2)
 *         .animate(animator -> animator.layer("base").drive(ctx -> ctx.living().isFallFlying() ? GLIDE : FOLD))
 *         .when(player -> player.getItemBySlot(EquipmentSlot.CHEST).is(MyItems.WING_HARNESS));
 * }</pre>
 */
public final class VanillaAttachment {
    private final String part;
    private final Armature armature;
    private final Vector3f offset = new Vector3f();
    private final Vector3f rotation = new Vector3f();
    private float scale = 1.0F;
    private final List<Consumer<Animator>> setups = new ArrayList<>();
    private @Nullable Predicate<LivingEntity> filter;
    private @Nullable Function<LivingEntity, @Nullable Identifier> texture;

    VanillaAttachment(String part, Armature armature) {
        this.part = part;
        this.armature = armature;
    }

    /** Moves it from the part's pivot, in pixels (Y up, the model's front north, as in Blockbench). */
    public VanillaAttachment offset(float x, float y, float z) {
        this.offset.set(x, y, z);
        return this;
    }

    /** Turns it on the part, in degrees. */
    public VanillaAttachment rotate(float x, float y, float z) {
        this.rotation.set(x, y, z);
        return this;
    }

    public VanillaAttachment scale(float scale) {
        this.scale = scale;
        return this;
    }

    /** Sets up its animator when it is first worn by an entity. */
    public VanillaAttachment animate(Consumer<Animator> setup) {
        this.setups.add(setup);
        return this;
    }

    /** Wears it only on entities that pass, asked every frame. */
    public VanillaAttachment when(Predicate<LivingEntity> filter) {
        this.filter = filter;
        return this;
    }

    /** Swaps its first texture per entity; null keeps the armature's. */
    public VanillaAttachment texture(Function<LivingEntity, @Nullable Identifier> texture) {
        this.texture = texture;
        return this;
    }

    // ---- For CoolCatLib's renderer ----

    @ApiStatus.Internal
    public String part() {
        return this.part;
    }

    @ApiStatus.Internal
    public Armature armature() {
        return this.armature;
    }

    @ApiStatus.Internal
    public List<Consumer<Animator>> setups() {
        return this.setups;
    }

    @ApiStatus.Internal
    public boolean test(LivingEntity entity) {
        return this.filter == null || this.filter.test(entity);
    }

    @ApiStatus.Internal
    public @Nullable Identifier texture(LivingEntity entity) {
        return this.texture == null ? null : this.texture.apply(entity);
    }

    /** Its offset, turn and scale, from the part's pivot (blocks, Blockbench's axes). */
    @ApiStatus.Internal
    public void transform(PoseStack poseStack) {
        poseStack.translate(this.offset.x / 16.0F, this.offset.y / 16.0F, this.offset.z / 16.0F);
        if (this.rotation.z != 0.0F) poseStack.mulPose(Axis.ZP.rotationDegrees(this.rotation.z));
        if (this.rotation.y != 0.0F) poseStack.mulPose(Axis.YP.rotationDegrees(this.rotation.y));
        if (this.rotation.x != 0.0F) poseStack.mulPose(Axis.XP.rotationDegrees(this.rotation.x));
        if (this.scale != 1.0F) poseStack.scale(this.scale, this.scale, this.scale);
    }

    @ApiStatus.Internal
    public Matrix4f transform(Matrix4f matrix) {
        return matrix.translate(this.offset.x / 16.0F, this.offset.y / 16.0F, this.offset.z / 16.0F)
                .rotateZYX(this.rotation.z * Mth.DEG_TO_RAD, this.rotation.y * Mth.DEG_TO_RAD, this.rotation.x * Mth.DEG_TO_RAD)
                .scale(this.scale);
    }
}
