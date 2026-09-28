package net.ixdarklord.coolcatcore.internal.config.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.coolcatcore.api.config.ConfigNode;
import net.ixdarklord.coolcatcore.api.config.ConfigTheme;
import net.ixdarklord.coolcatcore.api.config.client.ConfigEffect;
import net.ixdarklord.coolcatcore.api.platform.Platform;
import net.ixdarklord.coolcatcore.internal.config.ConfigGroupImpl;
import net.ixdarklord.coolcatcore.internal.config.ConfigImpl;
import net.ixdarklord.coolcatcore.internal.config.ConfigManager;
import net.ixdarklord.coolcatcore.internal.config.ConfigNodeImpl;
import net.ixdarklord.coolcatcore.internal.config.ConfigValueImpl;
import net.ixdarklord.coolcatcore.internal.config.client.ClientConfigManager;
import net.ixdarklord.coolcatcore.internal.config.client.gui.style.ConfigIcons;
import net.ixdarklord.coolcatcore.internal.config.client.gui.style.ConfigStyle;
import net.ixdarklord.coolcatcore.internal.config.client.gui.style.FlatButton;
import net.ixdarklord.coolcatcore.internal.config.client.gui.style.ModIcons;
import net.ixdarklord.coolcatcore.internal.config.client.gui.style.StyledEditBox;
import net.ixdarklord.coolcatcore.internal.config.client.gui.style.StyledScreen;
import net.ixdarklord.coolcatcore.internal.config.client.gui.style.ThemeEffects;
import net.ixdarklord.coolcatcore.internal.config.client.gui.style.ThemeResources;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.ixdarklord.coolcatcore.internal.config.client.gui.style.StyledList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

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
    private static final ResourceLocation MOD_LIST_ICON = CoolCatCore.rl("textures/gui/config/mod_list.png");
    // Cards are portrait, 1:1.3, sized to fit: as wide as a row allows up to the maximum, and never taller than the list.
    private static final int TILE_MIN_WIDTH = 84;
    private static final int TILE_MAX_WIDTH = 116;
    private static final float TILE_ASPECT = 1.3F;
    private static final int TILE_MIN_FIT_WIDTH = 70;
    private static final int TILE_GAP = 12;
    private static final int TOOLTIP_WIDTH = 220;
    // Room above and below a row of cards for a hovered one to grow into.
    private static final int TILE_ROW_PADDING = 6;
    // The artwork's opacity at rest and hovered: faint, so the card's label and icon lead.
    private static final float ART_OPACITY = 0.4F;
    private static final float ART_OPACITY_HOVERED = 0.65F;
    // The frame around a card's artwork, on all four sides.
    private static final int TILE_PADDING = 4;
    // How much a hovered tile grows, and how quickly (the time to get about two thirds of the way).
    private static final float TILE_GROW = 0.07F;
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
                ? Component.translatableWithFallback("config.coolcatcore.select.all", "Mod Configs")
                : Component.translatableWithFallback("config.coolcatcore.select.mod", "%s Configs", modName(modId)),
                modId == null ? ConfigTheme.DEFAULT : ThemeResources.resolve(modId, ConfigTheme.forMod(modId)));
        this.parent = parent;
        this.modId = modId;
    }

    @Override
    protected void init() {
        int searchWidth = Math.clamp(this.width / 3, 100, 200);
        this.search = this.addRenderableWidget(new StyledEditBox(this.font, searchWidth, 18,
                Component.translatableWithFallback("config.coolcatcore.search.all", "Search all configs")));
        this.search.setPosition(this.frameLeft() + this.frameWidth() - 8 - searchWidth, this.topBarWidgetY(18));
        this.search.setPlaceholder(Component.translatableWithFallback("config.coolcatcore.search.all.hint", "Search all configs… (Ctrl+F)"));
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
            // Each mod's configs as tiles, a few to a row; the list of every mod heads each mod's tiles with its name.
            Map<String, List<ConfigImpl>> byMod = new LinkedHashMap<>();
            for (ConfigImpl config : this.configs()) byMod.computeIfAbsent(config.modId(), id -> new ArrayList<>()).add(config);
            int rowWidth = this.rows.getRowWidth();
            int columns = Math.max(1, (rowWidth + TILE_GAP) / (TILE_MIN_WIDTH + TILE_GAP));
            int cardWidth = Math.min(TILE_MAX_WIDTH, (rowWidth - TILE_GAP * (columns - 1)) / columns);
            int cardHeight = Math.round(cardWidth * TILE_ASPECT);
            // Short enough for a row to fit whole, under its mod's name on the list of every mod.
            int maxHeight = this.rows.getHeight() - TILE_ROW_PADDING * 2 - 8 - (this.modId == null ? 22 : 0);
            if (cardHeight > maxHeight) {
                // Never so small the names don't fit; the list scrolls instead.
                cardWidth = Math.max(TILE_MIN_FIT_WIDTH, Math.round(maxHeight / TILE_ASPECT));
                cardHeight = Math.round(cardWidth * TILE_ASPECT);
            }
            int width = cardWidth;
            int height = cardHeight;
            byMod.forEach((mod, configs) -> {
                if (this.modId == null) entries.add(new ModHeading(mod));
                for (int i = 0; i < configs.size(); i += columns) {
                    entries.add(new TileRow(configs.subList(i, Math.min(configs.size(), i + columns)), width, height));
                }
            });
            int used = entries.stream().mapToInt(Row::height).sum();
            int free = this.rows.getHeight() - used - 8;
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Screen.hasControlDown() && keyCode == KEY_F && this.search != null) {
            this.setFocused(this.search);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderPanels(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        super.renderPanels(graphics, mouseX, mouseY, a);
        ConfigStyle.panel(graphics, this.frameLeft(), this.bodyTop(), this.frameWidth(), this.bodyBottom() - this.bodyTop());
        int searchLeft = this.search != null ? this.search.getX() - 18 - 24 : this.width;
        this.renderHeader(graphics, searchLeft - 8);
        if (this.search != null) {
            ConfigIcons.SEARCH.draw(graphics, this.search.getX() - 14, this.search.getY() + 4, this.search.isFocused() ? ConfigStyle.accent() : ConfigStyle.colors().textDim());
        }
        Component status = this.query.isBlank()
                ? Component.translatableWithFallback("config.coolcatcore.select.hint", "Choose a config to edit")
                : Component.translatableWithFallback("config.coolcatcore.search.results", "%s results in %s configs", this.resultCount, this.resultConfigs);
        ConfigStyle.text(graphics, this.font, status, this.frameLeft() + 10, this.bottomBarY() + (BAR_HEIGHT - 8) / 2, this.frameWidth() - 110,
                ConfigStyle.colors().textMuted());
    }

    // The mod's icon in a framed tile at the left, then its title over a line saying what's here.
    private void renderHeader(GuiGraphics graphics, int right) {
        int tile = HEADER_ICON + 4;
        int tileX = this.frameLeft() + 6;
        int tileY = this.topBarY() + (HEADER_HEIGHT - tile) / 2;
        ConfigStyle.rect(graphics, tileX, tileY, tile, tile, ConfigStyle.withAlpha(ConfigStyle.accent(), 0x26));
        ConfigStyle.outline(graphics, tileX, tileY, tile, tile, ConfigStyle.withAlpha(ConfigStyle.accent(), 0x66));
        if (this.modId == null) {
            // The list of every mod's configs has its own icon (resource packs may replace it).
            ConfigStyle.blit(graphics, MOD_LIST_ICON, tileX + 2, tileY + 2, 0, 0, HEADER_ICON, HEADER_ICON, 64, 64, 64, 64);
        } else {
            this.renderModIcon(graphics, this.modId, this.theme, tileX + 2, tileY + 2, HEADER_ICON);
        }
        int x = tileX + tile + 9;
        int width = Math.max(40, right - x);
        int textTop = this.topBarY() + (HEADER_HEIGHT - 21) / 2;
        ConfigStyle.text(graphics, this.font, this.title.copy().withStyle(ChatFormatting.BOLD), x, textTop, width, ConfigStyle.colors().text());
        ConfigStyle.text(graphics, this.font, this.subtitle(), x, textTop + 13, width, ConfigStyle.colors().textMuted());
    }

    // The mod's icon, or a question mark in its place for a mod without one.
    private void renderModIcon(GuiGraphics graphics, String modId, ConfigTheme theme, int x, int y, int size) {
        if (ModIcons.draw(graphics, modId, theme, x, y, size)) return;
        int accent = ConfigStyle.accentOf(theme);
        ConfigStyle.rect(graphics, x, y, size, size, ConfigStyle.withAlpha(accent, 0x30));
        Component mark = Component.literal("?").withStyle(ChatFormatting.BOLD);
        // The font's glyphs are 8 pixels tall; scaled to fill about two thirds of the square.
        float scale = Math.max(1, Math.round(size * 0.66F / 8F * 2) / 2F);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + size / 2F, y + size / 2F, 0);
        pose.scale(scale, scale, 1);
        graphics.drawString(this.font, mark, -this.font.width(mark) / 2, -4, accent, false);
        pose.popPose();
    }

    private Component subtitle() {
        if (this.modId == null) {
            return Component.translatableWithFallback("config.coolcatcore.select.all.count", "%s mods · %s configs",
                    ConfigManager.modIds().size(), ConfigManager.all().size());
        }
        int count = this.configs().size();
        return count == 1
                ? Component.translatableWithFallback("config.coolcatcore.select.mod.count.one", "1 config · %s", this.modId)
                : Component.translatableWithFallback("config.coolcatcore.select.mod.count", "%s configs · %s", count, this.modId);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private static String modName(String modId) {
        return Platform.getModName(modId).orElseGet(() -> ConfigNodeImpl.prettify(modId));
    }

    private static ConfigTheme themeOf(ConfigImpl config) {
        return ThemeResources.resolve(config.modId(), config.theme());
    }

    private final class Rows extends StyledList<Row> {
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
        protected void renderScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
            if (!this.scrollable()) return;
            ConfigStyle.rect(graphics, this.scrollBarX(), this.scrollBarY(), 3, this.scrollerHeight(), ConfigStyle.withAlpha(ConfigStyle.accent(), 0xB0));
        }
    }

    private abstract static class Row extends StyledList.Entry<Row> {
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
            return 22;
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = ConfigSelectScreen.this.font;
            int x = this.getX();
            int y = this.getY() + this.getHeight() - 13;
            int textX = x + 2;
            ConfigSelectScreen.this.renderModIcon(graphics, this.modId, this.theme, x + 2, y - 2, 12);
            textX += 16;
            ConfigStyle.text(graphics, font, Component.literal(modName(this.modId)).withStyle(ChatFormatting.BOLD), textX, y,
                    this.getWidth() - textX + x - 4, ConfigStyle.accentOf(this.theme));
        }
    }

    // A row of config cards, centered.
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
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float a) {
            this.layout();
            // The hovered tile is drawn last, so it grows over its neighbors.
            Tile top = null;
            for (Tile tile : this.tiles) {
                tile.animate(hovered && tile.contains(mouseX, mouseY));
                if (tile.grow > 0.001F && (top == null || tile.grow > top.grow)) top = tile;
            }
            for (Tile tile : this.tiles) if (tile != top) tile.render(graphics);
            if (top != null) {
                top.render(graphics);
                if (top.grow > 0.3F && top.contains(mouseX, mouseY)) {
                    ConfigStyle.tooltip(top.tooltip());
                }
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            this.layout();
            for (Tile tile : this.tiles) {
                if (tile.contains(mouseX, mouseY)) {
                    ConfigSelectScreen.this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    ConfigSelectScreen.this.minecraft.setScreen(ConfigScreen.create(ConfigSelectScreen.this, tile.config));
                    return true;
                }
            }
            return false;
        }
    }

    // Empty room above the tiles, centering them in the list.
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
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float a) {}
    }

    // One config as a navigation card: its artwork framed on all four sides, its scope's icon and a short name over the
    // artwork's foot; it grows and lights up while hovered.
    private final class Tile {
        private final ConfigImpl config;
        private final ConfigTheme theme;
        private final Component label;
        private final ResourceLocation artwork;
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
            this.label = Component.translatableWithFallback("config." + config.modId() + "." + config.name() + ".label",
                    ConfigNodeImpl.prettify(config.name()));
            this.artwork = artworkOf(config);
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
        }

        // What the config is: its title, what its scope means, what's in it, where it's stored, and anything to know
        // before opening it (a world's config, the server's values, or changes waiting for a restart).
        List<FormattedCharSequence> tooltip() {
            Font font = ConfigSelectScreen.this.font;
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.add(this.config.title().copy().withStyle(ChatFormatting.BOLD).withColor(ConfigStyle.accentOf(this.theme) & 0xFFFFFF).getVisualOrderText());
            lines.addAll(font.split(scopeDescription(this.config).copy().withStyle(ChatFormatting.GRAY), TOOLTIP_WIDTH));
            int settings = 0;
            for (ConfigValueImpl<?> ignored : this.config.allValues()) settings++;
            // The screen's tabs: each group, and "General" for settings outside any group.
            long categories = this.config.root().children().stream().filter(node -> node instanceof ConfigGroupImpl group && !group.isHidden()).count()
                    + (this.config.root().children().stream().anyMatch(node -> node instanceof ConfigValueImpl<?>) ? 1 : 0);
            lines.add(FormattedCharSequence.EMPTY);
            lines.add(Component.translatableWithFallback("config.coolcatcore.tile.contents", "%s settings in %s categories", settings, categories)
                    .withStyle(ChatFormatting.WHITE).getVisualOrderText());
            lines.add(Component.translatableWithFallback("config.coolcatcore.tile.file", "File: %s", this.config.fileName())
                    .withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
            Component note = switch (ClientConfigManager.access(this.config)) {
                case UNAVAILABLE -> Component.translatableWithFallback("config.coolcatcore.access.unavailable",
                        "This config belongs to a world; open it while playing").withStyle(ChatFormatting.RED);
                case READ_ONLY -> Component.translatableWithFallback("config.coolcatcore.tile.read_only",
                        "The server's values; you can view them but not change them").withStyle(ChatFormatting.GOLD);
                case REMOTE -> Component.translatableWithFallback("config.coolcatcore.tile.remote",
                        "You're editing the server's values").withStyle(ChatFormatting.GOLD);
                case LOCAL -> null;
            };
            if (note != null) lines.addAll(font.split(note, TOOLTIP_WIDTH));
            if (this.config.isRestartPending()) {
                lines.add(Component.translatableWithFallback("config.coolcatcore.tile.restart", "Some changes apply after a restart")
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

        void render(GuiGraphics graphics) {
            Font font = ConfigSelectScreen.this.font;
            boolean unavailable = ClientConfigManager.access(this.config) == ClientConfigManager.Access.UNAVAILABLE;
            int accent = ConfigStyle.accentOf(this.theme);
            float eased = this.grow * this.grow * (3 - 2 * this.grow);
            float scale = 1 + TILE_GROW * eased;
            int width = this.width;
            int height = this.height;

            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(this.x + width / 2F, this.y + height / 2F, 0);
            pose.scale(scale, scale, 1);
            pose.translate(-width / 2F, -height / 2F, 0);
            // A shadow that deepens as it lifts, the frame, and its border fading to the accent.
            int shadow = Math.round(2 + 4 * eased);
            ConfigStyle.rect(graphics, 2, shadow, width, height, ConfigStyle.withAlpha(0xFF000000, Math.round(0x38 + 0x48 * eased)));
            ConfigStyle.rect(graphics, 0, 0, width, height, blend(ConfigStyle.colors().button(), ConfigStyle.colors().buttonHover(), eased));
            ConfigStyle.outline(graphics, 0, 0, width, height, blend(ConfigStyle.colors().panelBorder(), accent, eased));

            // The artwork, inside the frame's padding; greyed out when the config can't be opened here.
            int artX = TILE_PADDING;
            int artY = TILE_PADDING;
            int artWidth = width - TILE_PADDING * 2;
            int artHeight = height - TILE_PADDING * 2;
            int[] size = ConfigStyle.textureSize(this.artwork);
            // Covers the area keeping its proportions, cropping the sides or the top and bottom.
            float cover = Math.max(artWidth / (float) size[0], artHeight / (float) size[1]);
            int regionWidth = Math.round(artWidth / cover);
            int regionHeight = Math.round(artHeight / cover);
            ConfigStyle.blit(graphics, this.artwork, artX, artY, (size[0] - regionWidth) / 2F, (size[1] - regionHeight) / 2F,
                    artWidth, artHeight, regionWidth, regionHeight, size[0], size[1],
                    ConfigStyle.withAlpha(unavailable ? 0xFF6A6A6A : 0xFFFFFFFF, Math.round(255 * (ART_OPACITY + (ART_OPACITY_HOVERED - ART_OPACITY) * eased))));
            // A dark fade over the artwork's foot, for the icon and name to sit on.
            int scrim = Math.min(artHeight, 46);
            graphics.fillGradient(artX, artY + artHeight - scrim, artX + artWidth, artY + artHeight, 0x00000000, 0xD0000000);
            graphics.fill(artX, artY, artX + artWidth, artY + 2, ConfigStyle.withAlpha(accent, Math.round(0x90 + 0x6F * eased)));

            int iconSize = 18;
            int labelY = artY + artHeight - 13;
            int iconY = labelY - iconSize - 5;
            int iconColor = unavailable ? 0xFFB0B0B0 : blend(0xFFFFFFFF, accent, 0.25F + 0.5F * eased);
            ConfigIcons.forScope(this.config.scope()).draw(graphics, (width - iconSize) / 2 + 1, iconY + 1, iconSize, 0x90000000);
            ConfigIcons.forScope(this.config.scope()).draw(graphics, (width - iconSize) / 2, iconY, iconSize, iconColor);
            if (unavailable) ConfigIcons.LOCK.draw(graphics, width - TILE_PADDING - ConfigIcons.SIZE - 4, TILE_PADDING + 5, 0xFFE0E0E0);
            Component label = this.label.copy().withStyle(ChatFormatting.BOLD);
            int textWidth = Math.min(font.width(label), artWidth - 6);
            ConfigStyle.text(graphics, font, label, (width - textWidth) / 2 + 1, labelY + 1, textWidth, 0x90000000);
            ConfigStyle.text(graphics, font, label, (width - textWidth) / 2, labelY, textWidth, unavailable ? 0xFFB0B0B0 : 0xFFFFFFFF);
            pose.popPose();
            int grownWidth = Math.round(width * scale);
            int grownHeight = Math.round(height * scale);
            ThemeEffects.widget(graphics, ConfigEffect.WidgetKind.CARD, this.x - (grownWidth - width) / 2, this.y - (grownHeight - height) / 2,
                    grownWidth, grownHeight, this.grow > 0.5F, false, !unavailable);
        }
    }

    // A config's card artwork: the mod's own for that config if it ships one
    // (assets/<modid>/textures/gui/config/cards/<config name>.png), otherwise CoolCatLib: Core's for its scope.
    private static ResourceLocation artworkOf(ConfigImpl config) {
        ResourceLocation own = ResourceLocation.fromNamespaceAndPath(config.modId(), "textures/gui/config/cards/" + config.name() + ".png");
        if (Minecraft.getInstance().getResourceManager().getResource(own).isPresent()) return own;
        return CoolCatCore.rl("textures/gui/config/cards/" + config.scope().name().toLowerCase(Locale.ROOT) + ".png");
    }

    private static Component scopeDescription(ConfigImpl config) {
        return switch (config.scope()) {
            case CLIENT -> Component.translatableWithFallback("config.coolcatcore.scope.client", "Client settings, kept on this computer");
            case COMMON -> Component.translatableWithFallback("config.coolcatcore.scope.common", "Settings for both the game and servers");
            case SERVER -> Component.translatableWithFallback("config.coolcatcore.scope.server", "Server settings, sent to players");
            case WORLD -> Component.translatableWithFallback("config.coolcatcore.scope.world", "Settings stored in each world, sent to players");
            case STARTUP -> Component.translatableWithFallback("config.coolcatcore.scope.startup", "Settings read when the game starts; changes need a restart");
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
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = ConfigSelectScreen.this.font;
            int x = this.getX();
            int y = this.getY() + this.getHeight() - 16;
            int accent = ConfigStyle.accentOf(this.theme);
            ConfigIcons.forScope(this.config.scope()).draw(graphics, x + 2, y - 1, 12, accent);
            MutableComponent title = this.config.title().copy().withStyle(ChatFormatting.BOLD);
            if (ConfigSelectScreen.this.modId == null) {
                title = Component.literal(modName(this.config.modId()) + " › ").withStyle(ChatFormatting.GRAY).append(this.config.title().copy().withStyle(ChatFormatting.BOLD));
            }
            Component count = Component.translatableWithFallback("config.coolcatcore.search.count", "%s found", this.count);
            int countWidth = font.width(count);
            ConfigStyle.text(graphics, font, title, x + 18, y + 1, this.getWidth() - countWidth - 30, accent);
            graphics.drawString(font, count, x + this.getWidth() - countWidth - 4, y + 1, ConfigStyle.colors().textMuted(), false);
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
                        ? Component.translatableWithFallback("config.coolcatcore.tab.general", "General")
                        : Component.literal(value.comment().getFirst()));
            }
            this.location = location;
        }

        @Override
        int height() {
            return 30;
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float a) {
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
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            ConfigSelectScreen.this.minecraft.setScreen(ConfigScreen.create(ConfigSelectScreen.this, this.config, this.value.path()));
            return true;
        }
    }

    private final class NoResults extends Row {
        @Override
        int height() {
            return 30;
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = ConfigSelectScreen.this.font;
            int y = this.getY() + (this.getHeight() - 8) / 2;
            Component text = Component.translatableWithFallback("config.coolcatcore.no_results", "No settings match");
            int textWidth = font.width(text) + 14;
            int x = this.getX() + (this.getWidth() - textWidth) / 2;
            ConfigIcons.SEARCH.draw(graphics, x, y - 1, ConfigStyle.colors().textMuted());
            graphics.drawString(font, text, x + 14, y, ConfigStyle.colors().textMuted(), false);
        }
    }
}
