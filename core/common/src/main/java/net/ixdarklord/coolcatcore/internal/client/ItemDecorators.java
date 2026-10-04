package net.ixdarklord.coolcatcore.internal.client;

import net.ixdarklord.coolcatcore.api.client.gui.ItemDecorator;
import net.ixdarklord.coolcatcore.api.item.DecoratedItem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

// The decorators of the items that are DecoratedItems, registered by themselves: an item is asked for its own the
// first time the game draws it in a GUI, and they're kept for good. Other items have none.
public final class ItemDecorators {
    private static final Map<Item, List<ItemDecorator>> DECORATORS = new IdentityHashMap<>();

    private ItemDecorators() {}

    private static List<ItemDecorator> of(Item item) {
        List<ItemDecorator> decorators = DECORATORS.get(item);
        if (decorators == null) {
            List<ItemDecorator> own = new ArrayList<>();
            if (item instanceof DecoratedItem decorated) decorated.registerDecorators(own::add);
            decorators = List.copyOf(own);
            DECORATORS.put(item, decorators);
        }
        return decorators;
    }

    // Called where the game draws an item's decorations (the mixin on GuiGraphicsExtractor).
    public static void extract(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y) {
        List<ItemDecorator> decorators = of(stack.getItem());
        if (decorators.isEmpty()) return;
        for (ItemDecorator decorator : decorators) {
            graphics.pose().pushMatrix();
            decorator.extract(graphics, font, stack, x, y);
            graphics.pose().popMatrix();
        }
    }
}
