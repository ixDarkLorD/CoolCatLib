package net.ixdarklord.coolcatcanvas.internal.client.config;

import net.ixdarklord.coolcatcanvas.internal.client.gui.style.ConfigIcons;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.ConfigStyle;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.ConfirmPopup;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.FlatButton;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.StyledPopup;
import net.ixdarklord.coolcatcore.api.config.Config;
import net.ixdarklord.coolcatcore.api.config.ConfigGroup;
import net.ixdarklord.coolcatcore.api.config.ConfigNode;
import net.ixdarklord.coolcatcore.api.config.ConfigTheme;
import net.ixdarklord.coolcatcore.api.config.ConfigValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One category of a config in a small window: that group's settings (its nested groups as sections) with Save and
 * Cancel, floating over the screen that opened it, like Glazed Menu's category popup. Switches for booleans and
 * selectors for enums; other values are shown and left to the full config screen or the file. A server config that
 * can't be changed from here is shown read only.
 */
final class CategoryPopup extends StyledPopup {
    private static final int WIDTH = 340;
    private static final int KEY_S = 83;

    private final CategoryEditSession session;
    private final ConfigGroup group;
    private @Nullable CategoryEntryList list;
    private @Nullable FlatButton saveButton;
    // What the Save button was last set up for: unsaved changes, and invalid values (-1 before the first update).
    private int shownModified = -1;
    private int shownErrors = -1;

    private CategoryPopup(@Nullable Screen parent, CategoryEditSession session, ConfigGroup group, ConfigTheme theme) {
        super(parent, group.parent() == null ? session.config().title() : group.displayName(), theme);
        this.session = session;
        this.group = group;
    }

    /**
     * The group at a dotted path in a config.
     *
     * @throws IllegalArgumentException when no group is at the path
     */
    static CategoryPopup create(@Nullable Screen parent, Config config, String path, ConfigTheme theme) {
        ConfigNode node = config.root();
        if (!path.isEmpty()) {
            for (String key : path.split("\\.")) {
                ConfigNode child = node instanceof ConfigGroup group ? group.child(key) : null;
                if (child == null) throw new IllegalArgumentException("Config " + config.id() + " has no category " + path);
                node = child;
            }
        }
        if (!(node instanceof ConfigGroup group)) throw new IllegalArgumentException("Config " + config.id() + "'s " + path + " isn't a category");
        return new CategoryPopup(parent, new CategoryEditSession(config), group, theme);
    }

    @Override
    protected ConfigIcons.Icon titleIcon() {
        return ConfigIcons.forScope(this.session.config().scope());
    }

    // Which config the category belongs to, when it isn't the whole config.
    @Override
    protected @Nullable Component titleNote() {
        return this.group.parent() == null ? null : this.session.config().title();
    }

