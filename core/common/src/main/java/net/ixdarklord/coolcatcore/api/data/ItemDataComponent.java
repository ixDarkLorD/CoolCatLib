package net.ixdarklord.coolcatcore.api.data;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * A {@link DataComponent} kept in an item stack's NBT tag, under its id.
 */
public abstract class ItemDataComponent<T extends ItemDataComponent<T>> extends DataComponent<T> {
    protected ItemStack stack;

    protected ItemDataComponent(ResourceLocation id, Codec<T> codec) {
        super(id, codec);
    }

    public ItemStack getStack() {
        return this.stack;
    }

    @SuppressWarnings("unchecked")
    public T setStack(ItemStack stack) {
        this.stack = stack;
        return (T) this;
    }

    /** Writes the data into its stack's tag. */
    public void save() {
        if (this.stack == null)
            throw new IllegalStateException("Cannot save %s data: stack is null! (did you call setStack()?)".formatted(this.id.toString()));
        super.save(this.stack.getOrCreateTag());
    }

    /** The data saved in a stack, bound to it, or {@code fallback}'s (also bound) when there's none. */
    public static <T extends ItemDataComponent<T>> T load(ItemStack stack, ResourceLocation id, Codec<T> codec, Supplier<T> fallback) {
        Optional<T> loaded = DataComponent.load(stack.getTag(), id, codec);
        return loaded.orElseGet(fallback).setStack(stack);
    }
}
