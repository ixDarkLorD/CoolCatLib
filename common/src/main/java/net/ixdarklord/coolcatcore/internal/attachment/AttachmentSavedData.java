package net.ixdarklord.coolcatcore.internal.attachment;

import com.mojang.serialization.Codec;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.Nullable;

// The attachment values of a level (in its data folder) or of the server (in the server's), as saved data: read as a
// tag, then loaded into the store once it's bound, and written from the store when the game saves.
public final class AttachmentSavedData extends SavedData {
    private static final Codec<AttachmentSavedData> CODEC = CompoundTag.CODEC.xmap(AttachmentSavedData::new, AttachmentSavedData::write);
    // (No data fixes of their own: the attachments' codecs read their own data.)
    public static final SavedDataType<AttachmentSavedData> LEVEL = new SavedDataType<>(CoolCatCore.rl("attachments"), AttachmentSavedData::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    public static final SavedDataType<AttachmentSavedData> SERVER = new SavedDataType<>(CoolCatCore.rl("server_attachments"), AttachmentSavedData::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

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
        if (this.loaded != null && !this.loaded.isEmpty()) store.load(TagValueInput.create(ProblemReporter.DISCARDING, registries, this.loaded));
        this.loaded = null;
        store.onDirty(this::setDirty);
        return store;
    }

    private CompoundTag write() {
        if (this.store == null || this.registries == null) return this.loaded != null ? this.loaded : new CompoundTag();
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, this.registries);
        this.store.save(output);
        return output.buildResult();
    }
}
