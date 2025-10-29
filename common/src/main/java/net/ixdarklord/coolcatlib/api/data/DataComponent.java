package net.ixdarklord.coolcatlib.api.data;

import com.mojang.serialization.Codec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.ixdarklord.coolcatlib.api.utils.CodecUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public abstract class DataComponent<T extends DataComponent<T>> {
    protected final ResourceLocation id;
    protected final Codec<T> codec;

    protected DataComponent(ResourceLocation id, Codec<T> codec) {
        this.id = id;
        this.codec = codec;
    }

    @SuppressWarnings("unchecked")
    @Environment(EnvType.CLIENT)
    public T onClientUpdate() {
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    protected void save(CompoundTag compoundTag) {
        CompoundTag result = (CompoundTag) CodecUtils.encode(this.codec, (T) this);
        compoundTag.put(id.toString(), result);
    }
}
