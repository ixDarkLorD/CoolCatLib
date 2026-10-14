package net.ixdarklord.coolcatcore.api.data;

import com.mojang.serialization.Codec;
import net.ixdarklord.coolcatcore.api.utils.CodecUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A piece of data saved with a {@link Codec} under its id in a {@link CompoundTag}. 1.20.1 has no data components:
 * this is CoolCatLib's NBT-backed stand-in, stored as {@code "<id>": {...}}.
 */
public abstract class DataComponent<T extends DataComponent<T>> {
    protected final ResourceLocation id;
    protected final Codec<T> codec;

    protected DataComponent(ResourceLocation id, Codec<T> codec) {
        this.id = id;
        this.codec = codec;
    }

    public ResourceLocation getId() {
        return this.id;
    }

    @SuppressWarnings("unchecked")
    public T onClientUpdate() {
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    protected void save(CompoundTag compoundTag) {
        Tag result = CodecUtils.encode(this.codec, (T) this);
        compoundTag.put(this.id.toString(), result);
    }

    /** Reads the data saved under {@code id} in {@code compoundTag}, if there is any and it can be read. */
    public static <T> Optional<T> load(@Nullable CompoundTag compoundTag, ResourceLocation id, Codec<T> codec) {
        if (compoundTag == null || !compoundTag.contains(id.toString())) return Optional.empty();
        return codec.parse(CodecUtils.registryOps(NbtOps.INSTANCE), compoundTag.get(id.toString())).result();
    }
}
