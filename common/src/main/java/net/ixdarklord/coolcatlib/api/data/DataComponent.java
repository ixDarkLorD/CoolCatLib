package net.ixdarklord.coolcatlib.api.data;

import com.mojang.serialization.Codec;
import net.ixdarklord.coolcatlib.api.utils.CodecUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

public abstract class DataComponent<T extends DataComponent<T>> {
    protected final Identifier id;
    protected final Codec<T> codec;

    protected DataComponent(Identifier id, Codec<T> codec) {
        this.id = id;
        this.codec = codec;
    }

    @SuppressWarnings("unchecked")
    public T onClientUpdate() {
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    protected void save(CompoundTag compoundTag) {
        CompoundTag result = (CompoundTag) CodecUtils.encode(this.codec, (T) this);
        compoundTag.put(id.toString(), result);
    }
}
