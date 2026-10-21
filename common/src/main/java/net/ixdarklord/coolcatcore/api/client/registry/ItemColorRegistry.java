package net.ixdarklord.coolcatcore.api.client.registry;

import net.ixdarklord.coolcatcore.internal.client.ItemColorHooks;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.world.level.ItemLike;

import java.util.List;
import java.util.function.Supplier;

/**
 * Colours items' tinted faces (a model's {@code tintindex}), the same on every loader. Register while the client
 * initialises; the items are looked up once the game's item colours are made.
 * <pre>{@code
 * ItemColorRegistry.register((stack, tintIndex) -> tintIndex == 0 ? 0xFF8040C0 : -1, MyItems.WAND);
 * }</pre>
 */
public final class ItemColorRegistry {
    private ItemColorRegistry() {}

    @SafeVarargs
    public static void register(ItemColor color, Supplier<? extends ItemLike>... items) {
        ItemColorHooks.add(color, List.of(items));
    }
}
