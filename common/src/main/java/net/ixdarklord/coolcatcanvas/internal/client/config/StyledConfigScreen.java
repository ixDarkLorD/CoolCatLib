package net.ixdarklord.coolcatcanvas.internal.client.config;

import com.google.gson.JsonObject;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.ConfigIcons;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.ConfigStyle;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.ConfirmPopup;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.DropdownScreen;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.FlatButton;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.StyledEditBox;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.StyledScreen;
import net.ixdarklord.coolcatcore.api.config.Config;
import net.ixdarklord.coolcatcore.api.config.ConfigGroup;
import net.ixdarklord.coolcatcore.api.config.ConfigNode;
import net.ixdarklord.coolcatcore.api.config.ConfigScope;
import net.ixdarklord.coolcatcore.api.config.ConfigTheme;
import net.ixdarklord.coolcatcore.api.config.ConfigValue;
import net.ixdarklord.coolcatcore.api.config.RestartRequirement;
import net.ixdarklord.coolcatcore.api.config.type.ColorType;
import net.ixdarklord.coolcatcore.api.config.type.ConfigTypes;
import net.ixdarklord.coolcatcore.api.config.type.EnumType;
import net.ixdarklord.coolcatcore.api.config.type.NumberType;
import net.ixdarklord.coolcatcore.api.config.type.ValidationResult;
import net.ixdarklord.coolcatcore.internal.config.ConfigImpl;
import net.ixdarklord.coolcatcore.internal.config.ConfigValueImpl;
import net.ixdarklord.coolcatcore.internal.config.client.ClientConfigManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// CoolCatLib's own config screen, standing in for Glazed Menu's when it isn't installed. Laid out like the screen
// effects screen: the configs' categories on the left, the selected category's settings on the right (each with its
// editor: a switch, a slider, a dropdown, a color swatch, or a text box opened in place), and a search over all of them
// in the top bar. Changes wait until Done saves them; a server config is sent to the server when playing on one.
public final class StyledConfigScreen extends StyledScreen {
    private static final int ROW = 26;
    private static final int NAV_ROW = 18;
    private static final int HEADER = 18;
    private static final int PANEL_TITLE = 18;
    private static final int TOGGLE_WIDTH = 26;
    private static final int ICON_BUTTON = 14;
    private static final int FIELD_HEIGHT = 16;

    private final @Nullable Screen parent;
    private final List<Config> configs;
    private final List<Section> sections = new ArrayList<>();
    private final Map<ConfigValue<?>, Object> pending = new LinkedHashMap<>();
    private Section selected;
    private StyledEditBox search;
    private FlatButton resetPage;
    private FlatButton undo;
    private String filter = "";
    private double navScroll;
    private double listScroll;

    // A text box open over one setting's editor, and a slider held.
    private @Nullable StyledEditBox editor;
    private @Nullable ConfigValue<?> editing;
    private @Nullable ConfigValue<?> sliding;
    private int slideX;
    private int slideWidth = 1;

    // Layout of the rows drawn last frame, for hit tests.
    private final List<NavRow> navRows = new ArrayList<>();
    private final List<EntryRow> entryRows = new ArrayList<>();

    /**
     * @param configs what the screen edits, each listed with its categories
     * @param path    the category to open at (like {@code "visuals"}), in the first config; null for the first one
     */
    public StyledConfigScreen(@Nullable Screen parent, Component title, ConfigTheme theme, List<Config> configs, @Nullable String path) {
        super(title, theme);
        this.parent = parent;
        this.configs = List.copyOf(configs);
        for (Config config : this.configs) this.addSections(config);
        this.selected = this.sections.isEmpty() ? null : this.sections.getFirst();
        if (path != null && !this.configs.isEmpty()) {
            for (Section section : this.sections) {
                if (section.config == this.configs.getFirst() && !section.general && (path.equals(section.group.path()) || path.startsWith(section.group.path() + "."))) {
                    this.selected = section;
                    break;
                }
            }
        }
    }

    // A config's categories: its top-level settings as "General", then each top-level group.
    private void addSections(Config config) {
        ConfigGroup root = config.root();
        if (root.children().stream().anyMatch(child -> child instanceof ConfigValue<?> && !child.isHidden())) {
            this.sections.add(new Section(config, root, true));
        }
        for (ConfigNode child : root.children()) {
            if (child instanceof ConfigGroup group && !group.isHidden() && group.values().anyMatch(value -> !value.isHidden())) {
                this.sections.add(new Section(config, group, false));
            }
        }
    }

    @Override
    protected @Nullable Screen parentScreen() {
        return this.parent;
    }

    // ---- Layout ----

    private int navX() {
        return this.frameLeft();
    }

    private int navWidth() {
        return Mth.clamp((this.frameWidth() - GAP) * 3 / 10, 110, 190);
    }

    private int listX() {
        return this.navX() + this.navWidth() + GAP;
    }

    private int listWidth() {
        return this.frameWidth() - this.navWidth() - GAP;
    }

    private int listTop() {
        return this.bodyTop() + PANEL_TITLE;
    }

    private int listBottom() {
        return this.bodyBottom() - 4;
    }

