package net.ixdarklord.coolcatlib.api.client.gui.components.widgets;

import com.google.common.collect.Lists;
import net.ixdarklord.coolcatlib.api.client.gui.components.widgets.panel.Panel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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
    protected void renderContents(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        int topModal = this.indexOfTopModal();
        // Nothing under the window's own widgets gets hover states.
        boolean overWidget = this.isMouseOverOwnWidget(mouseX, mouseY);
        boolean first = true;
        for (int i = 0; i < this.panels.size(); i++) {
            Panel panel = this.panels.get(i);
            if (!panel.isVisible()) continue;
            // Within a stratum text is drawn after all fills, so a panel stacked on another needs its own
            // stratum to cover the text below it.
            if (!first) graphics.nextStratum();
            first = false;
            if (i == topModal && i > 0) {
                ScreenRectangle r = this.layoutRectangle();
                graphics.fill(r.left(), r.top(), r.right(), r.bottom(), this.overlayColor);
            }
            // Panels below a modal one don't get hover states.
            boolean blocked = i < topModal || overWidget;
            panel.extractRenderState(graphics, blocked ? -1 : mouseX, blocked ? -1 : mouseY, partialTick);
        }
        // Window widgets (title bar buttons) stay on top of the panels.
        if (!first) graphics.nextStratum();
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.visible) return false;
        this.capturedPanel = null;
        if (super.mouseClicked(event, doubleClick)) return true;
        // A click on one of the window's widgets (even an inactive one) never reaches the panels below it.
        if (this.isMouseOverOwnWidget(event.x(), event.y())) return true;

        for (int i = this.panels.size() - 1; i >= 0; i--) {
            Panel panel = this.panels.get(i);
            if (!panel.isVisible()) continue;
            if (panel.isMouseOver(event.x(), event.y()) && panel.mouseClicked(event, doubleClick)) {
                this.capturedPanel = panel;
                return true;
            }
            if (panel.isModal()) return this.isMouseOverLayoutRectangle(event.x(), event.y());
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        Panel panel = this.capturedPanel;
        this.capturedPanel = null;
        boolean handled = panel != null && panel.mouseReleased(event);
        return super.mouseReleased(event) || handled;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!this.visible) return false;
        if (this.capturedPanel != null) return this.capturedPanel.mouseDragged(event, dragX, dragY);
        return super.mouseDragged(event, dragX, dragY);
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
    public boolean keyPressed(KeyEvent event) {
        if (super.keyPressed(event)) return true;
        if (!this.visible) return false;
        Panel top = this.getTopPanel();
        return top != null && top.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (!this.visible) return false;
        if (super.keyReleased(event)) return true;
        Panel top = this.getTopPanel();
        return top != null && top.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (super.charTyped(event)) return true;
        if (!this.visible) return false;
        Panel top = this.getTopPanel();
        return top != null && top.charTyped(event);
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
