package net.ixdarklord.coolcatcore.api.attachment;

import org.jetbrains.annotations.Nullable;

/**
 * A mutable value that reports its own changes. When a holder stores one, it listens, so editing the value in place
 * (like a {@link net.ixdarklord.coolcatcore.api.container.SlotContainer}'s slots) saves and syncs it without a
 * call to {@link AttachmentHolder#markDirty}.
 * <p>
 * Trackable values can't be stored on item stacks: stack data must be immutable.
 */
public interface Trackable {
    /** Called by the holder storing this value; call {@code listener} after every change. {@code null} unbinds. */
    void setChangeListener(@Nullable Runnable listener);
}
