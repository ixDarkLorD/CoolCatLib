package net.ixdarklord.coolcatcore.internal.attachment;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

// The attachment values of a level (in its data folder) or of the server (in the overworld's, under an id of its own:
// this version has no data folder for the server), as saved data: read as a tag, then loaded into the store once it's
// bound, and written from the store when the game saves.
public final class AttachmentSavedData extends SavedData {
    public static final String LEVEL_ID = "coolcatlib_attachments";
    public static final String SERVER_ID = "coolcatlib_server_attachments";
    // (No data fixes of their own: the attachments' codecs read their own data. A fix type is still needed: the
    // vanilla loader can't read saved data without one.)
    public static final SavedData.Factory<AttachmentSavedData> FACTORY = new SavedData.Factory<>(AttachmentSavedData::new,
            (tag, registries) -> new AttachmentSavedData(tag), DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private @Nullable CompoundTag loaded;
    private @Nullable AttachmentStore store;
    private @Nullable HolderLookup.Provider registries;

    public AttachmentSavedData() {
        this(new CompoundTag());
    }

    private AttachmentSavedData(CompoundTag loaded) {
        this.loaded = loaded;
    }

    /** Ties the saved values to their store: loads them into it, and saves it from now on. */
    AttachmentStore bind(AttachmentStore store, HolderLookup.Provider registries) {
        if (this.store != null) return this.store;
        this.store = store;
        this.registries = registries;
        if (this.loaded != null && !this.loaded.isEmpty()) store.load(this.loaded, registries);
        this.loaded = null;
        store.onDirty(this::setDirty);
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (this.store == null) return this.loaded != null ? this.loaded.copy() : tag;
        this.store.save(tag, this.registries != null ? this.registries : registries);
        return tag;
    }
}
