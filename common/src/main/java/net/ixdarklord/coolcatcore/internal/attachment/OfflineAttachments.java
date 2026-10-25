package net.ixdarklord.coolcatcore.internal.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.ixdarklord.coolcatcore.api.attachment.Attachment;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.ixdarklord.coolcatcore.internal.mixin.PlayerDataStorageAccessor;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

// Attachment values of players who aren't online, read from and written to their data file (an online player's are
// their entity's). Their file holds each mod's values under <modid>:attachments, as the entity saves them. (This
// version's PlayerDataStorage only loads into a player object, so the file is read here, and brought up to date the way
// it would be.)
public final class OfflineAttachments {
    private OfflineAttachments() {}

    public static <T> T get(MinecraftServer server, UUID id, Attachment<T> key) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return key.get(online);
        Codec<T> codec = codec(key);
        String tagName = key.registry().tagName();
        return load(server, id).filter(data -> data.contains(tagName, Tag.TAG_COMPOUND))
                .map(data -> data.getCompound(tagName).get(key.name()))
                .flatMap(value -> codec.parse(ops(server), value).resultOrPartial(error -> CoolCatCore.LOGGER.error("Couldn't read {} of offline player {}: {}", key.id(), id, error)))
                .orElseGet(key::createDefault);
    }

    /** Sets the value (or, for null, removes it). False if the player has never played here. */
    public static <T> boolean set(MinecraftServer server, UUID id, Attachment<T> key, @Nullable T value) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            if (value == null) key.reset(online);
            else key.set(online, value);
            return true;
        }
        Codec<T> codec = codec(key);
        Optional<CompoundTag> loaded = load(server, id);
        if (loaded.isEmpty()) return false;
        CompoundTag data = loaded.get();
        String tagName = key.registry().tagName();
        CompoundTag values = data.contains(tagName, Tag.TAG_COMPOUND) ? data.getCompound(tagName) : new CompoundTag();
        // As an entity saves them: a value equal to the default isn't written.
        if (value == null || value.equals(key.createDefault())) {
            values.remove(key.name());
        } else {
            Optional<Tag> encoded = codec.encodeStart(ops(server), value).resultOrPartial(error -> CoolCatCore.LOGGER.error("Couldn't write {} of offline player {}: {}", key.id(), id, error));
            if (encoded.isEmpty()) return false;
            values.put(key.name(), encoded.get());
        }
        if (values.isEmpty()) data.remove(tagName);
        else data.put(tagName, values);
        return write(server, id, data);
    }

    public static <T> boolean update(MinecraftServer server, UUID id, Attachment<T> key, UnaryOperator<T> function) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            key.update(online, function);
            return true;
        }
        if (load(server, id).isEmpty()) return false;
        return set(server, id, key, function.apply(get(server, id, key)));
    }

    /** Whether the player has data here (has played on this world or server), online or not. */
    public static boolean exists(MinecraftServer server, UUID id) {
        return server.getPlayerList().getPlayer(id) != null || file(server, id, ".dat").isFile();
    }

    private static <T> Codec<T> codec(Attachment<T> key) {
        Codec<T> codec = key.codec();
        if (codec == null || !key.isPersistent()) throw new IllegalArgumentException(key.id() + " isn't saved, so offline players have none");
        return codec;
    }

    private static DynamicOps<Tag> ops(MinecraftServer server) {
        return RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
    }

    private static PlayerDataStorageAccessor storage(MinecraftServer server) {
        return (PlayerDataStorageAccessor) ((ServerAttachments.PlayerDataAccess) server).coolcatlib$playerData();
    }

    // As the game reads a player's file: brought up to this version's data, as it would be to load the player.
    private static Optional<CompoundTag> load(MinecraftServer server, UUID id) {
        File file = file(server, id, ".dat");
        if (!file.isFile()) return Optional.empty();
        try {
            CompoundTag data = NbtIo.readCompressed(file);
            int version = NbtUtils.getDataVersion(data, -1);
            return Optional.of(DataFixTypes.PLAYER.updateToCurrentVersion(storage(server).coolcatlib$fixerUpper(), data, version));
        } catch (Exception e) {
            CoolCatCore.LOGGER.error("Couldn't read the data of offline player {}", id, e);
            return Optional.empty();
        }
    }

    private static File file(MinecraftServer server, UUID id, String suffix) {
        return new File(storage(server).coolcatlib$playerDir(), id + suffix);
    }

    // As the game saves a player: to a temporary file first, then swapped in, keeping the last file as .dat_old.
    private static boolean write(MinecraftServer server, UUID id, CompoundTag data) {
        try {
            File temporary = File.createTempFile(id + "-", ".dat", storage(server).coolcatlib$playerDir());
            NbtIo.writeCompressed(NbtUtils.addCurrentDataVersion(data), temporary);
            Util.safeReplaceFile(file(server, id, ".dat"), temporary, file(server, id, ".dat_old"));
            return true;
        } catch (Exception e) {
            CoolCatCore.LOGGER.error("Couldn't save the attachments of offline player {}", id, e);
            return false;
        }
    }
}
