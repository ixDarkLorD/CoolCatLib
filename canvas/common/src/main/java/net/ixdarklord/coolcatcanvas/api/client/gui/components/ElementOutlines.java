package net.ixdarklord.coolcatcanvas.api.client.gui.components;

import net.ixdarklord.coolcatcanvas.api.client.utils.Outline;
import net.ixdarklord.coolcatcanvas.internal.client.gui.ElementOutlineRenderer;
import net.minecraft.client.gui.components.AbstractWidget;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Outlines drawn around specific widgets, any {@link AbstractWidget} (vanilla's buttons, edit boxes, sliders or your
 * own), without subclassing them. The outline is drawn right after the widget, only while it's visible.
 * <pre>{@code
 * ElementOutlines.set(button, Outline.rainbow().withThickness(2));
 * ElementOutlines.set(editBox, Outline.solid(0xFFFFAA00).withPadding(1), AbstractWidget::isFocused);
 * }</pre>
 * Widgets are held weakly, so a widget that's thrown away (its screen closed) drops its outline too. Call from the
 * render thread.
 */
public final class ElementOutlines {
    private ElementOutlines() {}

    /** Outlines the widget whenever it's drawn, replacing any outline it had. */
    public static void set(AbstractWidget widget, Outline outline) {
        ElementOutlineRenderer.set(widget, outline, null);
    }

    /** Outlines the widget whenever it's drawn and {@code when} holds, e.g. {@code AbstractWidget::isHoveredOrFocused}. */
    public static void set(AbstractWidget widget, Outline outline, @Nullable Predicate<? super AbstractWidget> when) {
        ElementOutlineRenderer.set(widget, outline, when);
    }

    public static void remove(AbstractWidget widget) {
        ElementOutlineRenderer.remove(widget);
    }

    public static @Nullable Outline get(AbstractWidget widget) {
        return ElementOutlineRenderer.get(widget);
    }

    public static boolean has(AbstractWidget widget) {
        return ElementOutlineRenderer.get(widget) != null;
    }

    public static void clear() {
        ElementOutlineRenderer.clear();
    }
}
