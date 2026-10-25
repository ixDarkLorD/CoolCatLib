package net.ixdarklord.coolcatcore.internal.attachment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

// The attachment values of a level (in its data folder) or of the server (in the overworld's, under a name of its own:
// this version's server has no data storage of its own), as saved data: read as a tag, then loaded into the store once
// it's bound, and written from the store when the game saves.
public final class AttachmentSavedData extends SavedData {
    public static final String LEVEL = "coolcatlib_attachments";
    public static final String SERVER = "coolcatlib_server_attachments";

    private @Nullable CompoundTag loaded;
    private @Nullable AttachmentStore store;

    public AttachmentSavedData() {
        this(new CompoundTag());
    }

    private AttachmentSavedData(CompoundTag loaded) {
        this.loaded = loaded;
    }

    public static AttachmentSavedData load(CompoundTag tag) {
        return new AttachmentSavedData(tag);
    }

    /** Ties the saved values to their store: loads them into it, and saves it from now on. */
    AttachmentStore bind(AttachmentStore store) {
        if (this.store != null) return this.store;
        this.store = store;
        if (this.loaded != null && !this.loaded.isEmpty()) store.load(this.loaded);
        this.loaded = null;
        store.onDirty(this::setDirty);
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (this.store == null) {
            if (this.loaded != null) tag.merge(this.loaded);
            return tag;
        }
        this.store.save(tag);
        return tag;
    }
}
