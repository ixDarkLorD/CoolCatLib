package net.ixdarklord.coolcatlib.api.client.gui.components.widgets.panel;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.ApiStatus;

/**
 * A panel whose contents scroll vertically inside {@link #getScrollArea()} (by default the whole panel;
 * override it to leave room for a header/footer drawn in {@link #extractFrame}).
 * A thin scrollbar is drawn at the right edge of the scroll area when the content overflows.
 */
@ApiStatus.Experimental
public abstract class ScrollPanel extends Panel {
    protected static final int SCROLLBAR_WIDTH = 3;
    private double scroll;
    private boolean draggingBar;
    private int scrollStep = 10;
    private int barColor = 0xFFAEAEAE;
    private int trackColor = 0x40000000;

    /** Total height of the scrolled content. */
    protected abstract int getContentHeight();

    /** Draws the scrolled content; {@code top} is where content y = 0 lands on screen. Already clipped. */
    protected abstract void extractScrolled(GuiGraphicsExtractor graphics, int left, int top, int width, int mouseX, int mouseY, float partialTick);

    /** Unclipped drawing around the scroll area (background, header, footer). */
    protected void extractFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    protected ScreenRectangle getScrollArea() {
        return this.getBounds();
    }

    public ScrollPanel setScrollStep(int step) {
        this.scrollStep = Math.max(1, step);
        return this;
    }

    public ScrollPanel setScrollbarColors(int bar, int track) {
        this.barColor = bar;
        this.trackColor = track;
        return this;
    }

    public double getScroll() {
        return this.scroll;
    }

    public void setScroll(double scroll) {
        this.scroll = Mth.clamp(scroll, 0, this.getMaxScroll());
    }

    public int getMaxScroll() {
        return Math.max(0, this.getContentHeight() - this.getScrollArea().height());
    }

    public boolean canScroll() {
        return this.getMaxScroll() > 0;
    }

    /**
     * Width available to content. Room for the scrollbar is always reserved: whether it shows depends on the
     * content height, which usually depends on this width.
     */
    protected int getContentWidth() {
        return Math.max(0, this.getScrollArea().width() - SCROLLBAR_WIDTH - 2);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.setScroll(this.scroll);
        this.extractFrame(graphics, mouseX, mouseY, partialTick);

        ScreenRectangle area = this.getScrollArea();
        if (area.width() <= 0 || area.height() <= 0) return;
        graphics.enableScissor(area.left(), area.top(), area.right(), area.bottom());
        this.extractScrolled(graphics, area.left(), area.top() - (int) this.scroll, this.getContentWidth(), mouseX, mouseY, partialTick);
        graphics.disableScissor();

        if (this.canScroll()) {
            ScreenRectangle bar = this.getBarRectangle();
            graphics.fill(bar.left(), area.top(), bar.right(), area.bottom(), this.trackColor);
            graphics.fill(bar.left(), bar.top(), bar.right(), bar.bottom(), this.barColor);
        }
    }

    private ScreenRectangle getBarRectangle() {
        ScreenRectangle area = this.getScrollArea();
        int height = Mth.clamp(area.height() * area.height() / Math.max(1, this.getContentHeight()), 8, area.height());
        int top = area.top() + (int) ((area.height() - height) * (this.scroll / Math.max(1, this.getMaxScroll())));
        return new ScreenRectangle(area.right() - SCROLLBAR_WIDTH, top, SCROLLBAR_WIDTH, height);
    }

    /** Whether a screen position is inside the (visible part of the) scroll area. */
    protected boolean isInScrollArea(double mouseX, double mouseY) {
        return this.getScrollArea().containsPoint((int) mouseX, (int) mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (this.canScroll()) {
            ScreenRectangle area = this.getScrollArea();
            ScreenRectangle bar = this.getBarRectangle();
            if (event.x() >= bar.left() && event.x() < bar.right() && event.y() >= area.top() && event.y() < area.bottom()) {
                this.draggingBar = true;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingBar) {
            ScreenRectangle area = this.getScrollArea();
            int travel = Math.max(1, area.height() - this.getBarRectangle().height());
            this.setScroll(this.scroll + dragY * this.getMaxScroll() / travel);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.draggingBar) {
            this.draggingBar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.canScroll() || scrollY == 0 || !this.isMouseOver(mouseX, mouseY)) return false;
        this.setScroll(this.scroll - scrollY * this.scrollStep);
        return true;
    }

    @Override
    protected void onHidden() {
        this.draggingBar = false;
    }
}
