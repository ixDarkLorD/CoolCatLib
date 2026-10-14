package net.ixdarklord.coolcatcore.api.event.v2.common;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventResultHolder;
import net.ixdarklord.coolcatcore.api.attachment.AttachmentHolder;
import net.ixdarklord.coolcatcore.api.attachment.Attachment;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * {@link Attachment} values being added, changed and removed on any holder (entity, block entity or item stack), and
 * players starting and stopping to track a holder's data.
 * <p>
 * The events see every attachment; the {@code on...(attachment, ...)} helpers register a listener for one attachment, with typed values:
 * <pre>{@code
 * AttachmentEvents.onChanged(MADNESS, (holder, oldValue, newValue) -> {
 *     if (newValue >= 100 && holder.owner() instanceof ServerPlayer player) player.kill(player.level());
 * });
 * AttachmentEvents.onChanging(MADNESS, (holder, oldValue, newValue) ->
 *         newValue != null && newValue > 100 ? EventResultHolder.allow(100) : EventResultHolder.pass());   // clamp
 * AttachmentEvents.START_TRACKING.register((holder, player) -> ...);
 * }</pre>
 * Add, change and remove events run on the side the value changed on: where it was set, or on a client when a
 * synced value arrives (check {@link AttachmentHolder#isClientSide()}). Values read from a save aren't changes, and fire
 * nothing.
 */
public final class AttachmentEvents {
    public static final EventInvoker<Changing> CHANGING = EventInvoker.create(Changing.class, listeners -> (holder, attachment, oldValue, newValue) -> {
        for (Changing listener : listeners) {
            EventResultHolder<Object> result = listener.onChanging(holder, attachment, oldValue, newValue);
            if (result.isInterrupt()) return result;
        }
        return EventResultHolder.pass();
    });
    public static final EventInvoker<Added> ADDED = EventInvoker.create(Added.class, listeners -> (holder, attachment, value) -> {
        for (Added listener : listeners) listener.onAdded(holder, attachment, value);
    });
    public static final EventInvoker<Changed> CHANGED = EventInvoker.create(Changed.class, listeners -> (holder, attachment, oldValue, newValue) -> {
        for (Changed listener : listeners) listener.onChanged(holder, attachment, oldValue, newValue);
    });
    public static final EventInvoker<Removed> REMOVED = EventInvoker.create(Removed.class, listeners -> (holder, attachment, oldValue) -> {
        for (Removed listener : listeners) listener.onRemoved(holder, attachment, oldValue);
    });
    public static final EventInvoker<StartTracking> START_TRACKING = EventInvoker.create(StartTracking.class, listeners -> (holder, player) -> {
        for (StartTracking listener : listeners) listener.onStartTracking(holder, player);
    });
    public static final EventInvoker<StopTracking> STOP_TRACKING = EventInvoker.create(StopTracking.class, listeners -> (holder, player) -> {
        for (StopTracking listener : listeners) listener.onStopTracking(holder, player);
    });
    public static final EventInvoker<Received> RECEIVED = EventInvoker.create(Received.class, listeners -> (holder, attachments) -> {
        for (Received listener : listeners) listener.onReceived(holder, attachments);
    });

    private AttachmentEvents() {}

    /**
     * A value is about to be added, changed or removed by code on this side (not by a sync from the server).
     * <ul>
     *     <li>{@link EventResultHolder#pass()}: let it happen;</li>
     *     <li>{@link EventResultHolder#deny deny(anything)}: cancel it;</li>
     *     <li>{@link EventResultHolder#allow allow(value)}: store {@code value} instead (it must be of the attachment's type).</li>
     * </ul>
     * Changes a {@link net.ixdarklord.coolcatcore.api.attachment.Trackable} value makes to itself can't be cancelled.
     */
    @FunctionalInterface
    public interface Changing {
        /**
         * @param oldValue the stored value, {@code null} when there's none (an add)
         * @param newValue the value to store, {@code null} for a removal; for an in-place
         *                 {@link AttachmentHolder#modify modify}, the same object as {@code oldValue}, not yet changed
         */
        EventResultHolder<Object> onChanging(AttachmentHolder holder, Attachment<?> attachment, @Nullable Object oldValue, @Nullable Object newValue);
    }

    /** A value was stored where there was none. */
    @FunctionalInterface
    public interface Added {
        void onAdded(AttachmentHolder holder, Attachment<?> attachment, Object value);
    }

    /**
     * A stored value was replaced, or changed in place ({@link AttachmentHolder#modify modify}, a
     * {@link net.ixdarklord.coolcatcore.api.attachment.Trackable} such as a container's slots), in which case both values
     * are the same object.
     */
    @FunctionalInterface
    public interface Changed {
        void onChanged(AttachmentHolder holder, Attachment<?> attachment, Object oldValue, Object newValue);
    }

    /** A value was removed ({@link AttachmentHolder#reset reset}); the holder reads the default again. */
    @FunctionalInterface
    public interface Removed {
        void onRemoved(AttachmentHolder holder, Attachment<?> attachment, Object oldValue);
    }

    /**
     * A player starts receiving a holder's synced data (server side): they started seeing the entity or received the
     * block entity's chunk (for holders carrying attachments at that moment), or, for their own attachments, joined,
     * respawned or changed dimension (always). Fired before the values are sent.
     */
    @FunctionalInterface
    public interface StartTracking {
        void onStartTracking(AttachmentHolder holder, ServerPlayer player);
    }

    /**
     * A player stops receiving a holder's data (server side): they stopped seeing the entity, the block entity's chunk
     * was dropped for them, or, for their own data, they left.
     */
    @FunctionalInterface
    public interface StopTracking {
        void onStopTracking(AttachmentHolder holder, ServerPlayer player);
    }

    /** Synced values from the server were applied on this client (after their add, change and remove events). */
    @FunctionalInterface
    public interface Received {
        void onReceived(AttachmentHolder holder, Set<Attachment<?>> attachments);
    }

    // --- One attachment ---

    /** Listens to one attachment's pending changes; see {@link Changing}. */
    @SuppressWarnings("unchecked")
    public static <T> Changing onChanging(Attachment<T> attachment, TypedChanging<T> listener) {
        Changing wrapped = (holder, changed, oldValue, newValue) -> changed == attachment
                ? (EventResultHolder<Object>) (EventResultHolder<?>) listener.onChanging(holder, (T) oldValue, (T) newValue)
                : EventResultHolder.pass();
        CHANGING.register(wrapped);
        return wrapped;
    }

    @SuppressWarnings("unchecked")
    public static <T> Added onAdded(Attachment<T> attachment, TypedAdded<T> listener) {
        Added wrapped = (holder, added, value) -> {
            if (added == attachment) listener.onAdded(holder, (T) value);
        };
        ADDED.register(wrapped);
        return wrapped;
    }

    @SuppressWarnings("unchecked")
    public static <T> Changed onChanged(Attachment<T> attachment, TypedChanged<T> listener) {
        Changed wrapped = (holder, changed, oldValue, newValue) -> {
            if (changed == attachment) listener.onChanged(holder, (T) oldValue, (T) newValue);
        };
        CHANGED.register(wrapped);
        return wrapped;
    }

    @SuppressWarnings("unchecked")
    public static <T> Removed onRemoved(Attachment<T> attachment, TypedRemoved<T> listener) {
        Removed wrapped = (holder, removed, oldValue) -> {
            if (removed == attachment) listener.onRemoved(holder, (T) oldValue);
        };
        REMOVED.register(wrapped);
        return wrapped;
    }

    /**
     * Listens to every change of one attachment (added, changed or removed) with the values holders read before and after,
     * defaults standing in for missing values. Same as {@link Attachment#onChange}.
     */
    public static <T> void onAnyChange(Attachment<T> attachment, Attachment.Listener<T> listener) {
        attachment.onChange(listener);
    }

    @FunctionalInterface
    public interface TypedChanging<T> {
        EventResultHolder<T> onChanging(AttachmentHolder holder, @Nullable T oldValue, @Nullable T newValue);
    }

    @FunctionalInterface
    public interface TypedAdded<T> {
        void onAdded(AttachmentHolder holder, T value);
    }

    @FunctionalInterface
    public interface TypedChanged<T> {
        void onChanged(AttachmentHolder holder, T oldValue, T newValue);
    }

    @FunctionalInterface
    public interface TypedRemoved<T> {
        void onRemoved(AttachmentHolder holder, T oldValue);
    }
}
