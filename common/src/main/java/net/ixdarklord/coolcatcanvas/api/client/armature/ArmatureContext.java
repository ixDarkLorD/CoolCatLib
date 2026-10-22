package net.ixdarklord.coolcatcanvas.api.client.armature;

import net.ixdarklord.coolcatcanvas.internal.client.armature.molang.MolangVars;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * What one armature-driven thing is doing right now: the entity, block entity or item stack it belongs to, its clock, and its
 * Molang variables. Layer drivers, modifiers, event listeners and Molang queries all read it.
 * <pre>{@code
 * layer.drive(ctx -> ctx.moving() ? Motion.loop("walk") : Motion.loop("idle"));
 * layer.drive(ctx -> ctx.<Wisp>owner().isCharging() ? Motion.loop("charge") : null);
 * }</pre>
 */
public final class ArmatureContext {
    private Object owner;
    private @Nullable Entity entity;
    private @Nullable BlockEntity blockEntity;
    private ItemStack stack = ItemStack.EMPTY;
    private float partialTick;
    private double time;
    private double deltaTime;
    private double animTime;
    private double animLength;
    private double[] variables = new double[16];
    private double[] temps = new double[8];

    public ArmatureContext(Object owner) {
        this.setOwner(owner);
    }

    // ---- What it belongs to ----

    /** The entity, block entity or item stack, cast to what the caller expects. */
    @SuppressWarnings("unchecked")
    public <T> T owner() {
        return (T) this.owner;
    }

    public @Nullable Entity entity() {
        return this.entity;
    }

    public @Nullable LivingEntity living() {
        return this.entity instanceof LivingEntity living ? living : null;
    }

    public @Nullable BlockEntity blockEntity() {
        return this.blockEntity;
    }

    /** The stack, for an item; empty for anything else. */
    public ItemStack stack() {
        return this.stack;
    }

    public @Nullable Level level() {
        if (this.entity != null) return this.entity.level();
        if (this.blockEntity != null) return this.blockEntity.getLevel();
        return Minecraft.getInstance().level;
    }

    /** Where it is in the world: an entity's feet, a block's centre, or the camera for an item. */
    public Vec3 position() {
        if (this.entity != null) return this.entity.getPosition(this.partialTick);
        if (this.blockEntity != null) return Vec3.atCenterOf(this.blockEntity.getBlockPos());
        return Minecraft.getInstance().gameRenderer.getMainCamera().position();
    }

    public @Nullable BlockPos blockPos() {
        if (this.blockEntity != null) return this.blockEntity.getBlockPos();
        return this.entity != null ? this.entity.blockPosition() : null;
    }

    // ---- Time ----

    /** Seconds this armature has been animating; stops while the game is paused. */
    public double time() {
        return this.time;
    }

    /** Seconds since the last update. */
    public double deltaTime() {
        return this.deltaTime;
    }

    public float partialTick() {
        return this.partialTick;
    }

    /** The time into the clip being read; only meaningful inside a keyframe expression or an event listener. */
    public double animTime() {
        return this.animTime;
    }

    public double animLength() {
        return this.animLength;
    }

    /** An entity's age in seconds, or {@link #time()} for anything else. */
    public double lifeTime() {
        return this.entity != null ? (this.entity.tickCount + this.partialTick) / 20.0 : this.time;
    }

    // ---- Movement (entities; 0 or false for anything else) ----

    /** Horizontal speed in blocks per second. */
    public double groundSpeed() {
        if (this.entity == null) return 0.0;
        double dx = this.entity.getX() - this.entity.xo, dz = this.entity.getZ() - this.entity.zo;
        return Math.sqrt(dx * dx + dz * dz) * 20.0;
    }

    /** Vertical speed in blocks per second, up being positive. */
    public double verticalSpeed() {
        return this.entity == null ? 0.0 : (this.entity.getY() - this.entity.yo) * 20.0;
    }

