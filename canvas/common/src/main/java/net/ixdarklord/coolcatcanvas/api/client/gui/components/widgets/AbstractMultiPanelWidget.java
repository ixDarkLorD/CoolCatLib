package net.ixdarklord.coolcatcanvas.api.client.gui.components.widgets;

import com.google.common.collect.Lists;
import net.ixdarklord.coolcatcanvas.api.client.gui.components.widgets.panel.Panel;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * A (optionally draggable) window whose content area shows a stack of {@link Panel}s.
 * <ul>
 *     <li>Panels are drawn bottom to top in the order they were added; hidden panels are skipped.</li>
 *     <li>Input goes to the window's own widgets (title bar buttons...) first, then to the panels from the top down.
 *     A visible {@linkplain Panel#setModal(boolean) modal} panel stops input from reaching the panels below it,
 *     and those panels are dimmed with {@link #setOverlayColor(int) the overlay color}.</li>
 *     <li>The panel that accepted a mouse press receives the following drag and release events.</li>
 *     <li>Only the topmost visible panel receives keyboard input.</li>
 * </ul>
 * Panels are laid out in {@link #getPanelRectangle(Panel)} (the layout rectangle by default) whenever the window
 * moves or is resized with {@link #setBounds}.
 */
@ApiStatus.Experimental
public abstract class AbstractMultiPanelWidget extends AbstractDraggableWidget {
    private final List<Panel> panels = Lists.newArrayList();
    private final List<Panel> panelsView = Collections.unmodifiableList(this.panels);
    private @Nullable Panel capturedPanel;
    private int overlayColor = 0x80000000;
    private long lastClickTime;
    private int lastClickButton = -1;
    private ScreenRectangle laidOutRect = ScreenRectangle.empty();

    public AbstractMultiPanelWidget(Component title, int x, int y, int width, int height, boolean isMovable) {
        super(title, x, y, width, height, isMovable);
    }

    // --- Panels ---

    protected <P extends Panel> P addPanel(P panel) {
        panel.attach(this);
        this.panels.add(panel);
        this.laidOutRect = ScreenRectangle.empty();
        return panel;
    }

    protected void removePanel(Panel panel) {
        this.panels.remove(panel);
        if (this.capturedPanel == panel) this.capturedPanel = null;
    }

    public List<Panel> getPanels() {
        return this.panelsView;
    }

    /** The topmost visible panel, if any. */
    public @Nullable Panel getTopPanel() {
        for (int i = this.panels.size() - 1; i >= 0; i--) {
            if (this.panels.get(i).isVisible()) return this.panels.get(i);
        }
        return null;
    }

    /** Where a panel is placed; the layout rectangle unless overridden. */
    protected ScreenRectangle getPanelRectangle(Panel panel) {
        return this.layoutRectangle();
    }

    /** Color drawn over the panels below a visible modal panel. */
    public void setOverlayColor(int argb) {
        this.overlayColor = argb;
    }

    /** Moves and resizes the window. */
    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.layoutPanels(true);
    }

    private void layoutPanels(boolean force) {
        ScreenRectangle rect = this.layoutRectangle();
        if (!force && rect.equals(this.laidOutRect)) return;
        this.laidOutRect = rect;
        for (Panel panel : this.panels) {
            panel.setBounds(this.getPanelRectangle(panel));
        }
    }

    // --- Rendering ---

    @Override
    protected void updateChildren() {
        super.updateChildren();
        this.layoutPanels(false);
    }

    /** Whether the cursor is over one of the window's own widgets, which sit on top of the panels. */
    protected boolean isMouseOverOwnWidget(double mouseX, double mouseY) {
        for (GuiEventListener child : this.children()) {
            if (child instanceof AbstractWidget widget && widget.visible && widget.isMouseOver(mouseX, mouseY)) return true;
        }
        return false;
    }

    @Override
    protected void renderContents(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int topModal = this.indexOfTopModal();
        // Nothing under the window's own widgets gets hover states.
        boolean overWidget = this.isMouseOverOwnWidget(mouseX, mouseY);
        boolean first = true;
        for (int i = 0; i < this.panels.size(); i++) {
            Panel panel = this.panels.get(i);
            if (!panel.isVisible()) continue;
            // Batched text is drawn after all fills, so the panels below are flushed first for a panel
            // stacked on them to cover their text.
            if (!first) graphics.flush();
            first = false;
            if (i == topModal && i > 0) {
                ScreenRectangle r = this.layoutRectangle();
                graphics.fill(r.left(), r.top(), r.right(), r.bottom(), this.overlayColor);
            }
            // Panels below a modal one don't get hover states.
            boolean blocked = i < topModal || overWidget;
            panel.render(graphics, blocked ? -1 : mouseX, blocked ? -1 : mouseY, partialTick);
        }
        // Window widgets (title bar buttons) stay on top of the panels.
        if (!first) graphics.flush();
        super.renderContents(graphics, partialTick, mouseX, mouseY);
    }

    private int indexOfTopModal() {
        for (int i = this.panels.size() - 1; i >= 0; i--) {
            Panel panel = this.panels.get(i);
            if (panel.isVisible() && panel.isModal()) return i;
        }
        return -1;
    }

    // --- Input ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible) return false;
        // The same button pressed again within 250 ms, like the vanilla mouse handler counts double clicks.
        long now = Util.getMillis();
        boolean doubleClick = button == this.lastClickButton && now - this.lastClickTime < 250L;
        this.lastClickTime = now;
        this.lastClickButton = button;

        this.capturedPanel = null;
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        // A click on one of the window's widgets (even an inactive one) never reaches the panels below it.
        if (this.isMouseOverOwnWidget(mouseX, mouseY)) return true;

        for (int i = this.panels.size() - 1; i >= 0; i--) {
            Panel panel = this.panels.get(i);
            if (!panel.isVisible()) continue;
            if (panel.isMouseOver(mouseX, mouseY) && panel.mouseClicked(mouseX, mouseY, button, doubleClick)) {
                this.capturedPanel = panel;
                return true;
            }
            if (panel.isModal()) return this.isMouseOverLayoutRectangle(mouseX, mouseY);
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        Panel panel = this.capturedPanel;
        this.capturedPanel = null;
        boolean handled = panel != null && panel.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button) || handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!this.visible) return false;
        if (this.capturedPanel != null) return this.capturedPanel.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.visible) return false;
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        if (this.isMouseOverOwnWidget(mouseX, mouseY)) return false;
        for (int i = this.panels.size() - 1; i >= 0; i--) {
            Panel panel = this.panels.get(i);
            if (!panel.isVisible()) continue;
            if (panel.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
            if (panel.isModal()) return false;
        }
        return false;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (!this.visible) return;
        super.mouseMoved(mouseX, mouseY);
        Panel top = this.getTopPanel();
        if (top != null && !this.isMouseOverOwnWidget(mouseX, mouseY)) top.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (!this.visible) return false;
        Panel top = this.getTopPanel();
        return top != null && top.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (!this.visible) return false;
        if (super.keyReleased(keyCode, scanCode, modifiers)) return true;
        Panel top = this.getTopPanel();
        return top != null && top.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (super.charTyped(codePoint, modifiers)) return true;
        if (!this.visible) return false;
        Panel top = this.getTopPanel();
        return top != null && top.charTyped(codePoint, modifiers);
    }

    /** Whether the given panel is currently receiving a mouse drag. */
    protected boolean isCaptured(Panel panel) {
        return this.capturedPanel == panel;
    }

    @Override
    protected List<Component> getDebugInfo() {
        List<Component> info = Lists.newArrayList();
        info.add(Component.literal("Panels: " + this.panels.size()));
        info.add(Component.literal("Visible: " + this.panels.stream().filter(Panel::isVisible).count()));
        Panel top = this.getTopPanel();
        info.add(Component.literal("Top: " + (top == null ? "none" : top.getClass().getSimpleName())));
        info.add(Component.literal("Captured: " + (this.capturedPanel == null ? "none" : this.capturedPanel.getClass().getSimpleName())));
        return info;
    }
}
