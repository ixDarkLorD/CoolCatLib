package net.ixdarklord.coolcatlib.api.client.gui.components.widgets.panel;

import com.google.common.collect.Lists;
import net.ixdarklord.coolcatlib.api.client.gui.components.widgets.AbstractMultiPanelWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One layer of an {@link AbstractMultiPanelWidget}. Panels are stacked in the order they are added:
 * the topmost visible panel gets input first, and a {@linkplain #setModal(boolean) modal} panel blocks
 * input to (and dims) the panels below it.
 * <p>
 * A panel can own plain widgets (buttons, edit boxes...) through {@link #addChild(AbstractWidget)}; they are
 * drawn after {@link #extractContents} and receive mouse input before the panel itself. Lay them out in
 * {@link #onResized()}.
 */
@ApiStatus.Experimental
public abstract class Panel implements Renderable {
    protected final Minecraft minecraft = Minecraft.getInstance();
    protected final Font font = this.minecraft.font;
    private final List<AbstractWidget> children = Lists.newArrayList();
    private @Nullable AbstractWidget pressedChild;
    private @Nullable AbstractMultiPanelWidget owner;
    private ScreenRectangle bounds = ScreenRectangle.empty();
    private boolean visible = true;
    private boolean modal;

    // --- Lifecycle ---

    /** Called by the owning widget; a panel belongs to one widget. */
    @ApiStatus.Internal
    public final void attach(AbstractMultiPanelWidget owner) {
        if (this.owner != null && this.owner != owner)
            throw new IllegalStateException("Panel is already attached to another widget");
        this.owner = owner;
    }

    public final @Nullable AbstractMultiPanelWidget getOwner() {
        return this.owner;
    }

    /** Sets the panel's screen-space bounds; {@link #onResized()} runs only when they change. */
    public final void setBounds(ScreenRectangle bounds) {
        if (!bounds.equals(this.bounds)) {
            this.bounds = bounds;
            this.onResized();
        }
    }

    public final ScreenRectangle getBounds() {
        return this.bounds;
    }

    /** Lay out children here. */
    protected void onResized() {}

    protected void onShown() {}

    protected void onHidden() {}

    public final boolean isVisible() {
        return this.visible;
    }

    public final void setVisible(boolean visible) {
        if (this.visible == visible) return;
        this.visible = visible;
        this.pressedChild = null;
        if (visible) this.onShown();
        else this.onHidden();
    }

    public final boolean isModal() {
        return this.modal;
    }

    /** A modal panel dims the panels below it and swallows input that would reach them. */
    public final Panel setModal(boolean modal) {
        this.modal = modal;
        return this;
    }

    // --- Children ---

    protected <W extends AbstractWidget> W addChild(W widget) {
        this.children.add(widget);
        return widget;
    }

    protected void removeChild(AbstractWidget widget) {
        this.children.remove(widget);
        if (this.pressedChild == widget) this.pressedChild = null;
    }

    protected List<AbstractWidget> children() {
        return this.children;
    }

    // --- Rendering ---

    /** Draws the panel's own contents; children are drawn on top afterwards. */
    protected abstract void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick);

    @Override
    public final void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractContents(graphics, mouseX, mouseY, partialTick);
        for (AbstractWidget child : this.children) {
            child.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }

    // --- Input (return true when handled) ---

    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.bounds.containsPoint((int) mouseX, (int) mouseY);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (int i = this.children.size() - 1; i >= 0; i--) {
            AbstractWidget child = this.children.get(i);
            if (child.visible && child.active && child.mouseClicked(event, doubleClick)) {
                this.pressedChild = child;
                return true;
            }
        }
        return false;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        AbstractWidget child = this.pressedChild;
        this.pressedChild = null;
        return child != null && child.mouseReleased(event);
    }

    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return this.pressedChild != null && this.pressedChild.mouseDragged(event, dragX, dragY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    public void mouseMoved(double mouseX, double mouseY) {}

    public boolean keyPressed(KeyEvent event) {
        return false;
    }

    public boolean keyReleased(KeyEvent event) {
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        return false;
    }
}
