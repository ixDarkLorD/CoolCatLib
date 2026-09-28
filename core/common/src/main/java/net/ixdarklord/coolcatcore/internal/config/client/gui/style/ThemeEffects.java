package net.ixdarklord.coolcatcore.internal.config.client.gui.style;

import net.ixdarklord.coolcatcore.api.config.ConfigTheme;
import net.ixdarklord.coolcatcore.api.config.client.ConfigEffect;
import net.ixdarklord.coolcatcore.api.config.client.ConfigEffects;
import net.ixdarklord.coolcatcore.internal.config.client.CoolCatCoreClientSettings;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.resources.Identifier;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Runs the current theme's effects at each layer. An id nobody registered, or an effect that throws, is logged once and
// then skipped, so one broken effect never breaks a screen.
public final class ThemeEffects {
    private static final long START = System.nanoTime();
    private static final Set<Identifier> MISSING = ConcurrentHashMap.newKeySet();
    private static final Set<Identifier> FAILED = ConcurrentHashMap.newKeySet();
    private static ConfigEffect.Context lastContext = context(0, 0, -1, -1, 0);

    private ThemeEffects() {}

    /** Registers the built-in effects; called once on the client. */
    public static void init() {
        ConfigEffects.register(ConfigTheme.STARFALL, new StarfallEffect());
    }

    /** Behind the panels, over the theme's background. */
    public static void background(GuiGraphicsExtractor graphics, int width, int height, int mouseX, int mouseY, float partialTick) {
        ConfigEffect.Context context = context(width, height, mouseX, mouseY, partialTick);
        lastContext = context;
        run(context.theme(), effect -> effect.extractBackground(graphics, context));
    }

    /** Over the whole screen, under tooltips. */
    public static void foreground(GuiGraphicsExtractor graphics, int width, int height, int mouseX, int mouseY, float partialTick) {
        ConfigEffect.Context context = context(width, height, mouseX, mouseY, partialTick);
        run(context.theme(), effect -> effect.extractForeground(graphics, context));
    }

    /** Over a widget just drawn. */
    public static void widget(GuiGraphicsExtractor graphics, ConfigEffect.WidgetKind kind, AbstractWidget widget) {
        widget(graphics, kind, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), widget.isHovered(), widget.isFocused(), widget.active);
    }

    /** Over something drawn like a widget, by its bounds. */
    public static void widget(GuiGraphicsExtractor graphics, ConfigEffect.WidgetKind kind, int x, int y, int width, int height,
                              boolean hovered, boolean focused, boolean active) {
        if (!CoolCatCoreClientSettings.themeEffects()) return;
        ConfigEffect.WidgetContext context = new ConfigEffect.WidgetContext(kind, x, y, width, height, hovered, focused, active, withTheme(lastContext));
        run(context.screen().theme(), effect -> effect.extractWidget(graphics, context));
    }

    private static void run(ConfigTheme theme, java.util.function.Consumer<ConfigEffect> call) {
        if (!CoolCatCoreClientSettings.themeEffects()) return;
        for (Identifier id : theme.effects()) {
            ConfigEffect effect = ConfigEffects.get(id);
            if (effect == null) {
                if (MISSING.add(id)) CoolCatCore.LOGGER.warn("Config screens: no effect is registered as {}", id);
                continue;
            }
            if (FAILED.contains(id)) continue;
            try {
                call.accept(effect);
            } catch (RuntimeException e) {
                FAILED.add(id);
                CoolCatCore.LOGGER.error("Config screens: the effect {} failed and is switched off", id, e);
            }
        }
    }

    private static ConfigEffect.Context context(int width, int height, int mouseX, int mouseY, float partialTick) {
        ConfigTheme theme = ConfigStyle.theme();
        ConfigTheme.Mode mode = ConfigStyle.mode();
        // No Minecraft instance yet when this class loads during a data run (NeoForge datagen constructs client mods).
        Minecraft minecraft = Minecraft.getInstance();
        return new ConfigEffect.Context(width, height, mouseX, mouseY, (System.nanoTime() - START) / 1_000_000_000F, partialTick,
                theme, theme.colors(mode), mode, minecraft != null && minecraft.level != null);
    }

    // Widgets are drawn after their screen's background, so they share its size and mouse, with the current theme.
    private static ConfigEffect.Context withTheme(ConfigEffect.Context context) {
        ConfigTheme theme = ConfigStyle.theme();
        ConfigTheme.Mode mode = ConfigStyle.mode();
        return new ConfigEffect.Context(context.width(), context.height(), context.mouseX(), context.mouseY(),
                (System.nanoTime() - START) / 1_000_000_000F, context.partialTick(), theme, theme.colors(mode), mode, context.inWorld());
    }
}
