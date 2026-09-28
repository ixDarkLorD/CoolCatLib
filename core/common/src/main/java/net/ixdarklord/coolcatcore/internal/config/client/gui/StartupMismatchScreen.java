package net.ixdarklord.coolcatcore.internal.config.client.gui;

import net.ixdarklord.coolcatcore.api.config.ConfigTheme;
import net.ixdarklord.coolcatcore.internal.config.ConfigImpl;
import net.ixdarklord.coolcatcore.internal.config.ConfigValueImpl;
import net.ixdarklord.coolcatcore.internal.config.client.ClientConfigManager;
import net.ixdarklord.coolcatcore.internal.config.client.ClientConfigManager.StartupMismatch;
import net.ixdarklord.coolcatcore.internal.config.client.gui.style.ConfigIcons;
import net.ixdarklord.coolcatcore.internal.config.client.gui.style.ConfigStyle;
import net.ixdarklord.coolcatcore.internal.config.client.gui.style.FlatButton;
import net.ixdarklord.coolcatcore.internal.config.client.gui.style.StyledScreen;
import net.ixdarklord.coolcatcore.internal.config.client.gui.style.ThemeResources;
import org.jetbrains.annotations.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.ixdarklord.coolcatcore.internal.config.client.gui.style.StyledList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shown after a server refused this client for having different startup values. The player can adopt the server's
 * values, which are saved for the next start (startup values can't change while the game runs).
 */
public final class StartupMismatchScreen extends StyledScreen {
    private final Screen parent;
    private final List<StartupMismatch> mismatches;
    private Rows rows;
    private int statusRight;

    public StartupMismatchScreen(Screen parent, List<StartupMismatch> mismatches) {
        super(Component.translatableWithFallback("config.coolcatcore.startup.title", "Startup settings differ from the server's"),
                mismatches.isEmpty() ? ConfigTheme.DEFAULT
                        : ThemeResources.resolve(mismatches.get(0).config().modId(), mismatches.get(0).config().theme()));
        this.parent = parent;
        this.mismatches = List.copyOf(mismatches);
    }

    @Override
    protected void init() {
        int bodyTop = this.bodyTop();
        int bodyHeight = this.bodyBottom() - bodyTop;
        this.rows = this.addRenderableWidget(new Rows(this.minecraft, this.frameWidth() - 8, bodyHeight - 8, bodyTop + 4));
        this.rows.updateSizeAndPosition(this.frameWidth() - 8, bodyHeight - 8, this.frameLeft() + 4, bodyTop + 4);
        List<Row> entries = new ArrayList<>();
        for (StartupMismatch mismatch : this.mismatches) entries.add(new Row(mismatch));
        this.rows.replaceEntries(entries);

        int x = this.frameLeft() + this.frameWidth() - 4;
        int y = this.barWidgetY(this.bottomBarY());
        FlatButton quit = this.addRenderableWidget(FlatButton.of(Component.translatableWithFallback("config.coolcatcore.startup.adopt_quit", "Use Server's & Quit"), 124, button -> {
            this.adopt();
            this.minecraft.stop();
        }).style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.RESTART).tooltip(Component.translatableWithFallback("config.coolcatcore.startup.adopt_quit.tooltip",
                "Saves the server's values and closes the game; they apply when you start it again")));
        quit.setPosition(x - 124, y);
        x -= 128;
        FlatButton adopt = this.addRenderableWidget(FlatButton.of(Component.translatableWithFallback("config.coolcatcore.startup.adopt", "Use Server's"), 92, button -> {
            this.adopt();
            ClientConfigManager.toast(Component.translatableWithFallback("config.coolcatcore.toast.adopted", "Server's values saved"),
                    Component.translatableWithFallback("config.coolcatcore.toast.restart_game", "Restart the game to apply every change")
                            .withStyle(ChatFormatting.GOLD));
            this.onClose();
        }).withIcon(ConfigIcons.CHECK).tooltip(Component.translatableWithFallback("config.coolcatcore.startup.adopt.tooltip",
                "Saves the server's values; restart the game before joining")));
        adopt.setPosition(x - 92, y);
        x -= 96;
        FlatButton back = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_BACK, 60, button -> this.onClose()));
        back.setPosition(x - 60, y);
        this.statusRight = x - 68;
        this.addModeToggle(this.frameLeft() + this.frameWidth() - 24);
    }

    @Override
    protected void repositionElements() {
        this.rebuildWidgets();
    }

    @Override
    protected void renderPanels(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        super.renderPanels(graphics, mouseX, mouseY, a);
        ConfigStyle.panel(graphics, this.frameLeft(), this.bodyTop(), this.frameWidth(), this.bodyBottom() - this.bodyTop());
        this.renderTitle(graphics, this.title, this.frameWidth() - 20);
        int x = this.frameLeft() + 10;
        int y = this.bottomBarY() + (BAR_HEIGHT - 8) / 2;
        ConfigIcons.WARNING.draw(graphics, x, y - 1, ConfigStyle.colors().modified());
        ConfigStyle.text(graphics, this.font, Component.translatableWithFallback("config.coolcatcore.startup.explain",
                "Both sides must use the same values to play together."), x + 14, y, this.statusRight - x - 14, ConfigStyle.colors().textDim());
    }

    // Saved as each config's values for the next start; the running game keeps its own.
    private void adopt() {
        Map<ConfigImpl, Map<ConfigValueImpl<?>, Object>> byConfig = new LinkedHashMap<>();
        for (StartupMismatch mismatch : this.mismatches) {
            byConfig.computeIfAbsent(mismatch.config(), config -> new LinkedHashMap<>()).put(mismatch.value(), mismatch.serverValue());
        }
        byConfig.forEach((config, values) -> {
            config.applyChanges(values);
            config.save();
        });
    }

    @Override
    protected @Nullable Screen parentScreen() {
        return this.parent;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private final class Rows extends StyledList<Row> {
        Rows(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, 36);
        }

        @Override
        public int getRowWidth() {
            return Math.min(this.width - 14, 440);
        }


    }

    // A setting's name, then "yours → server's".
    private final class Row extends StyledList.Entry<Row> {
        private final Component name;
        private final Component yours;
        private final Component server;

        Row(StartupMismatch mismatch) {
            this.name = mismatch.config().title().copy().withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(" › ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(mismatch.value().displayName().copy().withStyle(ChatFormatting.WHITE));
            this.yours = Component.translatableWithFallback("config.coolcatcore.startup.yours", "Yours: %s", mismatch.format(mismatch.value().get()));
            this.server = Component.translatableWithFallback("config.coolcatcore.startup.server", "Server: %s", mismatch.format(mismatch.serverValue()));
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = StartupMismatchScreen.this.font;
            int x = this.getX();
            int y = this.getY() + 2;
            int width = this.getWidth();
            int height = this.getHeight() - 4;
            ConfigStyle.rect(graphics, x, y, width, height, ConfigStyle.colors().button());
            ConfigStyle.rect(graphics, x, y + 4, 2, height - 8, ConfigStyle.colors().modified());
            ConfigStyle.text(graphics, font, this.name, x + 10, y + 5, width - 20, ConfigStyle.colors().text());
            int half = (width - 20) / 2;
            ConfigStyle.text(graphics, font, this.yours, x + 10, y + 18, half - 16, ConfigStyle.colors().error());
            ConfigIcons.CHEVRON.draw(graphics, x + 10 + half - 12, y + 17, ConfigStyle.colors().textMuted());
            ConfigStyle.text(graphics, font, this.server, x + 10 + half, y + 18, half, ConfigStyle.colors().success());
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }
}
