package net.ixdarklord.coolcatcore.api.client.gui.screens;

import net.ixdarklord.coolcatcore.api.menu.StorageMenu;
import net.ixdarklord.coolcatcore.api.menu.StorageMenuDefinition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * A ready screen for any {@link StorageMenu}: a vanilla-style panel sized by its {@link StorageMenuDefinition}, a box
 * behind every slot, and the usual labels, with no texture needed. Register it as is
 * ({@code MenuScreenRegistry.register(CRATE_MENU, StorageScreen::new)}), or extend it to draw more:
 * <pre>{@code
 * public class CrateScreen extends StorageScreen {
 *     public CrateScreen(StorageMenu menu, Inventory inventory, Component title) {
 *         super(menu, inventory, title);
 *     }
 *
 *     @Override
 *     protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
 *         super.renderLabels(graphics, mouseX, mouseY);
 *         graphics.drawString(this.font, "Uses: " + this.menu.get(CRATE_USES), 100, 6, LABEL_COLOR, false);
 *     }
 * }
 * }</pre>
 * To draw a texture instead, override {@link #extractPanel}.
 */
public class StorageScreen extends AbstractContainerScreen<StorageMenu> {
    public static final int LABEL_COLOR = 0xFF404040;
    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;
    private static final int BORDER = 0xFF000000;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFF373737;

    public StorageScreen(StorageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = menu.getDefinition().width();
        this.imageHeight = menu.getDefinition().height();
        this.inventoryLabelX = menu.getDefinition().inventoryX();
        this.inventoryLabelY = menu.getDefinition().inventoryY() - 11;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        this.extractPanel(graphics, this.leftPos, this.topPos);
    }

    /** Draws the panel and the slot boxes, at the screen's top-left corner. */
    protected void extractPanel(GuiGraphics graphics, int left, int top) {
        drawPanel(graphics, left, top, this.imageWidth, this.imageHeight);
        for (Slot slot : this.menu.slots) {
            if (slot.isActive()) drawSlot(graphics, left + slot.x - 1, top + slot.y - 1);
        }
    }

    /** Presses a button of the menu's definition; its action runs on the server. */
    protected void pressButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    /** A raised vanilla-style panel. */
    public static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x + 1, y, x + width - 1, y + height, BORDER);
        graphics.fill(x, y + 1, x + width, y + height - 1, BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL);
        graphics.fill(x + 1, y + 1, x + width - 2, y + 3, LIGHT);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 2, LIGHT);
        graphics.fill(x + 2, y + height - 3, x + width - 1, y + height - 1, SHADOW);
        graphics.fill(x + width - 3, y + 2, x + width - 1, y + height - 1, SHADOW);
        graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, PANEL);
    }

    /** A sunken 18x18 slot box; (x, y) is its top-left corner, one pixel up and left of the item. */
    public static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, LIGHT);
        graphics.fill(x, y, x + 17, y + 17, SLOT_SHADOW);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT);
    }
}
