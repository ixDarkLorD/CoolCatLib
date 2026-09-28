package net.ixdarklord.coolcatcanvas.api.event.v2.client;

import net.ixdarklord.coolcatcanvas.api.client.effect.EffectUniform;
import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffect;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Screen effects being turned on and off, and changed. All fire on the render thread.
 */
public final class ScreenEffectEvents {
    /**
     * Before an effect is turned on or off, from any {@linkplain ToggleCause cause}. Returning
     * {@link EventResult#INTERRUPT} keeps it as it is (an effect whose condition was vetoed isn't asked again until
     * the condition changes).
     */
    public static final EventInvoker<BeforeToggle> BEFORE_TOGGLE = EventInvoker.create(BeforeToggle.class, listeners -> (effect, enabling, cause) -> {
        for (BeforeToggle listener : listeners) {
            EventResult result = listener.onToggle(effect, enabling, cause);
            if (result.isInterrupt()) return result;
        }
        return EventResult.PASS;
    });
    /** After an effect was turned on or off. It may still be fading. */
    public static final EventInvoker<Toggled> TOGGLED = EventInvoker.create(Toggled.class, listeners -> (effect, enabled, cause) -> {
        for (Toggled listener : listeners) listener.onToggled(effect, enabled, cause);
    });
    /** After an effect's manual strength was set, or an animation of it started (with the value it heads to). */
    public static final EventInvoker<StrengthChanged> STRENGTH_CHANGED = EventInvoker.create(StrengthChanged.class, listeners -> (effect, oldStrength, newStrength) -> {
        for (StrengthChanged listener : listeners) listener.onStrengthChanged(effect, oldStrength, newStrength);
    });
    /**
     * After a uniform was set, animated, bound, unbound or reset. Not fired for the values bindings and animations
     * produce every frame.
     */
    public static final EventInvoker<UniformChanged> UNIFORM_CHANGED = EventInvoker.create(UniformChanged.class, listeners -> (effect, uniform) -> {
        for (UniformChanged listener : listeners) listener.onUniformChanged(effect, uniform);
    });
    /** After the layer order changed, with the new order from bottom to top. */
    public static final EventInvoker<LayersChanged> LAYERS_CHANGED = EventInvoker.create(LayersChanged.class, listeners -> order -> {
        for (LayersChanged listener : listeners) listener.onLayersChanged(order);
    });
    /** After an effect's shaders were (re)loaded, with resources or after a change needing it. */
    public static final EventInvoker<Loaded> LOADED = EventInvoker.create(Loaded.class, listeners -> (effect, error) -> {
        for (Loaded listener : listeners) listener.onLoaded(effect, error);
    });

    private ScreenEffectEvents() {}

    /** What turned an effect on or off. */
    public enum ToggleCause {
        /** A mod, through the effect's methods. */
        CODE,
        /** Its {@code activeWhen} condition. */
        CONDITION,
        /** The end of an {@code enableFor}. */
        TIMEOUT,
        /** The player, from the effects screen or a saved choice. */
        PLAYER,
        /** The server, through {@code ScreenEffectControl}. */
        SERVER
    }

    @FunctionalInterface
    public interface BeforeToggle {
        EventResult onToggle(ScreenEffect effect, boolean enabling, ToggleCause cause);
    }

    @FunctionalInterface
    public interface Toggled {
        void onToggled(ScreenEffect effect, boolean enabled, ToggleCause cause);
    }

    @FunctionalInterface
    public interface StrengthChanged {
        void onStrengthChanged(ScreenEffect effect, float oldStrength, float newStrength);
    }

    @FunctionalInterface
    public interface UniformChanged {
        void onUniformChanged(ScreenEffect effect, EffectUniform uniform);
    }

    @FunctionalInterface
    public interface LayersChanged {
        void onLayersChanged(List<ScreenEffect> order);
    }

    @FunctionalInterface
    public interface Loaded {
        /** @param error why it failed, or null if it loaded */
        void onLoaded(ScreenEffect effect, @Nullable String error);
    }
}