    @Override
    protected void init() {
        this.editor = null;
        this.editing = null;
        int barY = this.barWidgetY(this.topBarY());
        int right = this.frameLeft() + this.frameWidth() - 4;

        this.addModeToggle(right - 20);
        int searchWidth = Math.min(160, this.frameWidth() / 3);
        this.search = this.addRenderableWidget(new StyledEditBox(this.font, searchWidth, 20, Component.translatableWithFallback("coolcatlib.config.screen.search", "Search")));
        this.search.setPosition(right - 24 - searchWidth, barY);
        this.search.setPlaceholder(Component.translatableWithFallback("coolcatlib.config.screen.search.hint", "Search settings…").withStyle(ChatFormatting.GRAY));
        this.search.setValue(this.filter);
        this.search.setResponder(value -> {
            this.filter = value.toLowerCase(Locale.ROOT).trim();
            this.listScroll = 0;
        });

        int bottomY = this.barWidgetY(this.bottomBarY());
        int x = this.frameLeft() + 4;
        this.resetPage = this.addRenderableWidget(FlatButton.of(Component.translatableWithFallback("coolcatlib.config.screen.reset_page", "Reset Page"), 84, button -> this.resetShown())
                .style(FlatButton.Style.DANGER)
                .tooltip(Component.translatableWithFallback("coolcatlib.config.screen.reset_page.tooltip", "Set every setting shown back to its default")));
        this.resetPage.setPosition(x, bottomY);
        x += 88;
        this.undo = this.addRenderableWidget(FlatButton.of(Component.translatableWithFallback("coolcatlib.config.screen.undo", "Undo Changes"), 104, button -> this.pending.clear())
                .withIcon(ConfigIcons.UNDO)
                .tooltip(Component.translatableWithFallback("coolcatlib.config.screen.undo.tooltip", "Forget the changes made since opening")));
        this.undo.setPosition(x, bottomY);

        FlatButton done = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_DONE, 70, button -> {
            this.save();
            this.minecraft.setScreen(this.parent);
        }).style(FlatButton.Style.PRIMARY));
        done.setPosition(right - 70, bottomY);
        FlatButton cancel = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_CANCEL, 70, button -> this.onClose()));
        cancel.setPosition(right - 144, bottomY);
    }

    @Override
    public void onClose() {
        this.cancelEditing();
        if (this.pending.isEmpty()) {
            this.minecraft.setScreen(this.parent);
            return;
        }
        this.minecraft.setScreen(new ConfirmPopup(this, this.theme, Component.translatableWithFallback("coolcatlib.config.screen.discard.title", "Discard changes?"),
                Component.translatableWithFallback("coolcatlib.config.screen.discard", "The changes you made haven't been saved. Press Done to save them."),
                Component.translatableWithFallback("coolcatlib.config.screen.discard.yes", "Discard"), true,
                discard -> this.minecraft.setScreen(discard ? this.parent : this)));
    }

    @Override
    public boolean isPauseScreen() {
        return this.parent == null || this.parent.isPauseScreen();
    }

    // ---- Data ----

    private ClientConfigManager.Access access(Config config) {
        return ClientConfigManager.access(config);
    }

    private boolean editable(ConfigValue<?> value) {
        ClientConfigManager.Access access = this.access(value.config());
        return (access == ClientConfigManager.Access.LOCAL || access == ClientConfigManager.Access.REMOTE) && value.isActive();
    }

    /** The value as shown: the change waiting to be saved, else the one in the file. */
    private Object current(ConfigValue<?> value) {
        Object changed = this.pending.get(value);
        return changed != null ? changed : value.getStored();
    }

    private void stage(ConfigValue<?> value, Object newValue) {
        if (same(value, value.getStored(), newValue)) this.pending.remove(value);
        else this.pending.put(value, newValue);
    }

    private void resetShown() {
        for (Line line : this.lines()) {
            if (line.value != null && this.editable(line.value)) this.stage(line.value, line.value.getDefault());
        }
    }

    // Each config's changes, applied and saved here, or sent to the server for one that's edited there.
    private void save() {
        this.commitEditing();
        if (this.pending.isEmpty()) return;
        RestartRequirement restart = RestartRequirement.NONE;
        Map<Config, Map<ConfigValue<?>, Object>> byConfig = new LinkedHashMap<>();
        this.pending.forEach((value, newValue) -> byConfig.computeIfAbsent(value.config(), config -> new LinkedHashMap<>()).put(value, newValue));
        for (Map.Entry<Config, Map<ConfigValue<?>, Object>> entry : byConfig.entrySet()) {
            if (!(entry.getKey() instanceof ConfigImpl config)) continue;
            switch (this.access(config)) {
                case LOCAL -> {
                    Map<ConfigValueImpl<?>, Object> changes = new LinkedHashMap<>();
                    entry.getValue().forEach((value, newValue) -> changes.put((ConfigValueImpl<?>) value, newValue));
                    config.applyChanges(changes);
                    config.save();
                }
                case REMOTE -> {
                    JsonObject changes = new JsonObject();
                    entry.getValue().forEach((value, newValue) -> changes.add(value.path(), ((ConfigValueImpl<?>) value).encodeUnchecked(newValue)));
                    ClientConfigManager.sendUpdate(config, changes);
                }
                case READ_ONLY, UNAVAILABLE -> {
                    continue;
                }
            }
            for (ConfigValue<?> value : entry.getValue().keySet()) {
                RestartRequirement needed = value.config().scope() == ConfigScope.STARTUP ? RestartRequirement.GAME : value.restartRequirement();
                restart = restart.max(needed);
            }
        }
        this.pending.clear();
        if (restart == RestartRequirement.GAME) {
            ClientConfigManager.toast(Component.translatableWithFallback("coolcatlib.config.screen.restart.title", "Restart needed"),
                    Component.translatableWithFallback("coolcatlib.config.screen.restart.game", "Some changes apply after restarting the game"));
        } else if (restart == RestartRequirement.WORLD && this.minecraft.level != null) {
            ClientConfigManager.toast(Component.translatableWithFallback("coolcatlib.config.screen.restart.title", "Restart needed"),
                    Component.translatableWithFallback("coolcatlib.config.screen.restart.world", "Some changes apply after rejoining the world"));
        }
    }

    /** What the list shows: the selected category's settings, or every match of the search, under their groups' names. */
    private List<Line> lines() {
        List<Line> lines = new ArrayList<>();
        if (!this.filter.isEmpty()) {
            for (Config config : this.configs) {
                ConfigGroup[] lastGroup = {null};
                config.values().filter(value -> !value.isHidden() && this.matches(value)).forEach(value -> {
                    ConfigGroup group = value.parent();
                    if (group != lastGroup[0]) {
                        lines.add(new Line(null, group, 0, this.searchHeader(config, group)));
                        lastGroup[0] = group;
                    }
                    lines.add(new Line(value, null, 0, null));
                });
            }
            return lines;
        }
        if (this.selected == null) return lines;
        if (this.selected.general) {
            for (ConfigNode child : this.selected.group.children()) {
                if (child instanceof ConfigValue<?> value && !value.isHidden()) lines.add(new Line(value, null, 0, null));
            }
        } else {
            this.addGroup(lines, this.selected.group, 0);
        }
        return lines;
    }

    // A group's settings, then its subgroups under their names, each a step further in.
    private void addGroup(List<Line> lines, ConfigGroup group, int depth) {
        for (ConfigNode child : group.children()) {
            if (child instanceof ConfigValue<?> value && !value.isHidden()) lines.add(new Line(value, null, depth, null));
        }
        for (ConfigNode child : group.children()) {
            if (child instanceof ConfigGroup subgroup && !subgroup.isHidden() && subgroup.values().anyMatch(value -> !value.isHidden())) {
                lines.add(new Line(null, subgroup, depth, subgroup.displayName()));
                this.addGroup(lines, subgroup, depth + 1);
            }
        }
    }

    private Component searchHeader(Config config, @Nullable ConfigGroup group) {
        MutableComponent header = config.title().copy();
        List<Component> names = new ArrayList<>();
        for (ConfigGroup at = group; at != null && !at.isRoot(); at = at.parent()) names.addFirst(at.displayName());
        for (Component name : names) header.append(" › ").append(name);
        return header;
    }

    private boolean matches(ConfigValue<?> value) {
        if (value.displayName().getString().toLowerCase(Locale.ROOT).contains(this.filter)) return true;
        if (value.path().toLowerCase(Locale.ROOT).contains(this.filter)) return true;
        return value.comment().stream().anyMatch(line -> line.toLowerCase(Locale.ROOT).contains(this.filter));
    }

    // ---- Drawing ----

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractPanels(graphics, mouseX, mouseY, a);
        this.extractTitle(graphics, this.title, this.search.getX() - this.frameLeft() - 20, this::extractTitleIcon);
        this.undo.active = !this.pending.isEmpty();
        this.resetPage.active = this.lines().stream().anyMatch(line -> line.value != null && this.editable(line.value) && !same(line.value, this.current(line.value), line.value.getDefault()));

        int top = this.bodyTop();
        int height = this.bodyBottom() - top;
        ConfigStyle.panel(graphics, this.navX(), top, this.navWidth(), height);
        ConfigStyle.panel(graphics, this.listX(), top, this.listWidth(), height);
        this.extractNav(graphics, mouseX, mouseY);
        this.extractList(graphics, mouseX, mouseY);
    }

    // The scope's icon of the only config, when there's one.
    private boolean extractTitleIcon(GuiGraphicsExtractor graphics, int x, int y, int size) {
        if (this.configs.size() != 1) return false;
        ConfigIcons.forScope(this.configs.getFirst().scope()).draw(graphics, x, y, size, ConfigStyle.accent());
        return true;
    }

    private void extractPanelTitle(GuiGraphicsExtractor graphics, int x, int width, Component title, @Nullable Component hint, int hintColor) {
        int y = this.bodyTop() + 6;
        int hintWidth = hint == null ? 0 : Math.min(this.font.width(hint), width / 2 - 12);
        ConfigStyle.text(graphics, this.font, title.copy().withStyle(ChatFormatting.BOLD), x + 8, y, width - 20 - hintWidth, ConfigStyle.colors().text());
        if (hint != null) ConfigStyle.text(graphics, this.font, hint, x + width - 8 - hintWidth, y, hintWidth, hintColor);
    }

    private void extractNav(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = this.navX();
        int width = this.navWidth();
        this.extractPanelTitle(graphics, x, width, Component.translatableWithFallback("coolcatlib.config.screen.categories", "Categories"), null, 0);
        this.navRows.clear();

        int top = this.listTop();
        int bottom = this.listBottom();
        boolean headers = this.configs.size() > 1;
        int content = 0;
        for (Config config : this.configs) {
            if (headers) content += HEADER;
            content += (int) this.sections.stream().filter(section -> section.config == config).count() * NAV_ROW;
        }
        this.navScroll = Mth.clamp(this.navScroll, 0, Math.max(0, content - (bottom - top)));
        if (this.sections.isEmpty()) {
            ConfigStyle.centeredText(graphics, this.font, Component.translatableWithFallback("coolcatlib.config.screen.empty", "Nothing to change here"),
                    x + width / 2, top + 20, width - 16, ConfigStyle.colors().textMuted());
            return;
        }

        graphics.enableScissor(x + 1, top, x + width - 1, bottom);
        int y = top - (int) this.navScroll;
        for (Config config : this.configs) {
            if (headers) {
                if (y + HEADER >= top && y <= bottom) {
                    ConfigIcons.forScope(config.scope()).draw(graphics, x + 8, y + 4, ConfigIcons.SIZE, ConfigStyle.colors().textMuted());
                    ConfigStyle.text(graphics, this.font, config.title(), x + 8 + ConfigIcons.SIZE + 4, y + 5, width - 28 - ConfigIcons.SIZE, ConfigStyle.colors().textMuted());
                }
                y += HEADER;
            }
            for (Section section : this.sections) {
                if (section.config != config) continue;
                this.navRows.add(new NavRow(section, y));
                if (y + NAV_ROW >= top && y <= bottom) this.extractNavRow(graphics, section, x + 4, y, width - 8, mouseX, mouseY, top, bottom);
                y += NAV_ROW;
            }
        }
        graphics.disableScissor();
        this.extractScrollbar(graphics, x + width - 4, top, bottom, content, this.navScroll);
    }

    private void extractNavRow(GuiGraphicsExtractor graphics, Section section, int x, int y, int width, int mouseX, int mouseY, int top, int bottom) {
        boolean selected = this.filter.isEmpty() && section == this.selected;
        boolean hovered = mouseY >= Math.max(y, top) && mouseY < Math.min(y + NAV_ROW, bottom) && mouseX >= x && mouseX < x + width;
        if (selected) {
            ConfigStyle.rect(graphics, x, y + 1, width, NAV_ROW - 2, ConfigStyle.withAlpha(ConfigStyle.accent(), 0x30));
            ConfigStyle.rect(graphics, x, y + 4, 2, NAV_ROW - 8, ConfigStyle.accent());
        } else if (hovered) {
            ConfigStyle.rect(graphics, x, y + 1, width, NAV_ROW - 2, ConfigStyle.colors().rowHover());
        }
        long changed = this.pending.keySet().stream().filter(value -> section.contains(value)).count();
        int textWidth = width - 16 - (changed > 0 ? 8 : 0);
        ConfigStyle.text(graphics, this.font, section.name(), x + 8, y + (NAV_ROW - 8) / 2, textWidth, selected ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim());
        // A dot marks a category with unsaved changes.
        if (changed > 0) ConfigStyle.rect(graphics, x + width - 10, y + NAV_ROW / 2 - 2, 4, 4, ConfigStyle.accent());
    }

    private void extractList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = this.listX();
        int width = this.listWidth();
        Component title = !this.filter.isEmpty() ? Component.translatableWithFallback("coolcatlib.config.screen.results", "Search results")
                : this.selected != null ? this.selected.name() : Component.empty();
        Component hint = null;
        int hintColor = ConfigStyle.colors().textMuted();
        if (this.filter.isEmpty() && this.selected != null) {
            switch (this.access(this.selected.config)) {
                case READ_ONLY, UNAVAILABLE -> {
                    hint = Component.translatableWithFallback("coolcatlib.config.screen.read_only", "Read only");
                    hintColor = ConfigStyle.colors().warning();
                }
                case REMOTE -> hint = Component.translatableWithFallback("coolcatlib.config.screen.remote", "Saved on the server");
                case LOCAL -> {
                    if (this.selected.config.scope() == ConfigScope.STARTUP) hint = Component.translatableWithFallback("coolcatlib.config.screen.startup", "Applies on the next start");
                }
            }
        }
        this.extractPanelTitle(graphics, x, width, title, hint, hintColor);
        this.entryRows.clear();

        int top = this.listTop();
        int bottom = this.listBottom();
        List<Line> lines = this.lines();
        int content = lines.stream().mapToInt(line -> line.value != null ? ROW : HEADER).sum();
        this.listScroll = Mth.clamp(this.listScroll, 0, Math.max(0, content - (bottom - top)));
        boolean editorShown = false;
        if (lines.isEmpty()) {
            Component empty = this.filter.isEmpty() ? Component.translatableWithFallback("coolcatlib.config.screen.empty", "Nothing to change here")
                    : Component.translatableWithFallback("coolcatlib.config.screen.no_match", "No setting matches the search");
            ConfigStyle.centeredText(graphics, this.font, empty, x + width / 2, top + 20, width - 16, ConfigStyle.colors().textMuted());
        } else {
            graphics.enableScissor(x + 1, top, x + width - 1, bottom);
            int y = top - (int) this.listScroll;
            for (Line line : lines) {
                int indent = Math.min(line.depth, 4) * 8;
                if (line.value == null) {
                    if (y + HEADER >= top && y <= bottom) {
                        int rowX = x + 4 + indent;
                        ConfigStyle.rect(graphics, rowX, y + HEADER / 2, 3, 1, ConfigStyle.accent());
                        ConfigStyle.text(graphics, this.font, line.header, rowX + 6, y + 6, width - 16 - indent, ConfigStyle.accent());
                    }
                    y += HEADER;
                    continue;
                }
                EntryRow row = new EntryRow(line.value, x + 4 + indent, y, width - 8 - indent);
                this.entryRows.add(row);
                if (y + ROW >= top && y <= bottom) this.extractEntry(graphics, row, mouseX, mouseY, top, bottom);
                if (line.value == this.editing && this.editor != null && y >= top && y + ROW <= bottom) {
                    this.editor.setPosition(row.editorX(), y + (ROW - FIELD_HEIGHT) / 2);
                    this.editor.setWidth(row.editorWidth());
                    editorShown = true;
                }
                y += ROW;
            }
            graphics.disableScissor();
            this.extractScrollbar(graphics, x + width - 4, top, bottom, content, this.listScroll);
        }
        if (this.editor != null) this.editor.visible = editorShown;
    }

    private void extractEntry(GuiGraphicsExtractor graphics, EntryRow row, int mouseX, int mouseY, int top, int bottom) {
        ConfigValue<?> value = row.value;
        int x = row.x;
        int y = row.y;
        int width = row.width;
        boolean editable = this.editable(value);
        boolean hovered = mouseY >= Math.max(y, top) && mouseY < Math.min(y + ROW, bottom) && mouseX >= x && mouseX < x + width;
        if (hovered) ConfigStyle.rect(graphics, x, y + 1, width, ROW - 2, ConfigStyle.colors().rowHover());
        // An accent bar marks an unsaved change.
        if (this.pending.containsKey(value)) ConfigStyle.rect(graphics, x, y + 4, 2, ROW - 8, ConfigStyle.accent());

        // Name (with a restart mark) and its comment's first line, or its path.
        int textX = x + 8;
        int textWidth = row.resetX() - 4 - textX;
        int nameColor = !value.isActive() ? ConfigStyle.colors().textMuted() : ConfigStyle.colors().text();
        Component name = value.displayName();
        int nameWidth = Math.min(this.font.width(name), textWidth - (needsRestart(value) ? ConfigIcons.SIZE + 4 : 0));
        ConfigStyle.text(graphics, this.font, name, textX, y + 4, nameWidth, nameColor);
        boolean overRestart = false;
        if (needsRestart(value)) {
            int iconX = textX + nameWidth + 4;
            ConfigIcons.RESTART.draw(graphics, iconX, y + 3, ConfigStyle.colors().warning());
            overRestart = hovered && mouseX >= iconX && mouseX < iconX + ConfigIcons.SIZE && mouseY < y + 14;
        }
        Component second = value.comment().isEmpty() ? Component.literal(value.path()) : Component.literal(value.comment().getFirst());
        ConfigStyle.text(graphics, this.font, second, textX, y + 15, textWidth, ConfigStyle.colors().textMuted());

        // Back to the default.
        boolean canReset = editable && !same(value, this.current(value), value.getDefault());
        this.iconButton(graphics, ConfigIcons.RESET, row.resetX(), y, mouseX, mouseY, canReset);

        this.extractEditor(graphics, row, mouseX, mouseY, editable);

        if (!hovered || this.sliding != null) return;
        if (overRestart) {
            graphics.setTooltipForNextFrame(restartTooltip(value), mouseX, mouseY);
        } else if (canReset && inIcon(mouseX, mouseY, row.resetX(), y)) {
            graphics.setTooltipForNextFrame(Component.translatableWithFallback("coolcatlib.config.screen.reset", "Reset to %s", format(value, value.getDefault())), mouseX, mouseY);
        } else if (mouseX < row.resetX()) {
            graphics.setTooltipForNextFrame(this.font.split(this.tooltip(value), 240), mouseX, mouseY);
        }
    }

    private void extractEditor(GuiGraphicsExtractor graphics, EntryRow row, int mouseX, int mouseY, boolean editable) {
        ConfigValue<?> value = row.value;
        Object current = this.current(value);
        int x = row.editorX();
        int width = row.editorWidth();
        int y = row.y + (ROW - FIELD_HEIGHT) / 2;
        boolean hovered = editable && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + FIELD_HEIGHT;

        if (value.type() == ConfigTypes.BOOLEAN) {
            float on = Boolean.TRUE.equals(current) ? 1 : 0;
            int trackY = row.y + (ROW - 12) / 2;
            int off = hovered ? ConfigStyle.mix(ConfigStyle.colors().toggleOff(), ConfigStyle.colors().text(), 0.12F) : ConfigStyle.colors().toggleOff();
            int track = editable ? ConfigStyle.mix(off, ConfigStyle.accent(), on) : ConfigStyle.colors().buttonDisabled();
            ConfigStyle.rect(graphics, x, trackY, TOGGLE_WIDTH, 12, track);
            ConfigStyle.rect(graphics, x + 1 + Math.round((TOGGLE_WIDTH - 12) * on), trackY + 1, 10, 10, editable ? ConfigStyle.colors().knob() : ConfigStyle.colors().textMuted());
            Component label = on > 0 ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
            ConfigStyle.text(graphics, this.font, label, x + TOGGLE_WIDTH + 6, row.y + (ROW - 8) / 2, width - TOGGLE_WIDTH - 6,
                    !editable ? ConfigStyle.colors().textMuted() : on > 0 ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim());
            return;
        }
        if (value.type() instanceof NumberType<?> number && number.isSlider() && number.isBounded()) {
            int sliderWidth = row.sliderWidth();
            int sliderY = row.y + (ROW - 5) / 2;
            double progress = sliderProgress(number, current);
            ConfigStyle.rect(graphics, x, sliderY, sliderWidth, 5, ConfigStyle.colors().toggleOff());
            if (editable) ConfigStyle.rect(graphics, x, sliderY, Math.max(2, (int) Math.round(sliderWidth * progress)), 5, ConfigStyle.withAlpha(ConfigStyle.accent(), 0xD0));
            int knobX = x + (int) Math.round((sliderWidth - 4) * progress);
            ConfigStyle.rect(graphics, knobX, sliderY - 3, 4, 11, editable ? ConfigStyle.colors().knob() : ConfigStyle.colors().textMuted());
            ConfigStyle.text(graphics, this.font, Component.literal(format(value, current)), x + sliderWidth + 6, row.y + (ROW - 8) / 2, width - sliderWidth - 6,
                    editable ? ConfigStyle.colors().textDim() : ConfigStyle.colors().textMuted());
            return;
        }

        boolean open = value == this.editing && this.editor != null;
        if (open) return;
        int boxX = x;
        int boxWidth = width;
        if (value.type() instanceof ColorType) {
            int color = (Integer) current;
            ConfigStyle.rect(graphics, x, y, FIELD_HEIGHT, FIELD_HEIGHT, ConfigStyle.colors().fieldBorder());
            graphics.fill(x + 1, y + 1, x + FIELD_HEIGHT - 1, y + FIELD_HEIGHT - 1, color | 0xFF000000);
            if (editable && mouseX >= x && mouseX < x + FIELD_HEIGHT && mouseY >= y && mouseY < y + FIELD_HEIGHT) {
                ConfigStyle.outline(graphics, x, y, FIELD_HEIGHT, FIELD_HEIGHT, ConfigStyle.accent());
                graphics.setTooltipForNextFrame(Component.translatableWithFallback("coolcatlib.config.screen.pick_color", "Pick a color"), mouseX, mouseY);
            }
            boxX += FIELD_HEIGHT + 4;
            boxWidth -= FIELD_HEIGHT + 4;
            hovered = hovered && mouseX >= boxX;
        }
        ConfigStyle.rect(graphics, boxX, y, boxWidth, FIELD_HEIGHT, editable ? ConfigStyle.colors().field() : ConfigStyle.colors().buttonDisabled());
        ConfigStyle.outline(graphics, boxX, y, boxWidth, FIELD_HEIGHT, hovered ? ConfigStyle.mix(ConfigStyle.colors().fieldBorder(), ConfigStyle.accent(), 0.5F) : ConfigStyle.colors().fieldBorder());
        Component text = value.type() instanceof EnumType<?> enumType ? enumName(enumType, current) : Component.literal(format(value, current));
        boolean dropdown = value.type() instanceof EnumType<?>;
        int textWidth = boxWidth - 8 - (dropdown || !editable ? ConfigIcons.SIZE + 2 : 0);
        ConfigStyle.text(graphics, this.font, text, boxX + 4, y + (FIELD_HEIGHT - 8) / 2, textWidth, editable ? ConfigStyle.colors().text() : ConfigStyle.colors().textMuted());
        if (!editable) {
            ConfigIcons.LOCK.draw(graphics, boxX + boxWidth - ConfigIcons.SIZE - 3, y + (FIELD_HEIGHT - ConfigIcons.SIZE) / 2, ConfigStyle.colors().textMuted());
        } else if (dropdown) {
            ConfigIcons.DOWN.draw(graphics, boxX + boxWidth - ConfigIcons.SIZE - 3, y + (FIELD_HEIGHT - ConfigIcons.SIZE) / 2, hovered ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim());
        }
    }

    private Component tooltip(ConfigValue<?> value) {
        MutableComponent tooltip = value.displayName().copy().withStyle(ChatFormatting.BOLD);
        for (String line : value.comment()) tooltip.append("\n").append(Component.literal(line).withStyle(ChatFormatting.GRAY));
        for (Component line : value.type().describe()) tooltip.append("\n").append(line.copy().withStyle(ChatFormatting.DARK_GRAY));
        tooltip.append("\n").append(Component.translatableWithFallback("config.coolcatlib.info.default", "Default: %s", format(value, value.getDefault())).withStyle(ChatFormatting.DARK_GRAY));
        if (!value.isActive()) {
            tooltip.append("\n").append(Component.translatableWithFallback("coolcatlib.config.screen.inactive", "Not used with the other settings as they are").withStyle(ChatFormatting.GOLD));
        }
        return tooltip;
    }

    private static boolean needsRestart(ConfigValue<?> value) {
        return value.restartRequirement() != RestartRequirement.NONE || value.config().scope() == ConfigScope.STARTUP;
    }

    private static Component restartTooltip(ConfigValue<?> value) {
        return value.restartRequirement() == RestartRequirement.WORLD
                ? Component.translatableWithFallback("coolcatlib.config.screen.needs_world", "Applies after rejoining the world")
                : Component.translatableWithFallback("coolcatlib.config.screen.needs_restart", "Applies after restarting the game");
    }

    private void iconButton(GuiGraphicsExtractor graphics, ConfigIcons.Icon icon, int x, int rowY, int mouseX, int mouseY, boolean enabled) {
        int y = rowY + (ROW - ICON_BUTTON) / 2;
        boolean hovered = enabled && mouseX >= x && mouseX < x + ICON_BUTTON && mouseY >= y && mouseY < y + ICON_BUTTON;
        if (hovered) ConfigStyle.rect(graphics, x, y, ICON_BUTTON, ICON_BUTTON, ConfigStyle.withAlpha(ConfigStyle.colors().text(), 0x1F));
        int color = !enabled ? ConfigStyle.withAlpha(ConfigStyle.colors().textMuted(), 0x60) : hovered ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim();
        icon.drawCentered(graphics, x, y, ICON_BUTTON, ICON_BUTTON, color);
    }

    private void extractScrollbar(GuiGraphicsExtractor graphics, int x, int top, int bottom, int content, double scroll) {
        int visible = bottom - top;
        if (content <= visible) return;
        int thumb = Math.max(12, visible * visible / content);
        int thumbY = top + (int) ((visible - thumb) * (scroll / (content - visible)));
        ConfigStyle.rect(graphics, x, thumbY, 2, thumb, ConfigStyle.withAlpha(ConfigStyle.colors().text(), 0x50));
    }

    // ---- Editing ----

    private void startEditing(EntryRow row) {
        this.commitEditing();
        ConfigValue<?> value = row.value;
        StyledEditBox box = new StyledEditBox(this.font, row.editorWidth(), FIELD_HEIGHT, value.displayName());
        box.setMaxLength(32767);
        box.setValue(format(value, this.current(value)));
        box.setResponder(text -> box.setInvalid(parse(value, text).isError()));
        box.setPosition(row.editorX(), row.y + (ROW - FIELD_HEIGHT) / 2);
        this.editor = this.addRenderableWidget(box);
        this.editing = value;
        this.setFocused(box);
    }

    // Stages the text box's value when it's valid; keeps the box open (and says why) when it isn't.
    private boolean commitEditing() {
        if (this.editor == null || this.editing == null) return true;
        ValidationResult<?> result = parse(this.editing, this.editor.getValue());
        if (result.isError()) {
            result.message().ifPresent(message -> ClientConfigManager.toast(this.editing.displayName(), message));
            return false;
        }
        this.stage(this.editing, result.value());
        this.closeEditor();
        return true;
    }

    private void cancelEditing() {
        if (this.editor != null) this.closeEditor();
    }

    private void closeEditor() {
        if (this.editor != null) this.removeWidget(this.editor);
        this.editor = null;
        this.editing = null;
        this.setFocused(null);
    }

    private void clickEditor(EntryRow row, double mouseX) {
        ConfigValue<?> value = row.value;
        if (value.type() == ConfigTypes.BOOLEAN) {
            this.stage(value, !Boolean.TRUE.equals(this.current(value)));
        } else if (value.type() instanceof NumberType<?> number && number.isSlider() && number.isBounded()) {
            this.sliding = value;
            this.slideX = row.editorX();
            this.slideWidth = row.sliderWidth();
            this.slide(mouseX);
        } else if (value.type() instanceof EnumType<?> enumType) {
            this.openDropdown(row, enumType);
        } else if (value.type() instanceof ColorType colorType && mouseX < row.editorX() + FIELD_HEIGHT) {
            this.minecraft.setScreen(new ColorPickerPopup(this, this.theme, value.displayName(), (Integer) this.current(value), colorType.hasAlpha(),
                    color -> this.stage(value, colorType.validate(color).value())));
        } else {
            this.startEditing(row);
        }
    }

    private <E extends Enum<E>> void openDropdown(EntryRow row, EnumType<E> type) {
        @SuppressWarnings("unchecked") E current = (E) this.current(row.value);
        this.minecraft.setScreen(new DropdownScreen<>(this, row.editorX(), row.y + (ROW - FIELD_HEIGHT) / 2, row.editorWidth(), FIELD_HEIGHT,
                type.constants(), current, type::displayName, type::description, picked -> this.stage(row.value, picked)));
    }

    private void slide(double mouseX) {
        if (this.sliding == null || !(this.sliding.type() instanceof NumberType<?> number)) return;
        double progress = Mth.clamp((mouseX - this.slideX) / Math.max(1, this.slideWidth - 4), 0, 1);
        double min = number.min().doubleValue();
        double max = number.max().doubleValue();
        double raw = min + (max - min) * progress;
        // Fractional ranges step by a hundredth of the range, rounded to keep the numbers short.
        if (!number.isIntegral()) {
            double step = (max - min) / 100;
            double magnitude = Math.pow(10, Math.floor(Math.log10(step)));
            raw = Math.round(raw / magnitude) * magnitude;
        }
        this.stage(this.sliding, number.fromDouble(raw));
    }

    // ---- Input ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (this.editor != null && !this.editor.isMouseOver(mouseX, mouseY) && !this.commitEditing()) this.cancelEditing();
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0) return false;
        if (mouseY < this.listTop() || mouseY >= this.listBottom()) return false;

        if (mouseX >= this.navX() && mouseX < this.navX() + this.navWidth()) {
            for (NavRow row : this.navRows) {
                if (mouseY >= row.y && mouseY < row.y + NAV_ROW) {
                    this.selected = row.section;
                    this.listScroll = 0;
                    if (!this.filter.isEmpty()) this.search.setValue("");
                    return true;
                }
            }
            return false;
        }

        for (EntryRow row : this.entryRows) {
            if (mouseY < row.y || mouseY >= row.y + ROW || mouseX < row.x || mouseX >= row.x + row.width) continue;
            if (!this.editable(row.value)) return true;
            if (inIcon(mouseX, mouseY, row.resetX(), row.y)) {
                this.stage(row.value, row.value.getDefault());
            } else if (mouseX >= row.editorX()) {
                this.clickEditor(row, mouseX);
            }
            return true;
        }
        return false;
    }

    private static boolean inIcon(double mouseX, double mouseY, int x, int rowY) {
        int y = rowY + (ROW - ICON_BUTTON) / 2;
        return mouseX >= x && mouseX < x + ICON_BUTTON && mouseY >= y && mouseY < y + ICON_BUTTON;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.sliding != null) {
            this.slide(event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.sliding != null) {
            this.sliding = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < this.listX()) this.navScroll -= scrollY * NAV_ROW;
        else this.listScroll -= scrollY * ROW;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.editor != null && this.getFocused() == this.editor) {
            if (event.isEscape()) {
                this.cancelEditing();
                return true;
            }
            if (event.isConfirmation()) {
                this.commitEditing();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    // ---- Values of unknown type ----

    @SuppressWarnings("unchecked")
    private static <T> String format(ConfigValue<T> value, Object raw) {
        return value.type().format((T) raw);
    }

    @SuppressWarnings("unchecked")
    private static <T> boolean same(ConfigValue<T> value, Object first, Object second) {
        return value.type().equals((T) first, (T) second);
    }

    private static <T> ValidationResult<T> parse(ConfigValue<T> value, String text) {
        return value.type().parse(text).then(value::validate);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> Component enumName(EnumType<E> type, Object constant) {
        return type.displayName((E) constant);
    }

    private static double sliderProgress(NumberType<?> number, Object current) {
        double min = number.min().doubleValue();
        double max = number.max().doubleValue();
        return max <= min ? 0 : Mth.clamp((((Number) current).doubleValue() - min) / (max - min), 0, 1);
    }

    // ---- Layout records ----

    // A category in the left panel: a config's top-level group, or its top-level settings ("General").
    private record Section(Config config, ConfigGroup group, boolean general) {
        Component name() {
            return this.general ? Component.translatableWithFallback("coolcatlib.config.screen.general", "General") : this.group.displayName();
        }

        boolean contains(ConfigValue<?> value) {
            if (value.config() != this.config) return false;
            if (this.general) return value.parent() == this.group;
            for (ConfigGroup at = value.parent(); at != null; at = at.parent()) if (at == this.group) return true;
            return false;
        }
    }

    // A line of the right panel: a setting, or a group's name over its settings.
    private record Line(@Nullable ConfigValue<?> value, @Nullable ConfigGroup group, int depth, @Nullable Component header) {}

    private record NavRow(Section section, int y) {}

    // One setting's row as laid out, with its editor at the right.
    private record EntryRow(ConfigValue<?> value, int x, int y, int width) {
        int editorWidth() {
            return Mth.clamp(this.width * 2 / 5, 70, 150);
        }

        int editorX() {
            return this.x + this.width - this.editorWidth() - 6;
        }

        int resetX() {
            return this.editorX() - ICON_BUTTON - 4;
        }

        // Leaving room for the number after it.
        int sliderWidth() {
            return Math.max(20, this.editorWidth() - 40);
        }
    }
}
