package net.ixdarklord.coolcatcanvas.api.client.armature.molang;

import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureContext;
import net.ixdarklord.coolcatcanvas.internal.client.armature.molang.MolangVars;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;

/**
 * The {@code query.*} values Molang can read. The Bedrock names animations use most are built in; a mod adds its own
 * before the expressions that use them are first read (resource reload), usually while the client sets up.
 * <pre>{@code
 * ArmatureQueries.register("query.mymod_charge", ctx -> ctx.<Wisp>owner().getCharge());
 * ArmatureQueries.register("query.is_angry", ctx -> ctx.entity() instanceof Wolf wolf && wolf.isAngry());
 * }</pre>
 * Built in: anim_time, anim_length, life_time, delta_time, time_stamp, ground_speed, vertical_speed, modified_move_speed,
 * modified_distance_moved, limb_swing, limb_swing_amount, head_x_rotation, head_y_rotation, body_y_rotation, yaw_speed,
 * is_on_ground, is_in_water, is_in_water_or_rain, is_on_fire, is_sneaking, is_sprinting, is_swimming, is_riding,
 * is_baby, is_moving, is_alive, health, max_health, hurt_time, death_time, scale, time_of_day, moon_phase,
 * distance_from_camera and item_count. A query no one registered reads as 0, with one warning in the log.
 */
public final class ArmatureQueries {
    private static final Map<String, Query> QUERIES = new TreeMap<>();

    static {
        simple("anim_time", ArmatureContext::animTime);
        simple("anim_length", ArmatureContext::animLength);
        simple("life_time", ArmatureContext::lifeTime);
        simple("delta_time", ArmatureContext::deltaTime);
        simple("time_stamp", ctx -> {
            Level level = ctx.level();
            return level == null ? 0.0 : level.getGameTime();
        });
        simple("ground_speed", ArmatureContext::groundSpeed);
        simple("vertical_speed", ArmatureContext::verticalSpeed);
        simple("modified_move_speed", ArmatureContext::limbSwingAmount);
        simple("limb_swing_amount", ArmatureContext::limbSwingAmount);
        simple("modified_distance_moved", ArmatureContext::limbSwing);
        simple("limb_swing", ArmatureContext::limbSwing);
        simple("head_x_rotation", ArmatureContext::headPitch);
        simple("head_y_rotation", ArmatureContext::headYaw);
        simple("body_y_rotation", ArmatureContext::bodyYaw);
        simple("yaw_speed", ctx -> {
            Entity entity = ctx.entity();
            if (entity instanceof LivingEntity living) return (living.yBodyRot - living.yBodyRotO) * 20.0;
            return entity == null ? 0.0 : (entity.getYRot() - entity.yRotO) * 20.0;
        });
        flag("is_on_ground", ArmatureContext::onGround);
        flag("is_in_water", ArmatureContext::inWater);
        flag("is_in_water_or_rain", ctx -> ctx.entity() != null && ctx.entity().isInWaterOrRain());
        flag("is_on_fire", ctx -> ctx.entity() != null && ctx.entity().isOnFire());
        flag("is_sneaking", ctx -> ctx.entity() != null && ctx.entity().isShiftKeyDown());
        flag("is_sprinting", ctx -> ctx.entity() != null && ctx.entity().isSprinting());
        flag("is_swimming", ctx -> ctx.entity() != null && ctx.entity().isSwimming());
        flag("is_riding", ctx -> ctx.entity() != null && ctx.entity().isPassenger());
        flag("is_baby", ctx -> ctx.living() != null && ctx.living().isBaby());
        flag("is_moving", ArmatureContext::moving);
        flag("is_alive", ctx -> ctx.entity() == null || ctx.entity().isAlive());
        simple("health", ArmatureContext::health);
        simple("max_health", ArmatureContext::maxHealth);
        simple("hurt_time", ctx -> ctx.living() == null ? 0.0 : ctx.living().hurtTime);
        simple("death_time", ctx -> ctx.living() == null ? 0.0 : ctx.living().deathTime);
        simple("scale", ctx -> ctx.living() == null ? 1.0 : ctx.living().getScale());
        simple("time_of_day", ctx -> {
            Level level = ctx.level();
            return level == null ? 0.0 : (level.getOverworldClockTime() % 24000L) / 24000.0;
        });
        simple("moon_phase", ctx -> {
            Level level = ctx.level();
            return level == null ? 0.0 : (level.getOverworldClockTime() / 24000L) % 8L;
        });
        simple("distance_from_camera", ctx -> ctx.position().distanceTo(Minecraft.getInstance().gameRenderer.getMainCamera().position()));
        simple("item_count", ctx -> ctx.stack().getCount());
    }

    private ArmatureQueries() {
    }

    /**
     * Adds or replaces a query.
     *
     * @param name {@code "query.name"} or {@code "q.name"}; the {@code query.} is added if it is left out
     */
    public static synchronized void register(String name, ToDoubleFunction<ArmatureContext> query) {
        register(name, (context, args) -> query.applyAsDouble(context));
    }

    /** Adds or replaces a query that takes arguments, as in {@code q.my_query(2, 'x')} (strings read as 0). */
    public static synchronized void register(String name, Query query) {
        String normal = MolangVars.normalize(name);
        if (!normal.startsWith("query.")) normal = "query." + normal.substring(normal.indexOf('.') + 1);
        QUERIES.put(normal, query);
    }

    /** A query by its whole name, {@code "query.ground_speed"}; null if there is none. */
    public static synchronized @Nullable Query get(String name) {
        return QUERIES.get(MolangVars.normalize(name));
    }

    private static void simple(String name, ToDoubleFunction<ArmatureContext> query) {
        QUERIES.put("query." + name, (context, args) -> query.applyAsDouble(context));
    }

    private static void flag(String name, java.util.function.Predicate<ArmatureContext> query) {
        QUERIES.put("query." + name, (context, args) -> query.test(context) ? 1.0 : 0.0);
    }

    /** A Molang query. */
    @FunctionalInterface
    public interface Query {
        double get(ArmatureContext context, double[] args);
    }
}
