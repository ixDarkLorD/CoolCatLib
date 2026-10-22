package net.ixdarklord.coolcatcanvas.internal.client.armature;

import net.ixdarklord.coolcatcanvas.api.client.armature.Animator;
import net.ixdarklord.coolcatcanvas.internal.armature.ArmatureCommandPayload;
import net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla.VanillaArmatures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/** Carries out the server's armature commands on this client. */
public final class ArmatureClientPackets {
    private ArmatureClientPackets() {
    }

    public static void handle(ArmatureCommandPayload payload) {
        Object owner = owner(payload);
        if (owner == null) return;
        Consumer<Animator> command = switch (payload.action()) {
            case PLAY -> animator -> animator.play(payload.layer(), payload.motion());
            case STOP -> animator -> {
                var layer = animator.findLayer(payload.layer());
                if (layer != null) layer.stop(payload.fade());
            };
            case VARIABLE -> animator -> animator.setVariable(payload.variable(), payload.value());
        };
        // A vanilla mob's animator is made on demand: the local player in first person is never drawn to make one.
        if (owner instanceof LivingEntity living && ArmatureInstances.find(owner) == null) VanillaArmatures.animator(living);
        ArmatureInstances.command(owner, command);
        // Armatures a vanilla mob wears get the command too, when it means something to them.
        for (Animator worn : VanillaArmatures.attachments(owner)) {
            boolean relevant = switch (payload.action()) {
                case PLAY -> worn.armature().get().hasClip(payload.motion().clip());
                case STOP -> worn.findLayer(payload.layer()) != null;
                case VARIABLE -> true;
            };
            if (relevant) command.accept(worn);
        }
    }

    private static @Nullable Object owner(ArmatureCommandPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return null;
        return switch (payload.target()) {
            case ENTITY -> level.getEntity(payload.entityId());
            case BLOCK -> level.getBlockEntity(payload.pos());
            case HELD -> {
                Entity entity = level.getEntity(payload.entityId());
                if (!(entity instanceof LivingEntity living)) yield null;
                ItemStack stack = living.getItemInHand(payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
                yield stack.isEmpty() ? null : stack;
            }
        };
    }
}
