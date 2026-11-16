package net.ixdarklord.coolcatcanvas.api.cutscene;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * A stretch of a cutscene in which the camera keeps looking at something, whatever its keyframes' angles say: a
 * point, an entity, or the player watching. The camera still follows its path; only where it looks changes, so an
 * orbit stays on its subject and a fly-by tracks what it passes.
 * <pre>{@code
 * CameraFocus.onPoint(0, 200, altar)                 // the whole shot looks at the altar
 * CameraFocus.onEntity(40, 120, dragon).blend(15)    // turns to the dragon over 15 ticks, and away again after
 * CameraFocus.onPlayer(0, 60)
 * }</pre>
 *
 * @param start  when it begins, in ticks from the cutscene's start
 * @param end    when it ends
 * @param blend  over how many ticks the camera turns towards the target at the start, and back to its path at the end
 * @param point  the point looked at, in the cutscene's own space
 * @param entity the entity looked at, by its UUID; with neither a point nor an entity, it is the player
 */
public record CameraFocus(float start, float end, float blend, Optional<Vec3> point, Optional<UUID> entity) {
    /** An end for a focus that lasts as long as the cutscene does. */
    public static final float UNTIL_END = Float.MAX_VALUE;

    public static final Codec<CameraFocus> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("start", 0.0F).forGetter(CameraFocus::start),
            Codec.FLOAT.optionalFieldOf("end", UNTIL_END).forGetter(CameraFocus::end),
            Codec.FLOAT.optionalFieldOf("blend", 10.0F).forGetter(CameraFocus::blend),
            Vec3.CODEC.optionalFieldOf("point").forGetter(CameraFocus::point),
            UUIDUtil.STRING_CODEC.optionalFieldOf("entity").forGetter(CameraFocus::entity)
    ).apply(instance, CameraFocus::new));

    public CameraFocus {
        if (end < start) throw new IllegalArgumentException("A focus can't end before it starts: " + start + " to " + end);
        blend = Math.max(0.0F, blend);
    }

    public static CameraFocus onPoint(float start, float end, Vec3 point) {
        return new CameraFocus(start, end, 10.0F, Optional.of(point), Optional.empty());
    }

    public static CameraFocus onEntity(float start, float end, Entity entity) {
        return onEntity(start, end, entity.getUUID());
    }

    public static CameraFocus onEntity(float start, float end, UUID entity) {
        return new CameraFocus(start, end, 10.0F, Optional.empty(), Optional.of(entity));
    }

    /** Looks at the player watching the cutscene. */
    public static CameraFocus onPlayer(float start, float end) {
        return new CameraFocus(start, end, 10.0F, Optional.empty(), Optional.empty());
    }

    public CameraFocus blend(float ticks) {
        return new CameraFocus(this.start, this.end, ticks, this.point, this.entity);
    }

    /** How strongly the camera is held on the target at a time: 0 outside the stretch, 1 once fully turned. */
    public float weight(float time) {
        if (time < this.start || time > this.end) return 0.0F;
        if (this.blend <= 0.0F) return 1.0F;
        // A focus running from the cutscene's very start, or to its end, has nothing to turn from or back to.
        float in = this.start <= 0.0F ? 1.0F : Mth.clamp((time - this.start) / this.blend, 0.0F, 1.0F);
        float out = this.end == UNTIL_END ? 1.0F : Mth.clamp((this.end - time) / this.blend, 0.0F, 1.0F);
        float weight = Math.min(in, out);
        return weight * weight * (3.0F - 2.0F * weight);
    }
}