    /** Whether it is walking: a living entity's limbs are swinging, or anything else is sliding along. */
    public boolean moving() {
        LivingEntity living = this.living();
        if (living != null) return living.walkAnimation.speed(this.partialTick) > 0.05F;
        return this.groundSpeed() > 0.1;
    }

    /** How far the limbs have swung in all, vanilla's walk cycle position. */
    public float limbSwing() {
        LivingEntity living = this.living();
        return living == null ? 0.0F : living.walkAnimation.position(this.partialTick);
    }

    /** How hard the limbs swing now, 0 to about 1. */
    public float limbSwingAmount() {
        LivingEntity living = this.living();
        return living == null ? 0.0F : Math.min(1.0F, living.walkAnimation.speed(this.partialTick));
    }

    public boolean onGround() {
        return this.entity != null && this.entity.onGround();
    }

    public boolean inWater() {
        return this.entity != null && this.entity.isInWater();
    }

    /** The body's yaw in degrees (an entity's own yaw if it has no body). */
    public float bodyYaw() {
        if (this.entity instanceof LivingEntity living) return Mth.rotLerp(this.partialTick, living.yBodyRotO, living.yBodyRot);
        return this.entity == null ? 0.0F : this.entity.getYRot(this.partialTick);
    }

    /** How far the head is turned from the body, in degrees, positive to the entity's right. */
    public float headYaw() {
        if (!(this.entity instanceof LivingEntity living)) return 0.0F;
        float head = Mth.rotLerp(this.partialTick, living.yHeadRotO, living.yHeadRot);
        return Mth.wrapDegrees(head - this.bodyYaw());
    }

    /** The head's pitch in degrees, positive looking down. */
    public float headPitch() {
        return this.entity == null ? 0.0F : this.entity.getXRot(this.partialTick);
    }

    public float health() {
        LivingEntity living = this.living();
        return living == null ? 0.0F : living.getHealth();
    }

    public float maxHealth() {
        LivingEntity living = this.living();
        return living == null ? 0.0F : living.getMaxHealth();
    }

    public boolean dead() {
        LivingEntity living = this.living();
        return living != null && living.isDeadOrDying();
    }

    // ---- Variables ----

    /** A Molang variable ({@code "v.charge"} or {@code "charge"}); 0 until it is set. */
    public double variable(String name) {
        int slot = MolangVars.existing(name);
        return slot < 0 ? 0.0 : this.variableSlot(slot);
    }

    public ArmatureContext setVariable(String name, double value) {
        this.setVariableSlot(MolangVars.slot(name), value);
        return this;
    }

    public double variableSlot(int slot) {
        return slot < this.variables.length ? this.variables[slot] : 0.0;
    }

    public void setVariableSlot(int slot, double value) {
        if (slot >= this.variables.length) this.variables = Arrays.copyOf(this.variables, Math.max(slot + 1, this.variables.length * 2));
        this.variables[slot] = value;
    }

    public double tempSlot(int slot) {
        return slot < this.temps.length ? this.temps[slot] : 0.0;
    }

    public void setTempSlot(int slot, double value) {
        if (slot >= this.temps.length) this.temps = Arrays.copyOf(this.temps, Math.max(slot + 1, this.temps.length * 2));
        this.temps[slot] = value;
    }

    // ---- Set by the animator ----

    void setOwner(Object owner) {
        this.owner = owner;
        this.entity = owner instanceof Entity e ? e : null;
        this.blockEntity = owner instanceof BlockEntity b ? b : null;
        this.stack = owner instanceof ItemStack s ? s : ItemStack.EMPTY;
    }

    void advance(double time, double deltaTime, float partialTick) {
        this.time = time;
        this.deltaTime = deltaTime;
        this.partialTick = partialTick;
    }

    void setAnim(double animTime, double animLength) {
        this.animTime = animTime;
        this.animLength = animLength;
    }
}
