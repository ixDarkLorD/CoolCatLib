package net.ixdarklord.coolcatlib.api.data;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public abstract class ItemDataComponent<T extends DataComponent<T>> extends DataComponent<T> {
    protected ItemStack stack;

    protected ItemDataComponent(ResourceLocation dataId, Codec<T> codec) {
        super(dataId, codec);
    }

    public ItemStack getStack() {
        return this.stack;
    }

    @SuppressWarnings("unchecked")
    public T setStack(ItemStack itemStack) {
        this.stack = itemStack;
        return (T) this;
    }

    public void save() {
        if (stack == null)
            throw new IllegalStateException("Cannot save %s data: stack is null! (did you call setStack()?)".formatted(id.toString()));
        super.save(stack.getOrCreateTag());
    }
}
