package net.ixdarklord.coolcatlib.api.data;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

public abstract class ItemDataComponent<T extends ItemDataComponent<T>> extends DataComponent<T> {
    protected ItemStack stack;
    protected final DataComponentType<T> type;

    protected ItemDataComponent(DataComponentType<T> type) {
        super(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type), type.codec());
        this.type = type;
    }

    public ItemStack getStack() {
        return stack;
    }

    @SuppressWarnings("unchecked")
    public T setStack(ItemStack stack) {
        this.stack = stack;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public void save() {
        if (stack == null)
            throw new IllegalStateException("Cannot save %s data: stack is null! (did you call setStack()?)".formatted(id.toString()));
        stack.set(type, (T) this);
    }
}
