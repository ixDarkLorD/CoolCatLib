package net.ixdarklord.coolcatcore.internal.client;

import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

// ItemColorRegistry's colours, put into the game's item colours as they are made (ItemColorsMixin): every loader makes
// them in the same place, once every item is registered.
public final class ItemColorHooks {
    private record Entry(ItemColor color, List<Supplier<? extends ItemLike>> items) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static ItemColors made;

    private ItemColorHooks() {}

    public static synchronized void add(ItemColor color, List<Supplier<? extends ItemLike>> items) {
        Entry entry = new Entry(color, items);
        ENTRIES.add(entry);
        if (made != null) apply(made, entry);
    }

    public static synchronized void onCreated(ItemColors colors) {
        made = colors;
        ENTRIES.forEach(entry -> apply(colors, entry));
    }

    private static void apply(ItemColors colors, Entry entry) {
        colors.register(entry.color(), entry.items().stream().map(Supplier::get).toArray(ItemLike[]::new));
    }
}
