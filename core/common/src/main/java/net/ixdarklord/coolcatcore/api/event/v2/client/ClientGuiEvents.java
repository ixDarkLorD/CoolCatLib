package net.ixdarklord.coolcatcore.api.event.v2.client;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The in-game GUI.
 */
public final class ClientGuiEvents {
    public static final EventInvoker<RenderHud> RENDER_HUD = EventInvoker.create(RenderHud.class, listeners -> (graphics, partialTick) -> {
        for (RenderHud listener : listeners) listener.render(graphics, partialTick);
    });

    private ClientGuiEvents() {}

    /** Draws over the vanilla HUD, every frame. {@code partialTick} is the frame's progress into the next tick. */
    @FunctionalInterface
    public interface RenderHud {
        void render(GuiGraphics graphics, float partialTick);
    }
}
