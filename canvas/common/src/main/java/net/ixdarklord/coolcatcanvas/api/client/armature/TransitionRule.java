package net.ixdarklord.coolcatcanvas.api.client.armature;

import net.ixdarklord.coolcatcanvas.api.armature.Motion;
import net.ixdarklord.coolcatcanvas.api.armature.Transition;
import org.jetbrains.annotations.Nullable;

/**
 * Picks the transition between two animations on a layer, asked each time one gives way to another (unless the new
 * motion names its own). Return null to fall through to the layer's pair rules and default.
 * <pre>{@code
 * layer.transitions((from, to, ctx) -> {
 *     if (to.clip().equals("hurt")) return Transition.CUT;                                 // flinches snap
 *     if ("run".equals(from)) return Transition.freeze(0.35F, Easing.CUBIC_OUT);          // skid to a stop
 *     return ctx.inWater() ? Transition.crossfade(0.5F) : null;                           // slow blends underwater
 * });
 * }</pre>
 */
@FunctionalInterface
public interface TransitionRule {
    /**
     * @param from the clip playing now, or null when nothing is
     * @param to   the motion about to play
     */
    @Nullable Transition choose(@Nullable String from, Motion to, ArmatureContext context);
}
