package net.ixdarklord.coolcatcanvas.api.client.gui;

import net.ixdarklord.coolcatcanvas.internal.client.gui.HudTakeoverManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.function.BooleanSupplier;

/**
 * Takes the HUD over: while a condition holds, the game's own HUD (hotbar, health, crosshair, chat, and whatever
 * other mods add to it) is not drawn, and a view of the mod's own is drawn in its place. For looking through
 * something: a scope, a lens, a helmet's visor.
 * <pre>{@code
 * HudTakeover.register(MyMod.id("scope"), () -> MyScope.isRaised(), (graphics, delta) -> MyScope.draw(graphics));
 * }</pre>
 * If several takeovers are active at once, the HUD is hidden and each draws, in the order they were registered.
 * Client code only.
 */
public final class HudTakeover {
    private HudTakeover() {}

    /** What is drawn in the HUD's place. */
    @FunctionalInterface
    public interface View {
        void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker);
    }

    /**
     * @param active asked every frame; while true the HUD is this view's
     * @throws IllegalArgumentException if the id is taken
     */
    public static void register(Identifier id, BooleanSupplier active, View view) {
        HudTakeoverManager.register(id, active, view);
    }

    public static boolean unregister(Identifier id) {
        return HudTakeoverManager.unregister(id);
    }
}
