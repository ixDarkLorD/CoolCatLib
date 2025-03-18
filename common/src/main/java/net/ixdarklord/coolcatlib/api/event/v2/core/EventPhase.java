package net.ixdarklord.coolcatlib.api.event.v2.core;

import net.ixdarklord.coolcatlib.internal.core.CoolCatLib;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * Event phases useful for handling <code>net.fabricmc.fabric.api.event.Event</code> on Fabric, equivalent to <code>net.minecraftforge.eventbus.api.EventPriority</code> on Forge.
 */
@ApiStatus.NonExtendable
public record EventPhase(ResourceLocation identifier, EventPhase parent, Ordering ordering) {
    /**
     * Fabric's default event phase, equivalent to EventPriority#NORMAL on Forge.
     */
    static EventPhase DEFAULT = new EventPhase(ResourceLocation.fromNamespaceAndPath("fabric", "default"), null, null);
    /**
     * A phase to be used directly before the default phase, equivalent to EventPriority#HIGH on Forge.
     */
    static EventPhase BEFORE = new EventPhase(CoolCatLib.rl("before"), DEFAULT, EventPhase.Ordering.BEFORE);
    /**
     * A phase to be used directly after the default phase, equivalent to EventPriority#LOW on Forge.
     */
    static EventPhase AFTER = new EventPhase(CoolCatLib.rl("after"), DEFAULT, EventPhase.Ordering.AFTER);
    /**
     * A phase to be used as the very first phase, equivalent to EventPriority#HIGHEST on Forge.
     */
    static EventPhase FIRST = new EventPhase(CoolCatLib.rl("first"), BEFORE, EventPhase.Ordering.BEFORE);
    /**
     * A phase to be used as the very last phase, equivalent to EventPriority#LOWEST on Forge.
     */
    static EventPhase LAST = new EventPhase(CoolCatLib.rl("last"), AFTER, EventPhase.Ordering.AFTER);

    /**
     * @return the identifier used for registering this phase on Fabric
     */
    public ResourceLocation identifier() {
        return this.identifier;
    }

    /**
     * @return another event phase that runs before / after this one, the order is defined by {@link #applyOrdering(BiConsumer)}
     */
    public @Nullable EventPhase parent() {
        return this.parent;
    }

    /**
     * The ordering defines in which relation this event phase is to {@link #parent()}, if it is supposed to run before or afterward.
     *
     * @param consumer apply event phases to the Fabric event
     */
    public void applyOrdering(BiConsumer<ResourceLocation, ResourceLocation> consumer) {
        this.ordering().apply(consumer, this.identifier(), this.parent().identifier());
    }

    /**
     * Constructs a custom event phase that runs before <code>eventPhase</code>.
     *
     * @param eventPhase the event phase to run before
     * @return the custom event phase
     */
    static EventPhase early(EventPhase eventPhase) {
        return new EventPhase(CoolCatLib.rl("early_" + eventPhase.identifier().getPath()), eventPhase, EventPhase.Ordering.BEFORE);
    }

    /**
     * Constructs a custom event phase that runs after <code>eventPhase</code>.
     *
     * @param eventPhase the event phase to run after
     * @return the custom event phase
     */
    static EventPhase late(EventPhase eventPhase) {
        return new EventPhase(CoolCatLib.rl("late_" + eventPhase.identifier().getPath()), eventPhase, EventPhase.Ordering.AFTER);
    }

    public interface Ordering {
        Ordering BEFORE = BiConsumer::accept;
        Ordering AFTER = (consumer, first, second) -> {
            consumer.accept(second, first);
        };

        void apply(BiConsumer<ResourceLocation, ResourceLocation> consumer, ResourceLocation first, ResourceLocation second);
    }
}