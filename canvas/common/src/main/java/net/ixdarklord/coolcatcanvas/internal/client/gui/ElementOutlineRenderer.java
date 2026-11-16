package net.ixdarklord.coolcatcanvas.internal.client.gui;

import net.ixdarklord.coolcatcanvas.api.client.utils.Outline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

// Backs ElementOutlines; drawn from AbstractWidgetMixin after each widget.
public final class ElementOutlineRenderer {
    private static final Map<AbstractWidget, Entry> OUTLINES = new WeakHashMap<>();

    private ElementOutlineRenderer() {}

    public static void set(AbstractWidget widget, Outline outline, @Nullable Predicate<? super AbstractWidget> when) {
        OUTLINES.put(widget, new Entry(outline, when));
    }

    public static void remove(AbstractWidget widget) {
        OUTLINES.remove(widget);
    }

    public static @Nullable Outline get(AbstractWidget widget) {
        Entry entry = OUTLINES.get(widget);
        return entry != null ? entry.outline : null;
    }

    public static void clear() {
        OUTLINES.clear();
    }

    public static void render(AbstractWidget widget, GuiGraphicsExtractor graphics) {
        if (OUTLINES.isEmpty() || !widget.visible) return;
        Entry entry = OUTLINES.get(widget);
        if (entry == null || (entry.when != null && !entry.when.test(widget))) return;
        entry.outline.draw(graphics, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
    }

    private record Entry(Outline outline, @Nullable Predicate<? super AbstractWidget> when) {}
}
