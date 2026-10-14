package net.ixdarklord.coolcatcore.internal.attachment;

import net.ixdarklord.coolcatcore.api.event.v2.common.AttachmentEvents;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventResultHolder;
import net.ixdarklord.coolcatcore.api.attachment.AttachmentHolder;
import net.ixdarklord.coolcatcore.api.attachment.Attachment;
import org.jetbrains.annotations.Nullable;

// Fires AttachmentEvents (and the key's own listeners) for the holders.
public final class AttachmentEventHooks {
    /** What {@link #changing} returns when a listener cancelled the change. */
    public static final Object CANCELLED = new Object();

    private AttachmentEventHooks() {}

    /**
     * Asks the CHANGING listeners about a change.
     *
     * @return {@link #CANCELLED}, or the value to store (a listener's replacement, or {@code newValue})
     */
    public static <T> @Nullable Object changing(AttachmentHolder holder, Attachment<T> key, @Nullable T oldValue, @Nullable T newValue) {
        if (!AttachmentEvents.CHANGING.hasListeners()) return newValue;
        EventResultHolder<Object> result = AttachmentEvents.CHANGING.invoker().onChanging(holder, key, oldValue, newValue);
        if (result.isPass()) return newValue;
        if (!result.result().getAsBoolean()) return CANCELLED;
        return result.getValue().orElse(newValue);
    }

    /** Tells listeners about a change; {@code null} stands for no stored value. */
    public static <T> void changed(AttachmentHolder holder, Attachment<T> key, @Nullable T oldValue, @Nullable T newValue) {
        if (oldValue == null && newValue != null) {
            if (AttachmentEvents.ADDED.hasListeners()) AttachmentEvents.ADDED.invoker().onAdded(holder, key, newValue);
        } else if (oldValue != null && newValue == null) {
            if (AttachmentEvents.REMOVED.hasListeners()) AttachmentEvents.REMOVED.invoker().onRemoved(holder, key, oldValue);
        } else if (oldValue != null) {
            if (AttachmentEvents.CHANGED.hasListeners()) AttachmentEvents.CHANGED.invoker().onChanged(holder, key, oldValue, newValue);
        } else {
            return;
        }
        key.fireChanged(holder, oldValue != null ? oldValue : key.createDefault(), newValue != null ? newValue : key.createDefault());
    }
}
