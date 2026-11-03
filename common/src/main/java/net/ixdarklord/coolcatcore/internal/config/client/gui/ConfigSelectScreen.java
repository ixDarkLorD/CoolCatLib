package net.ixdarklord.coolcatcore.internal.config.client.gui;

import net.ixdarklord.coolcatcanvas.api.client.gui.theme.ConfigEffect;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.ConfigIcons;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.ConfigStyle;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.FlatButton;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.ModIcons;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.StyledEditBox;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.StyledScreen;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.ThemeEffects;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.ThemeResources;
import net.ixdarklord.coolcatcore.api.config.ConfigNode;
import net.ixdarklord.coolcatcore.api.config.ConfigScope;
import net.ixdarklord.coolcatcore.api.config.ConfigTheme;
import net.ixdarklord.coolcatcore.api.platform.Platform;
import net.ixdarklord.coolcatcore.internal.config.ConfigGroupImpl;
import net.ixdarklord.coolcatcore.internal.config.ConfigImpl;
import net.ixdarklord.coolcatcore.internal.config.ConfigManager;
import net.ixdarklord.coolcatcore.internal.config.ConfigNodeImpl;
import net.ixdarklord.coolcatcore.internal.config.ConfigValueImpl;
import net.ixdarklord.coolcatcore.internal.config.client.ClientConfigManager;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The main config screen: one mod's configs, or every mod's, as cards grouped by mod. Its search bar is the only one
 * that searches across configs: results are listed by config, each config a category of its matching settings, and
 * opening one shows that config filtered to it. (A config's own search only searches that config.)
 */
public final class ConfigSelectScreen extends StyledScreen {
    private static final int KEY_F = 70;
    // A taller header than the other screens, so the mod's icon is large enough to recognize.
    private static final int HEADER_HEIGHT = 46;
    private static final int HEADER_ICON = 32;
    private static final Identifier MOD_LIST_ICON = CoolCatCore.rl("textures/gui/config/mod_list.png");
    // Each config is a row, as wide as the list: this tall, or shorter (down to the smallest that fits its two lines) so
    // a mod's few configs fit without scrolling.
    private static final int ROW_HEIGHT = 32;
    private static final int ROW_MIN_HEIGHT = 26;
    private static final int MOD_HEADING_HEIGHT = 22;
    private static final int TILE_GAP = 12;
    private static final int TOOLTIP_WIDTH = 220;
    // Room above and below each row.
    private static final int TILE_ROW_PADDING = 2;
    // How quickly a hovered row lights up (the time to get about two thirds of the way).
    private static final float TILE_EASE_MILLIS = 70;

    private final @Nullable Screen parent;
    private final @Nullable String modId;
    private @Nullable Rows rows;
    private @Nullable StyledEditBox search;
    private String query = "";
    private int resultCount;
    private int resultConfigs;

    public ConfigSelectScreen(@Nullable Screen parent, @Nullable String modId) {
        super(modId == null
                ? Component.translatableWithFallback("config.coolcatlib.select.all", "Mod Configs")
                : Component.translatableWithFallback("config.coolcatlib.select.mod", "%s Configs", modName(modId)),
                modId == null ? ConfigTheme.DEFAULT : ThemeResources.resolve(modId, ConfigTheme.forMod(modId)));
        this.parent = parent;
        this.modId = modId;
    }

    @Override
    protected void init() {
        int searchWidth = Math.clamp(this.width / 3, 100, 200);
        this.search = this.addRenderableWidget(new StyledEditBox(this.font, searchWidth, 18,
                Component.translatableWithFallback("config.coolcatlib.search.all", "Search all configs")));
        this.search.setPosition(this.frameLeft() + this.frameWidth() - 8 - searchWidth, this.topBarWidgetY(18));
        this.search.setPlaceholder(Component.translatableWithFallback("config.coolcatlib.search.all.hint", "Search all configs… (Ctrl+F)"));
        this.search.setValue(this.query);
        this.search.setResponder(text -> {
            this.query = text;
            this.rebuildRows();
        });
        this.addModeToggle(this.search.getX() - 18 - 22);

        int bodyTop = this.bodyTop();
        int bodyHeight = this.bodyBottom() - bodyTop;
        this.rows = this.addRenderableWidget(new Rows(this.minecraft, this.frameWidth() - 8, bodyHeight - 8, bodyTop + 4));
        this.rows.updateSizeAndPosition(this.frameWidth() - 8, bodyHeight - 8, this.frameLeft() + 4, bodyTop + 4);
        FlatButton done = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_DONE, 80, button -> this.onClose()).style(FlatButton.Style.PRIMARY));
        done.setPosition(this.frameLeft() + this.frameWidth() - 84, this.barWidgetY(this.bottomBarY()));
        this.rebuildRows();
    }

    @Override
    protected @Nullable Screen parentScreen() {
        return this.parent;
    }

    @Override
    protected int topBarHeight() {
        return HEADER_HEIGHT;
    }

    @Override
    protected void setInitialFocus() {
        if (this.search != null && !this.query.isEmpty()) this.setInitialFocus(this.search);
        else super.setInitialFocus();
    }

    private List<ConfigImpl> configs() {
        return this.modId == null ? ConfigManager.all() : ConfigManager.forMod(this.modId);
    }

    private void rebuildRows() {
        if (this.rows == null) return;
        List<Row> entries = new ArrayList<>();
        String query = this.query.trim().toLowerCase(Locale.ROOT);
        this.resultCount = 0;
        this.resultConfigs = 0;
        if (query.isEmpty()) {
            // Each config as a row; the list of every mod heads each mod's rows with its name.
            Map<String, List<ConfigImpl>> byMod = new LinkedHashMap<>();
            for (ConfigImpl config : this.configs()) byMod.computeIfAbsent(config.modId(), id -> new ArrayList<>()).add(config);
            int width = this.rows.getRowWidth();
            int count = byMod.values().stream().mapToInt(List::size).sum();
            int headings = this.modId == null ? byMod.size() : 0;
            int fit = (this.rows.getHeight() - 8 - headings * MOD_HEADING_HEIGHT) / Math.max(1, count) - TILE_ROW_PADDING * 2;
            int height = Math.max(ROW_MIN_HEIGHT, Math.min(ROW_HEIGHT, fit));
            byMod.forEach((mod, configs) -> {
                if (this.modId == null) entries.add(new ModHeading(mod));
                for (ConfigImpl config : configs) entries.add(new TileRow(List.of(config), width, height));
            });
            // No mod has a config here (CoolCatLib has none of its own): say so.
            if (entries.isEmpty()) entries.add(new NoConfigs());
            // Centered in the list when they don't fill it (which keeps 4 pixels above its rows, but none below).
            int free = this.rows.getHeight() - entries.stream().mapToInt(Row::height).sum() - 4;
            if (free > 1) entries.addFirst(new Spacer(free / 2));
        } else {
            // Each config with matches is a category: its header, then its matching settings.
            for (ConfigImpl config : this.configs()) {
                ConfigEditSession session = new ConfigEditSession(config);
                if (session.access() == ClientConfigManager.Access.UNAVAILABLE) continue;
                List<Row> results = new ArrayList<>();
                for (ConfigValueImpl<?> value : config.allValues()) {
                    if (session.isVisible(value) && matches(config, value, query)) results.add(new Result(config, value));
                }
                if (results.isEmpty()) continue;
                entries.add(new CategoryHeader(config, results.size()));
                entries.addAll(results);
                this.resultCount += results.size();
                this.resultConfigs++;
            }
            if (entries.isEmpty()) entries.add(new NoResults());
        }
        this.rows.setRows(entries);
    }

    // Anything a player might type: the name, the key or path, the comment, a group's name, or the value itself.
    private static boolean matches(ConfigImpl config, ConfigValueImpl<?> value, String query) {
        if (value.displayName().getString().toLowerCase(Locale.ROOT).contains(query)) return true;
        if (value.path().toLowerCase(Locale.ROOT).contains(query)) return true;
        if (value.comment().stream().anyMatch(line -> line.toLowerCase(Locale.ROOT).contains(query))) return true;
        for (ConfigNode group = value.parent(); group != null && group.parent() != null; group = group.parent()) {
            if (group.displayName().getString().toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        return format(value).toLowerCase(Locale.ROOT).contains(query);
    }

    private static <T> String format(ConfigValueImpl<T> value) {
        return value.type().format(value.getStored());
    }

    @Override
    protected void repositionElements() {
        this.rebuildWidgets();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.hasControlDownWithQuirk() && event.key() == KEY_F && this.search != null) {
            this.setFocused(this.search);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractPanels(graphics, mouseX, mouseY, a);
        ConfigStyle.panel(graphics, this.frameLeft(), this.bodyTop(), this.frameWidth(), this.bodyBottom() - this.bodyTop());
        int searchLeft = this.search != null ? this.search.getX() - 18 - 24 : this.width;
        this.extractHeader(graphics, searchLeft - 8);
        if (this.search != null) {
            ConfigIcons.SEARCH.draw(graphics, this.search.getX() - 14, this.search.getY() + 4, this.search.isFocused() ? ConfigStyle.accent() : ConfigStyle.colors().textDim());
        }
        Component status = this.query.isBlank()
                ? Component.translatableWithFallback("config.coolcatlib.select.hint", "Choose a config to edit")
                : Component.translatableWithFallback("config.coolcatlib.search.results", "%s results in %s configs", this.resultCount, this.resultConfigs);
        ConfigStyle.text(graphics, this.font, status, this.frameLeft() + 10, this.bottomBarY() + (BAR_HEIGHT - 8) / 2, this.frameWidth() - 110,
                ConfigStyle.colors().textMuted());
    }

    // The mod's icon in a framed tile at the left, then its title over a line saying what's here.
    private void extractHeader(GuiGraphicsExtractor graphics, int right) {
        // The icon on its own, with nothing around it.
        int iconX = this.frameLeft() + 8;
        int iconY = this.topBarY() + (HEADER_HEIGHT - HEADER_ICON) / 2;
        if (this.modId == null) {
            // The list of every mod's configs has its own: a white glyph, drawn in the theme's accent (resource packs
            // may replace it).
            graphics.blit(RenderPipelines.GUI_TEXTURED, MOD_LIST_ICON, iconX, iconY, 0, 0, HEADER_ICON, HEADER_ICON, 64, 64, 64, 64, ConfigStyle.accent());
        } else {
            this.extractModIcon(graphics, this.modId, this.theme, iconX, iconY, HEADER_ICON);
        }
        int x = iconX + HEADER_ICON + 10;
        int width = Math.max(40, right - x);
        int textTop = this.topBarY() + (HEADER_HEIGHT - 21) / 2;
        ConfigStyle.text(graphics, this.font, this.title.copy().withStyle(ChatFormatting.BOLD), x, textTop, width, ConfigStyle.colors().text());
        ConfigStyle.text(graphics, this.font, this.subtitle(), x, textTop + 13, width, ConfigStyle.colors().textMuted());
    }

    // The mod's icon, or a question mark in its place for a mod without one.
    private void extractModIcon(GuiGraphicsExtractor graphics, String modId, ConfigTheme theme, int x, int y, int size) {
        if (ModIcons.draw(graphics, modId, theme, x, y, size)) return;
        int accent = ConfigStyle.accentOf(theme);
        ConfigStyle.rect(graphics, x, y, size, size, ConfigStyle.withAlpha(accent, 0x30));
        Component mark = Component.literal("?").withStyle(ChatFormatting.BOLD);
        // The font's glyphs are 8 pixels tall; scaled to fill about two thirds of the square.
        float scale = Math.max(1, Math.round(size * 0.66F / 8F * 2) / 2F);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x + size / 2F, y + size / 2F);
        pose.scale(scale, scale);
        graphics.text(this.font, mark, -this.font.width(mark) / 2, -4, accent, false);
        pose.popMatrix();
    }

    private Component subtitle() {
        if (this.modId == null) {
            return Component.translatableWithFallback("config.coolcatlib.select.all.count", "%s mods · %s configs",
                    ConfigManager.modIds().size(), ConfigManager.all().size());
        }
        int count = this.configs().size();
        return count == 1
                ? Component.translatableWithFallback("config.coolcatlib.select.mod.count.one", "1 config · %s", this.modId)
                : Component.translatableWithFallback("config.coolcatlib.select.mod.count", "%s configs · %s", count, this.modId);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    private static String modName(String modId) {
        return Platform.getModName(modId).orElseGet(() -> ConfigNodeImpl.prettify(modId));
    }

    private static ConfigTheme themeOf(ConfigImpl config) {
        return ThemeResources.resolve(config.modId(), config.theme());
    }

    private final class Rows extends ContainerObjectSelectionList<Row> {
        Rows(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, 36);
        }

        @Override
        public int getRowWidth() {
            return Math.min(this.width - 14, 460);
        }

        void setRows(List<Row> rows) {
            this.clearEntries();
            for (Row row : rows) this.addEntry(row, row.height());
            this.setScrollAmount(0);
        }

        @Override
        protected void extractListBackground(GuiGraphicsExtractor graphics) {}

        @Override
        protected void extractListSeparators(GuiGraphicsExtractor graphics) {}

        @Override
        protected void extractScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            if (!this.scrollable()) return;
            ConfigStyle.rect(graphics, this.scrollBarX(), this.scrollBarY(), 3, this.scrollerHeight(), ConfigStyle.withAlpha(ConfigStyle.accent(), 0xB0));
        }
    }

    private abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
        abstract int height();

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    // A mod's name above its configs, with its icon.
    private final class ModHeading extends Row {
        private final String modId;
        private final ConfigTheme theme;

        ModHeading(String modId) {
            this.modId = modId;
            this.theme = ThemeResources.resolve(modId, ConfigTheme.forMod(modId));
        }

        @Override
        int height() {
            return MOD_HEADING_HEIGHT;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = ConfigSelectScreen.this.font;
            int x = this.getX();
            int y = this.getY() + this.getHeight() - 13;
            int textX = x + 2;
            ConfigSelectScreen.this.extractModIcon(graphics, this.modId, this.theme, x + 2, y - 2, 12);
            textX += 16;
            ConfigStyle.text(graphics, font, Component.literal(modName(this.modId)).withStyle(ChatFormatting.BOLD), textX, y,
                    this.getWidth() - textX + x - 4, ConfigStyle.accentOf(this.theme));
        }
    }

    // A config's row in the list.
    private final class TileRow extends Row {
        private final List<Tile> tiles = new ArrayList<>();
        private final int cardWidth;
        private final int cardHeight;

        TileRow(List<ConfigImpl> configs, int cardWidth, int cardHeight) {
            for (ConfigImpl config : configs) this.tiles.add(new Tile(config));
            this.cardWidth = cardWidth;
            this.cardHeight = cardHeight;
        }

        @Override
        int height() {
            // Room around the cards for a hovered one to grow into.
            return this.cardHeight + TILE_ROW_PADDING * 2;
        }

        private void layout() {
            int count = this.tiles.size();
            int x = this.getX() + (this.getWidth() - (this.cardWidth * count + TILE_GAP * (count - 1))) / 2;
            int y = this.getY() + (this.getHeight() - this.cardHeight) / 2;
            for (Tile tile : this.tiles) {
                tile.x = x;
                tile.y = y;
                tile.width = this.cardWidth;
                tile.height = this.cardHeight;
                x += this.cardWidth + TILE_GAP;
            }
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            this.layout();
            // The hovered tile is drawn last, so it grows over its neighbors.
            Tile top = null;
            for (Tile tile : this.tiles) {
                tile.animate(hovered && tile.contains(mouseX, mouseY));
                if (tile.grow > 0.001F && (top == null || tile.grow > top.grow)) top = tile;
            }
            for (Tile tile : this.tiles) if (tile != top) tile.extract(graphics);
            if (top != null) {
                top.extract(graphics);
                if (top.grow > 0.3F && top.contains(mouseX, mouseY)) {
                    graphics.setTooltipForNextFrame(ConfigSelectScreen.this.font, top.tooltip(), mouseX, mouseY);
                }
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            this.layout();
            for (Tile tile : this.tiles) {
                if (tile.contains(event.x(), event.y())) {
                    ConfigSelectScreen.this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    ConfigSelectScreen.this.minecraft.gui.setScreen(ConfigScreen.create(ConfigSelectScreen.this, tile.config));
                    return true;
                }
            }
            return false;
        }
    }

    // Empty room above a row, centering it in the list.
    private static final class Spacer extends Row {
        private final int height;

        Spacer(int height) {
            this.height = height;
        }

        @Override
        int height() {
            return this.height;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {}
    }

    // One config as a row: its scope's icon, its short name over what its scope means, and how many settings it has; it
    // lights up while hovered, and its tooltip says more.
    private final class Tile {
        private final ConfigImpl config;
        private final ConfigTheme theme;
        private final Component label;
        private final int settings;
        private final int accent;
        int x;
        int y;
        int width;
        int height;
        // 0 at rest, 1 fully hovered, eased between.
        float grow;
        private long lastFrame;

        Tile(ConfigImpl config) {
            this.config = config;
            this.theme = themeOf(config);
            this.label = ConfigNames.name(config);
            // Its own theme's accent if it has one, otherwise its kind's color.
            this.accent = config.hasOwnTheme() ? ConfigStyle.accentOf(this.theme) : scopeColor(config.scope());
            int settings = 0;
            for (ConfigValueImpl<?> ignored : config.allValues()) settings++;
            this.settings = settings;
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
        }

        // What the config is: its title, what its scope means, what's in it, where it's stored, and anything to know
        // before opening it (a world's config, the server's values, or changes waiting for a restart).
        List<FormattedCharSequence> tooltip() {
            Font font = ConfigSelectScreen.this.font;
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.add(ConfigNames.withMod(this.config).withStyle(ChatFormatting.BOLD).withColor(this.accent & 0xFFFFFF).getVisualOrderText());
            lines.addAll(font.split(scopeDescription(this.config).copy().withStyle(ChatFormatting.GRAY), TOOLTIP_WIDTH));
            int settings = 0;
            for (ConfigValueImpl<?> ignored : this.config.allValues()) settings++;
            // The screen's tabs: each group, and "General" for settings outside any group.
            long categories = this.config.root().children().stream().filter(node -> node instanceof ConfigGroupImpl group && !group.isHidden()).count()
                    + (this.config.root().children().stream().anyMatch(node -> node instanceof ConfigValueImpl<?>) ? 1 : 0);
            lines.add(FormattedCharSequence.EMPTY);
            lines.add(Component.translatableWithFallback("config.coolcatlib.tile.contents", "%s settings in %s categories", settings, categories)
                    .withStyle(ChatFormatting.WHITE).getVisualOrderText());
            lines.add(Component.translatableWithFallback("config.coolcatlib.tile.file", "File: %s", this.config.fileName())
                    .withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
            Component note = switch (ClientConfigManager.access(this.config)) {
                case UNAVAILABLE -> Component.translatableWithFallback("config.coolcatlib.access.unavailable",
                        "This config belongs to a world; open it while playing").withStyle(ChatFormatting.RED);
                case READ_ONLY -> Component.translatableWithFallback("config.coolcatlib.tile.read_only",
                        "The server's values; you can view them but not change them").withStyle(ChatFormatting.GOLD);
                case REMOTE -> Component.translatableWithFallback("config.coolcatlib.tile.remote",
                        "You're editing the server's values").withStyle(ChatFormatting.GOLD);
                case LOCAL -> null;
            };
            if (note != null) lines.addAll(font.split(note, TOOLTIP_WIDTH));
            if (this.config.isRestartPending()) {
                lines.add(Component.translatableWithFallback("config.coolcatlib.tile.restart", "Some changes apply after a restart")
                        .withStyle(ChatFormatting.GOLD).getVisualOrderText());
            }
            return lines;
        }

        void animate(boolean hovered) {
            long now = System.nanoTime();
            float elapsed = this.lastFrame == 0 ? 0 : (now - this.lastFrame) / 1_000_000F;
            this.lastFrame = now;
            float target = hovered ? 1 : 0;
            this.grow = target + (this.grow - target) * (float) Math.exp(-Math.min(elapsed, 200) / TILE_EASE_MILLIS);
            if (Math.abs(this.grow - target) < 0.002F) this.grow = target;
        }

        void extract(GuiGraphicsExtractor graphics) {
            Font font = ConfigSelectScreen.this.font;
            boolean unavailable = ClientConfigManager.access(this.config) == ClientConfigManager.Access.UNAVAILABLE;
            int accent = this.accent;
            float eased = this.grow * this.grow * (3 - 2 * this.grow);
            int x = this.x;
            int y = this.y;
            int width = this.width;
            int height = this.height;
            int muted = ConfigStyle.colors().textMuted();

            // The row, faintly in its color and lighter while hovered, its border fading to the color, and the color down
            // its left edge.
            int fill = blend(blend(ConfigStyle.colors().button(), ConfigStyle.colors().buttonHover(), eased), accent, 0.08F + 0.06F * eased);
            ConfigStyle.rect(graphics, x, y, width, height, fill);
            ConfigStyle.outline(graphics, x, y, width, height, blend(ConfigStyle.colors().panelBorder(), accent, 0.25F + 0.75F * eased));
            graphics.fill(x + 1, y + 1, x + 4, y + height - 1, ConfigStyle.withAlpha(accent, Math.round(0xD0 + 0x2F * eased)));

            // The scope's icon, then the name over what the scope means; the settings count (and a lock) on the right.
            int iconSize = 16;
            int iconX = x + 10;
            int iconColor = unavailable ? ConfigStyle.colors().textDim() : accent;
            ConfigIcons.forScope(this.config.scope()).draw(graphics, iconX, y + (height - iconSize) / 2, iconSize, iconColor);
            int right = x + width - 10;
            if (unavailable) {
                ConfigIcons.LOCK.draw(graphics, right - ConfigIcons.SIZE, y + (height - ConfigIcons.SIZE) / 2, muted);
                right -= ConfigIcons.SIZE + 8;
            }
            Component count = Component.translatableWithFallback("config.coolcatlib.row.settings", "%s settings", this.settings);
            int countWidth = font.width(count);
            ConfigStyle.text(graphics, font, count, right - countWidth, y + (height - 8) / 2, countWidth, muted);
            int textX = iconX + iconSize + 8;
            int textWidth = right - countWidth - 12 - textX;
            Component label = this.label.copy().withStyle(ChatFormatting.BOLD);
            ConfigStyle.text(graphics, font, label, textX, y + height / 2 - 10, textWidth, unavailable ? ConfigStyle.colors().textDim() : ConfigStyle.colors().text());
            ConfigStyle.text(graphics, font, scopeDescription(this.config), textX, y + height / 2 + 2, textWidth, muted);
            ThemeEffects.widget(graphics, ConfigEffect.WidgetKind.CARD, x, y, width, height, this.grow > 0.5F, false, !unavailable);
        }
    }

    // Each kind of config's color in the list, readable on the light and the dark panels.
    private static int scopeColor(ConfigScope scope) {
        return switch (scope) {
            case CLIENT -> 0xFF4F9BE8;
            case COMMON -> 0xFF3FAE8C;
            case SERVER -> 0xFF9B7BEA;
            case STARTUP -> 0xFFE8893F;
        };
    }

    private static Component scopeDescription(ConfigImpl config) {
        return switch (config.scope()) {
            case CLIENT -> Component.translatableWithFallback("config.coolcatlib.scope.client", "Client settings, kept on this computer");
            case COMMON -> Component.translatableWithFallback("config.coolcatlib.scope.common", "Settings for both the game and servers");
            case SERVER -> Component.translatableWithFallback("config.coolcatlib.scope.server", "Server settings, sent to players");
            case STARTUP -> Component.translatableWithFallback("config.coolcatlib.scope.startup", "Settings read when the game starts; changes need a restart");
        };
    }

    private static int blend(int from, int to, float t) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int a = from >>> shift & 0xFF;
            int b = to >>> shift & 0xFF;
            result |= Math.round(a + (b - a) * t) << shift;
        }
        return result;
    }

    // A config as a search category: its icon, title, mod and how many settings matched.
    private final class CategoryHeader extends Row {
        private final ConfigImpl config;
        private final ConfigTheme theme;
        private final int count;

        CategoryHeader(ConfigImpl config, int count) {
            this.config = config;
            this.theme = themeOf(config);
            this.count = count;
        }

        @Override
        int height() {
            return 26;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = ConfigSelectScreen.this.font;
            int x = this.getX();
            int y = this.getY() + this.getHeight() - 16;
            int accent = ConfigStyle.accentOf(this.theme);
            ConfigIcons.forScope(this.config.scope()).draw(graphics, x + 2, y - 1, 12, accent);
            MutableComponent title = ConfigNames.name(this.config).copy().withStyle(ChatFormatting.BOLD);
            if (ConfigSelectScreen.this.modId == null) {
                title = Component.literal(modName(this.config.modId()) + " › ").withStyle(ChatFormatting.GRAY).append(ConfigNames.name(this.config).copy().withStyle(ChatFormatting.BOLD));
            }
            Component count = Component.translatableWithFallback("config.coolcatlib.search.count", "%s found", this.count);
            int countWidth = font.width(count);
            ConfigStyle.text(graphics, font, title, x + 18, y + 1, this.getWidth() - countWidth - 30, accent);
            graphics.text(font, count, x + this.getWidth() - countWidth - 4, y + 1, ConfigStyle.colors().textMuted(), false);
            graphics.fill(x + 2, y + 13, x + this.getWidth() - 2, y + 14, ConfigStyle.withAlpha(accent, 0x50));
        }
    }

    // A matching setting: its name, where it sits, and its value; opening it shows its config filtered to it.
    private final class Result extends Row {
        private final ConfigImpl config;
        private final ConfigValueImpl<?> value;
        private final ConfigTheme theme;
        private final Component location;

        Result(ConfigImpl config, ConfigValueImpl<?> value) {
            this.config = config;
            this.value = value;
            this.theme = themeOf(config);
            MutableComponent location = Component.empty();
            List<Component> groups = new ArrayList<>();
            for (ConfigNode group = value.parent(); group != null && group.parent() != null; group = group.parent()) groups.addFirst(group.displayName());
            for (int i = 0; i < groups.size(); i++) {
                if (i > 0) location.append(" › ");
                location.append(groups.get(i));
            }
            if (groups.isEmpty()) {
                location.append(value.comment().isEmpty()
                        ? Component.translatableWithFallback("config.coolcatlib.tab.general", "General")
                        : Component.literal(value.comment().getFirst()));
            }
            this.location = location;
        }

        @Override
        int height() {
            return 30;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = ConfigSelectScreen.this.font;
            int x = this.getX() + 8;
            int y = this.getY() + 1;
            int width = this.getWidth() - 8;
            int height = this.getHeight() - 2;
            int accent = ConfigStyle.accentOf(this.theme);
            if (hovered) {
                ConfigStyle.rect(graphics, x, y, width, height, ConfigStyle.colors().rowHover());
                ConfigStyle.rect(graphics, x, y + 4, 2, height - 8, accent);
            }
            Component value = Component.literal(format(this.value));
            int valueWidth = Math.min(font.width(value), width / 3);
            int textWidth = width - valueWidth - 40;
            ConfigStyle.text(graphics, font, this.value.displayName(), x + 10, y + 5, textWidth, ConfigStyle.colors().text());
            ConfigStyle.text(graphics, font, this.location, x + 10, y + 17, textWidth, ConfigStyle.colors().textDim());
            ConfigStyle.text(graphics, font, value, x + width - valueWidth - 22, y + (height - 8) / 2, valueWidth, hovered ? accent : ConfigStyle.colors().textDim());
            ConfigIcons.CHEVRON.draw(graphics, x + width - 14, y + (height - ConfigIcons.SIZE) / 2, hovered ? accent : ConfigStyle.colors().textMuted());
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            ConfigSelectScreen.this.minecraft.gui.setScreen(ConfigScreen.create(ConfigSelectScreen.this, this.config, this.value.path()));
            return true;
        }
    }

    // In place of the list when no mod has a config here.
    private final class NoConfigs extends Row {
        @Override
        int height() {
            return 30;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = ConfigSelectScreen.this.font;
            int center = this.getX() + this.getWidth() / 2;
            Component title = Component.translatableWithFallback("config.coolcatlib.no_configs", "No configs here yet");
            Component hint = Component.translatableWithFallback("config.coolcatlib.no_configs.hint", "Mods that use CoolCatLib's configs show them here");
            graphics.text(font, title, center - font.width(title) / 2, this.getY() + 4, ConfigStyle.colors().text(), false);
            graphics.text(font, hint, center - font.width(hint) / 2, this.getY() + 18, ConfigStyle.colors().textMuted(), false);
        }
    }

    private final class NoResults extends Row {
        @Override
        int height() {
            return 30;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = ConfigSelectScreen.this.font;
            int y = this.getY() + (this.getHeight() - 8) / 2;
            Component text = Component.translatableWithFallback("config.coolcatlib.no_results", "No settings match");
            int textWidth = font.width(text) + 14;
            int x = this.getX() + (this.getWidth() - textWidth) / 2;
            ConfigIcons.SEARCH.draw(graphics, x, y - 1, ConfigStyle.colors().textMuted());
            graphics.text(font, text, x + 14, y, ConfigStyle.colors().textMuted(), false);
        }
    }
}
