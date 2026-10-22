package net.ixdarklord.coolcatcanvas.api.client.armature;

import net.ixdarklord.coolcatcanvas.internal.client.armature.ArmatureInstances;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * The animators of armature-driven things on this client, for client code that wants to play something itself (the server's
 * way is {@code ArmatureControl}). An animator exists once its owner has been drawn.
 * <pre>{@code
 * Animators.whenReady(entity, animator -> animator.play("action", Motion.once("roar")));
 * Animator animator = Animators.of(blockEntity);   // null if it hasn't been drawn
 * }</pre>
 */
public final class Animators {
    private Animators() {
    }

    public static @Nullable Animator of(Entity entity) {
        return ArmatureInstances.find(entity);
    }

    public static @Nullable Animator of(BlockEntity blockEntity) {
        return ArmatureInstances.find(blockEntity);
    }

    /** The animator of this very stack (each stack has its own). */
    public static @Nullable Animator of(ItemStack stack) {
        return ArmatureInstances.find(stack);
    }

    /**
     * Runs code on an entity's, block entity's or stack's animator: now if it has one, else when it is first drawn,
     * provided that is within a second.
     */
    public static void whenReady(Object owner, Consumer<Animator> action) {
        ArmatureInstances.command(owner, action);
    }
}