    @Override
    protected void initPopup() {
        List<CategoryEntryList.Entry> entries = this.entries();
        int listHeight = entries.stream().mapToInt(CategoryEntryList.Entry::preferredHeight).sum() + 6;
        int maxList = this.height - 40 - (PADDING * 2 + TITLE_HEIGHT + FOOTER);
        this.setPanel(WIDTH, PADDING + TITLE_HEIGHT + Math.min(listHeight, Math.max(60, maxList)) + FOOTER + PADDING);

        int listTop = this.contentTop();
        int listBottom = this.footerTop();
        this.list = this.addRenderableWidget(new CategoryEntryList(this.minecraft, this.contentWidth() + 8, listBottom - listTop, listTop));
        this.list.updateSizeAndPosition(this.contentWidth() + 8, listBottom - listTop, this.contentLeft() - 4, listTop);
        this.list.setEntries(entries);

        int buttonY = this.footerButtonY();
        int right = this.contentLeft() + this.contentWidth();
        this.saveButton = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_DONE, 76, button -> this.save())
                .style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.CHECK));
        this.saveButton.setPosition(right - 76, buttonY);
        boolean editable = this.session.isEditable();
        FlatButton cancel = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_CANCEL, 64, button -> this.onClose()));
        cancel.setPosition(right - 76 - 4 - 64, buttonY);
        FlatButton reset = this.addRenderableWidget(FlatButton.icon(ConfigIcons.RESET,
                Component.translatableWithFallback("coolcatlib.config.popup.reset.tooltip", "Sets the values shown here to their defaults"), button -> {
                    this.session.resetToDefaults(this.values());
                    this.refreshValues();
                }));
        reset.setPosition(this.contentLeft(), buttonY);
        reset.active = editable;
        this.shownModified = -1;
        this.updateButtons();
    }

    // Settings, and nested groups as sections.
    private List<CategoryEntryList.Entry> entries() {
        List<CategoryEntryList.Entry> entries = new ArrayList<>();
        // From the width the panel is about to get: the rows are made before it's sized.
        int contentWidth = Math.min(WIDTH, this.width - 16) - PADDING * 2;
        int editorWidth = Math.clamp((contentWidth - 14) * 2 / 5, 90, 150);
        this.addGroup(entries, this.group, 0, editorWidth);
        return entries;
    }

    private void addGroup(List<CategoryEntryList.Entry> entries, ConfigGroup group, int depth, int editorWidth) {
        for (ConfigNode child : group.children()) {
            if (child instanceof ConfigValue<?> value && !value.isHidden()) {
                entries.add(new CategoryEntryList.ValueEntry<>(this.session, value, editorWidth));
            } else if (child instanceof ConfigGroup subgroup && !subgroup.isHidden() && subgroup.values().anyMatch(value -> !value.isHidden())) {
                entries.add(new CategoryEntryList.SectionEntry(subgroup, depth));
                this.addGroup(entries, subgroup, depth + 1, editorWidth);
            }
        }
    }

    private List<ConfigValue<?>> values() {
        return this.group.values().filter(value -> !value.isHidden()).toList();
    }

    @Override
    protected void extractPopup(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // A hairline above the buttons, and what's unsaved or invalid beside them.
        int lineY = this.footerTop() + 1;
        graphics.fill(this.contentLeft(), lineY, this.contentLeft() + this.contentWidth(), lineY + 1, ConfigStyle.colors().panelBorder());
        int errors = this.session.errors().size();
        int modified = this.session.modifiedCount();
        Component status = !this.session.isEditable() ? Component.translatableWithFallback("coolcatlib.config.popup.status.read_only", "Read only")
                : errors > 0 ? Component.translatableWithFallback("coolcatlib.config.popup.status.errors", "%s invalid", errors)
                : modified > 0 ? Component.translatableWithFallback("coolcatlib.config.popup.status.modified", "%s unsaved", modified) : null;
        if (status != null) {
            int x = this.contentLeft() + 26;
            int width = this.contentLeft() + this.contentWidth() - 76 - 4 - 64 - 8 - x;
            ConfigStyle.text(graphics, this.font, status, x, this.contentBottom() - 14, width,
                    errors > 0 ? ConfigStyle.colors().error() : modified > 0 ? ConfigStyle.colors().modified() : ConfigStyle.colors().textMuted());
        }
    }

    @Override
    public void tick() {
        this.updateButtons();
    }

    // Only when what it shows changed, so the tooltip isn't rebuilt every tick.
    private void updateButtons() {
        int modified = this.session.modifiedCount();
        int errors = this.session.errors().size();
        if (this.saveButton == null || modified == this.shownModified && errors == this.shownErrors) return;
        this.shownModified = modified;
        this.shownErrors = errors;
        this.saveButton.active = !this.session.hasErrors();
        this.saveButton.setMessage(this.session.modifiedCount() > 0
                ? Component.translatableWithFallback("coolcatlib.config.popup.save", "Save") : CommonComponents.GUI_DONE);
        this.saveButton.setTooltip(this.session.hasErrors()
                ? Tooltip.create(Component.translatableWithFallback("coolcatlib.config.popup.save.errors", "Fix the invalid values first:")
                .append("\n").append(this.session.errors().stream().map(Component::getString).collect(Collectors.joining("\n"))))
                : null);
    }

    private void refreshValues() {
        if (this.list != null) this.list.refreshValues();
        this.updateButtons();
    }

    private void save() {
        if (this.session.hasErrors()) return;
        this.minecraft.gui.setScreen(this.parent);
        this.session.save();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.hasControlDownWithQuirk() && event.key() == KEY_S) {
            if (this.saveButton != null && this.saveButton.active) this.save();
            return true;
        }
        return super.keyPressed(event);
    }

    // Unsaved changes are confirmed before they're dropped, however the popup is closed.
    @Override
    public void onClose() {
        if (this.session.modifiedCount() == 0 && !this.session.hasErrors()) {
            this.minecraft.gui.setScreen(this.parent);
            return;
        }
        this.minecraft.gui.setScreen(new ConfirmPopup(this, this.theme,
                Component.translatableWithFallback("coolcatlib.config.popup.discard.title", "Discard changes?"),
                Component.translatableWithFallback("coolcatlib.config.popup.discard.message", "%s unsaved changes will be lost.", this.session.modifiedCount()),
                Component.translatableWithFallback("coolcatlib.config.popup.discard.yes", "Discard"), true,
                discard -> this.minecraft.gui.setScreen(discard ? this.parent : this)));
    }

    // Coming back from a dropdown or the discard question.
    @Override
    public void added() {
        super.added();
        this.refreshValues();
    }
}
