package net.ixdarklord.coolcatcanvas.api.client.armature.vanilla;

import net.ixdarklord.coolcatcanvas.api.armature.Motion;
import net.ixdarklord.coolcatcanvas.api.client.armature.Animator;
import net.ixdarklord.coolcatcanvas.internal.client.armature.ArmatureInstances;
import net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla.VanillaArmatures;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Armatures on vanilla mobs and players, without replacing their models: play clips on their parts, change their
 * parts, wear armatures on them. Set up while the client sets up; works for any living entity drawn by a vanilla-style
 * renderer, including other mods' mobs.
 * <pre>{@code
 * // Emotes for players: clips named after the player's parts (head, body, right_arm, left_arm, right_leg, left_leg).
 * VanillaModels.player().animations(Armature.of("mymod", "emotes"));
 * VanillaModels.play(player, "emote", Motion.once("wave"));                // here, on this client
 * ArmatureControl.play(serverPlayer, "emote", Motion.once("wave"));        // from the server, seen by everyone
 *
 * // Changes to the player's model.
 * VanillaModels.player().hide("hat").scale("head", 1.1F);
 *
 * // A tail on every zombie, swinging on a spring.
 * VanillaModels.of(EntityType.ZOMBIE).attach("body", Armature.of("mymod", "tail"))
 *         .offset(0, -10, 2)
 *         .animate(animator -> animator.spring("tail"));
 * }</pre>
 */
public final class VanillaModels {
    private static final Map<EntityType<?>, VanillaModel> MODELS = new HashMap<>();
    private static final Map<Supplier<? extends EntityType<?>>, VanillaModel> PENDING = new HashMap<>();
    private static final List<AnimationOverlap> EXTERNAL = new CopyOnWriteArrayList<>();

    private VanillaModels() {
    }

    /** Every player (yours and others'), in first person not at all: first-person arms stay vanilla. */
    public static VanillaModel player() {
        return of(net.minecraft.world.entity.EntityTypes.PLAYER);
    }

    /** One kind of mob; the same object every time. */
    public static synchronized VanillaModel of(EntityType<?> type) {
        return MODELS.computeIfAbsent(type, t -> new VanillaModel());
    }

    /** As {@link #of(EntityType)}, for a type not registered yet (a {@code RegistryEntry}). */
    public static synchronized VanillaModel of(Supplier<? extends EntityType<?>> type) {
        return PENDING.computeIfAbsent(type, t -> new VanillaModel());
    }

    // ---- Playing, on this client ----

    /** Plays a clip from the mob's {@link VanillaModel#animations} on its base layer. */
    public static void play(Entity entity, Motion motion) {
        if (entity instanceof LivingEntity living) VanillaArmatures.animator(living);
        ArmatureInstances.command(entity, animator -> animator.play(motion));
    }

    public static void play(Entity entity, String layer, Motion motion) {
        if (entity instanceof LivingEntity living) VanillaArmatures.animator(living);
        ArmatureInstances.command(entity, animator -> animator.play(layer, motion));
    }

    /** Blends a layer back to vanilla's own animation. */
    public static void stop(Entity entity, String layer) {
        if (entity instanceof LivingEntity living) VanillaArmatures.animator(living);
        ArmatureInstances.command(entity, animator -> animator.stop(layer));
    }

    /** The entity's animator, once it has been drawn. */
    public static @Nullable Animator animator(Entity entity) {
        return ArmatureInstances.find(entity);
    }

    // ---- Other animation mods ----

    /**
     * Makes another mod that animates vanilla models known, so clips share parts with it (see
     * {@link VanillaModel#overlapRule}). The player animation mod Better Combat uses is known already.
     */
    public static void registerOverlap(AnimationOverlap overlap) {
        EXTERNAL.add(overlap);
    }

    /**
     * Whether another mod is animating the entity right now, such as a Better Combat attack: a driver
     * can hold an emote back while it does.
     * <pre>{@code
     * layer.drive(ctx -> VanillaModels.hasOverlap(ctx.living()) ? null : IDLE_POSE);
     * }</pre>
     */
    public static boolean hasOverlap(@Nullable LivingEntity entity) {
        if (entity == null) return false;
        for (AnimationOverlap overlap : EXTERNAL) {
            if (overlap.isAnimating(entity)) return true;
        }
        return false;
    }

    @ApiStatus.Internal
    public static List<AnimationOverlap> overlaps() {
        return EXTERNAL;
    }

    // ---- For CoolCatLib's renderer ----

    @ApiStatus.Internal
    public static synchronized @Nullable VanillaModel find(EntityType<?> type) {
        if (!PENDING.isEmpty()) {
            PENDING.forEach((supplier, model) -> {
                VanillaModel existing = MODELS.get(supplier.get());
                if (existing == null) MODELS.put(supplier.get(), model);
                else merge(existing, model);
            });
            PENDING.clear();
        }
        return MODELS.get(type);
    }

    private static void merge(VanillaModel into, VanillaModel from) {
        if (from.animations() != null) into.animations(from.animations());
        from.setups().forEach(into::animate);
        from.modifiers().forEach(into::pose);
        into.attachmentsList().addAll(from.attachments());
        into.when(from::test);
        into.overlapRule(from.overlapRule());
        if (from.mirrorsLeftHanded()) into.mirrorLeftHanded(true);
        if (from.usesTorsoJointNames()) into.torsoJointNames();
        if (from.firstPersonRule() != null) into.firstPersonArms(from.firstPersonRule());
    }
}
