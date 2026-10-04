package net.ixdarklord.coolcatcore.api.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * Draws over an item wherever the game draws it in a GUI (inventory slots, the hotbar, a held stack...), after
 * vanilla's own decorations: the durability bar, the cooldown and the count. An item gets one by implementing
 * {@link net.ixdarklord.coolcatcore.api.item.DecoratedItem} on its class, which registers it by itself.
 * <pre>{@code
 * public final class FlaskChargesDecorator implements ItemDecorator {
 *     @Override
 *     public void extract(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
 *         int charges = FlaskItem.charges(stack);
 *         for (int i = 0; i < charges; i++) graphics.fill(x + 2 + i * 3, y + 13, x + 4 + i * 3, y + 15, 0xFF55FFFF);
 *     }
 * }
 * }</pre>
 * An item is 16x16 at ({@code x}, {@code y}). To draw at the item texture's own resolution, scale the pose (a 32x32
 * texture's pixel is half a GUI pixel) and restore it. To leave vanilla's bar out, return false from the item's
 * {@code isBarVisible}.
 */
@FunctionalInterface
public interface ItemDecorator {
    /**
     * @param x the item's left, in GUI pixels
     * @param y the item's top
     */
    void extract(GuiGraphics graphics, Font font, ItemStack stack, int x, int y);
}
