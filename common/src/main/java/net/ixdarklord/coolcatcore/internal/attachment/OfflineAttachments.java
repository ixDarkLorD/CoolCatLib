package net.ixdarklord.coolcatcore.internal.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.ixdarklord.coolcatcore.api.attachment.Attachment;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.ixdarklord.coolcatcore.internal.mixin.PlayerDataStorageAccessor;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

// Attachment values of players who aren't online, read from and written to their data file (an online player's are
// their entity's). Their file holds each mod's values under <modid>:attachments, as the entity saves them.
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
        CompoundTag values = data.getCompound(tagName);
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
        return server.getPlayerList().getPlayer(id) != null || Files.exists(file(server, id, ".dat"));
    }

    private static <T> Codec<T> codec(Attachment<T> key) {
        Codec<T> codec = key.codec();
        if (codec == null || !key.isPersistent()) throw new IllegalArgumentException(key.id() + " isn't saved, so offline players have none");
        return codec;
    }

    private static DynamicOps<Tag> ops(MinecraftServer server) {
        return server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
    }

    private static Path folder(MinecraftServer server) {
        return ((PlayerDataStorageAccessor) ((ServerAttachments.PlayerDataAccess) server).coolcatlib$playerData()).coolcatlib$playerDir().toPath();
    }

    private static Path file(MinecraftServer server, UUID id, String suffix) {
        return folder(server).resolve(id + suffix);
    }

    // As the game loads a player (PlayerDataStorage.load, which needs the player object on this version): the data
    // file, else the previous one, brought up to the current data version.
    private static Optional<CompoundTag> load(MinecraftServer server, UUID id) {
        return read(file(server, id, ".dat"), id).or(() -> read(file(server, id, ".dat_old"), id)).map(tag ->
                DataFixTypes.PLAYER.updateToCurrentVersion(server.getFixerUpper(), tag, NbtUtils.getDataVersion(tag, -1)));
    }

    private static Optional<CompoundTag> read(Path file, UUID id) {
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            return Optional.of(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()));
        } catch (Exception e) {
            CoolCatCore.LOGGER.warn("Couldn't read the data of offline player {}", id, e);
            return Optional.empty();
        }
    }

    // As the game saves a player: to a temporary file first, then swapped in, keeping the last file as .dat_old.
    private static boolean write(MinecraftServer server, UUID id, CompoundTag data) {
        try {
            Path temporary = Files.createTempFile(folder(server), id + "-", ".dat");
            NbtIo.writeCompressed(NbtUtils.addCurrentDataVersion(data), temporary);
            Util.safeReplaceFile(file(server, id, ".dat"), temporary, file(server, id, ".dat_old"));
            return true;
        } catch (Exception e) {
            CoolCatCore.LOGGER.error("Couldn't save the attachments of offline player {}", id, e);
            return false;
        }
    }
}
