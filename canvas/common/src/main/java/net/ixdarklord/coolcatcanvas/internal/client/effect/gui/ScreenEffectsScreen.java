package net.ixdarklord.coolcatcanvas.internal.client.effect.gui;

import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffect;
import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffectLayers;
import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffectStage;
import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffects;
import net.ixdarklord.coolcatcore.api.config.ConfigTheme;
import net.ixdarklord.coolcatcanvas.api.event.v2.client.ScreenEffectEvents;
import net.ixdarklord.coolcatcanvas.internal.client.effect.ScreenEffectImpl;
import net.ixdarklord.coolcatcanvas.internal.client.effect.ScreenEffectManager;
import net.ixdarklord.coolcatcanvas.internal.client.effect.ScreenEffectPreferences;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.ConfigIcons;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.ConfigStyle;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.FlatButton;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.StyledEditBox;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.StyledScreen;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// The effects screen: the library of selectable effects on the left (a switch each), the active ones on the right as
// layers, top drawn last, grouped by stage, with drag-and-drop, arrows and a strength slider. The game shows through
// unblurred as a live preview; "Preview" hides the panels altogether.
public final class ScreenEffectsScreen extends StyledScreen {
    private static final int ROW = 26;
    private static final int HEADER = 16;
    private static final int PANEL_TITLE = 18;
    private static final int TOGGLE_WIDTH = 26;
    private static final int ICON_BUTTON = 14;

    private final @Nullable Screen parent;
    private final List<AbstractWidget> chrome = new ArrayList<>();
    private StyledEditBox search;
    private FlatButton resetOrder;
    private String filter = "";
    private double libraryScroll;
    private double layersScroll;
    private boolean peek;

    // Drag state: a layer being moved, or a strength slider held.
    private @Nullable ScreenEffect dragging;
    private double dragY;
    private @Nullable ScreenEffect sliding;
    private int slideX;
    private int slideWidth = 1;

    // Layout of the layer rows drawn last frame, for hit tests.
    private final List<LayerRow> layerRows = new ArrayList<>();

    public ScreenEffectsScreen(@Nullable Screen parent) {
        super(Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.title", "Screen Effects"), ConfigTheme.forMod(CoolCatCore.MOD_ID));
        this.parent = parent;
    }

    // ---- Layout ----

    private int libraryX() {
        return this.frameLeft();
    }

    private int libraryWidth() {
        return (this.frameWidth() - GAP) * 11 / 20;
    }

    private int layersX() {
        return this.libraryX() + this.libraryWidth() + GAP;
    }

    private int layersWidth() {
        return this.frameWidth() - this.libraryWidth() - GAP;
    }

    private int listTop() {
        return this.bodyTop() + PANEL_TITLE;
    }

    private int listBottom() {
        return this.bodyBottom() - 4;
    }

    @Override
    protected void init() {
        this.chrome.clear();
        int barY = this.barWidgetY(this.topBarY());
        int right = this.frameLeft() + this.frameWidth() - 4;

        FlatButton mode = this.addModeToggle(right - 20);
        this.chrome.add(mode);
        int searchWidth = Math.min(160, this.frameWidth() / 3);
        this.search = this.addRenderableWidget(new StyledEditBox(this.font, searchWidth, 20, Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.search", "Search")));
        this.search.setPosition(right - 24 - searchWidth, barY);
        this.search.setPlaceholder(Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.search", "Search effects…").withStyle(ChatFormatting.GRAY));
        this.search.setValue(this.filter);
        this.search.setResponder(value -> {
            this.filter = value.toLowerCase(Locale.ROOT);
            this.libraryScroll = 0;
        });
        this.chrome.add(this.search);

        int bottomY = this.barWidgetY(this.bottomBarY());
        int x = this.frameLeft() + 4;
        FlatButton allOff = this.addRenderableWidget(FlatButton.of(Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.all_off", "Turn All Off"), 90, button -> this.turnAllOff())
                .style(FlatButton.Style.DANGER));
        allOff.setPosition(x, bottomY);
        this.chrome.add(allOff);
        x += 94;
        this.resetOrder = this.addRenderableWidget(FlatButton.of(Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.reset_order", "Reset Order"), 84, button -> {
            ScreenEffects.layers().resetOrder();
            ScreenEffectPreferences.setOrder(null);
        }).withIcon(ConfigIcons.RESET).tooltip(Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.reset_order.tooltip", "Sort the layers by the order their mods chose")));
        this.resetOrder.setPosition(x, bottomY);
        this.chrome.add(this.resetOrder);

        FlatButton done = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_DONE, 70, button -> this.onClose()).style(FlatButton.Style.PRIMARY));
        done.setPosition(right - 70, bottomY);
        this.chrome.add(done);
        FlatButton preview = this.addRenderableWidget(FlatButton.of(Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.preview", "Preview"), 70, button -> this.setPeek(true))
                .withIcon(ConfigIcons.SEARCH).tooltip(Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.preview.tooltip", "Hide this screen to see the effects; click or press Esc to come back")));
        preview.setPosition(right - 144, bottomY);
        this.chrome.add(preview);
        this.setPeek(this.peek);
    }

    @Override
    public void added() {
        super.added();
        ScreenEffectManager.setPreviewUnderGui(true);
    }

    @Override
    public void removed() {
        ScreenEffectManager.setPreviewUnderGui(false);
        ScreenEffectPreferences.save();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void setPeek(boolean peek) {
        this.peek = peek;
        for (AbstractWidget widget : this.chrome) widget.visible = !peek;
        if (peek) this.setFocused(null);
    }

    // ---- Data ----

    private List<ScreenEffect> library() {
        List<ScreenEffect> effects = new ArrayList<>();
        for (ScreenEffect effect : ScreenEffects.layers().order()) {
            if (!effect.isSelectable()) continue;
            if (!this.filter.isEmpty() && !effect.displayName().getString().toLowerCase(Locale.ROOT).contains(this.filter)
                    && !effect.id().toString().contains(this.filter)) continue;
            effects.add(effect);
        }
        effects.sort((a, b) -> a.displayName().getString().compareToIgnoreCase(b.displayName().getString()));
        return effects;
    }

    /** The layers panel's effects of one stage, top (drawn last) first. */
    private static List<ScreenEffect> layersOf(ScreenEffectStage stage) {
        List<ScreenEffect> effects = new ArrayList<>();
        for (ScreenEffect effect : ScreenEffects.layers().order()) {
            if (effect.stage() == stage && (effect.isEnabled() || effect.isVisible())) effects.addFirst(effect);
        }
        return effects;
    }

    private void toggle(ScreenEffect effect) {
        if (effect.isAutomatic() || effect.error() != null) return;
        ScreenEffectManager.runAs(ScreenEffectEvents.ToggleCause.PLAYER, effect::toggle);
        ScreenEffectPreferences.setEnabled(effect, effect.isEnabled());
    }

    private void turnAllOff() {
        for (ScreenEffect effect : ScreenEffects.all()) {
            if (!effect.isSelectable() || effect.isAutomatic() || !effect.isEnabled()) continue;
            ScreenEffectManager.runAs(ScreenEffectEvents.ToggleCause.PLAYER, effect::disable);
            ScreenEffectPreferences.setEnabled(effect, effect.isEnabled());
        }
    }

    private void saveOrder() {
        ScreenEffectLayers layers = ScreenEffects.layers();
        ScreenEffectPreferences.setOrder(layers.isCustomized() ? layers.order() : null);
    }

    // ---- Drawing ----

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ConfigStyle.use(this.theme);
        // No blur or dimming in a world: the effects preview live behind the panels.
        if (this.minecraft.level == null) ConfigStyle.background(graphics, this.width, this.height, () -> this.extractPanorama(graphics, a));
        if (this.peek) {
            Component hint = Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.peek", "Previewing: click or press Esc to come back");
            int width = this.font.width(hint) + 12;
            ConfigStyle.rect(graphics, (this.width - width) / 2, this.height - 26, width, 16, ConfigStyle.withAlpha(ConfigStyle.colors().bar(), 0xC0));
            ConfigStyle.centeredText(graphics, this.font, hint, this.width / 2, this.height - 22, width, ConfigStyle.colors().textDim());
            return;
        }
        this.extractPanels(graphics, mouseX, mouseY, a);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractPanels(graphics, mouseX, mouseY, a);
        this.extractTitle(graphics, this.title, this.search.getX() - this.frameLeft() - 20);
        this.resetOrder.active = ScreenEffects.layers().isCustomized();

        int top = this.bodyTop();
        int height = this.bodyBottom() - top;
        ConfigStyle.panel(graphics, this.libraryX(), top, this.libraryWidth(), height);
        ConfigStyle.panel(graphics, this.layersX(), top, this.layersWidth(), height);
        this.extractLibrary(graphics, mouseX, mouseY);
        this.extractLayers(graphics, mouseX, mouseY);
    }

    private void extractPanelTitle(GuiGraphicsExtractor graphics, int x, int width, Component title, Component hint) {
        int y = this.bodyTop() + 6;
        ConfigStyle.text(graphics, this.font, title.copy().withStyle(ChatFormatting.BOLD), x + 8, y, width / 2, ConfigStyle.colors().text());
        int hintWidth = Math.min(this.font.width(hint), width / 2 - 12);
        ConfigStyle.text(graphics, this.font, hint, x + width - 8 - hintWidth, y, hintWidth, ConfigStyle.colors().textMuted());
    }

    private void extractLibrary(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = this.libraryX();
        int width = this.libraryWidth();
        List<ScreenEffect> effects = this.library();
        long on = effects.stream().filter(ScreenEffect::isEnabled).count();
        this.extractPanelTitle(graphics, x, width, Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.library", "Effects"),
                Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.library.count", "%s of %s on", on, effects.size()));

        int top = this.listTop();
        int bottom = this.listBottom();
        this.libraryScroll = Mth.clamp(this.libraryScroll, 0, Math.max(0, effects.size() * ROW - (bottom - top)));
        if (effects.isEmpty()) {
            Component empty = this.filter.isEmpty()
                    ? Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.library.empty", "No screen effects are registered")
                    : Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.library.no_match", "No effect matches the search");
            ConfigStyle.centeredText(graphics, this.font, empty, x + width / 2, top + 20, width - 16, ConfigStyle.colors().textMuted());
            return;
        }

        graphics.enableScissor(x + 1, top, x + width - 1, bottom);
        int y = top - (int) this.libraryScroll;
        for (ScreenEffect effect : effects) {
            if (y + ROW >= top && y <= bottom) this.extractLibraryRow(graphics, effect, x + 4, y, width - 8, mouseX, mouseY, top, bottom);
            y += ROW;
        }
        graphics.disableScissor();
        this.extractScrollbar(graphics, x + width - 4, top, bottom, effects.size() * ROW, this.libraryScroll);
    }

    private void extractLibraryRow(GuiGraphicsExtractor graphics, ScreenEffect effect, int x, int y, int width, int mouseX, int mouseY, int top, int bottom) {
        boolean locked = effect.isAutomatic() || effect.error() != null;
        boolean hovered = mouseY >= Math.max(y, top) && mouseY < Math.min(y + ROW, bottom) && mouseX >= x && mouseX < x + width;
        if (hovered) ConfigStyle.rect(graphics, x, y + 1, width, ROW - 2, ConfigStyle.colors().rowHover());

        // The switch.
        int trackY = y + (ROW - 12) / 2;
        float on = effect.isEnabled() ? 1 : 0;
        int track = locked ? ConfigStyle.colors().buttonDisabled() : ConfigStyle.mix(ConfigStyle.colors().toggleOff(), ConfigStyle.accent(), on);
        ConfigStyle.rect(graphics, x + 4, trackY, TOGGLE_WIDTH, 12, track);
        ConfigStyle.rect(graphics, x + 5 + Math.round((TOGGLE_WIDTH - 12) * on), trackY + 1, 10, 10, locked ? ConfigStyle.colors().textMuted() : ConfigStyle.colors().knob());

        // Name and description (or id), with the stage as a badge.
        int textX = x + TOGGLE_WIDTH + 12;
        Component badge = stageBadge(effect.stage());
        int badgeWidth = this.font.width(badge) + 8;
        int textWidth = width - (textX - x) - badgeWidth - 10;
        int nameColor = effect.error() != null ? ConfigStyle.colors().error() : ConfigStyle.colors().text();
        ConfigStyle.text(graphics, this.font, effect.displayName(), textX, y + 4, textWidth, nameColor);
        Component second = effect.error() != null ? Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.error", "Failed to load")
                : effect.isAutomatic() ? Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.automatic", "Automatic: its mod turns it on and off")
                : effect.description() != null ? effect.description() : Component.literal(effect.id().toString());
        ConfigStyle.text(graphics, this.font, second, textX, y + 15, textWidth, ConfigStyle.colors().textMuted());
        ConfigStyle.badge(graphics, this.font, badge, x + width - badgeWidth - 4, y + (ROW - 11) / 2, stageColor(effect.stage()));

        if (hovered) {
            Component tooltip = effect.error() != null ? Component.literal(effect.error()).withStyle(ChatFormatting.RED) : effect.description();
            if (tooltip != null && mouseX >= textX) graphics.setTooltipForNextFrame(tooltip, mouseX, mouseY);
        }
    }

    private void extractLayers(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = this.layersX();
        int width = this.layersWidth();
        this.extractPanelTitle(graphics, x, width, Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.layers", "Layers"),
                Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.layers.hint", "Top draws last"));
        this.layerRows.clear();

        int top = this.listTop();
        int bottom = this.listBottom();
        List<ScreenEffect> screen = layersOf(ScreenEffectStage.SCREEN);
        List<ScreenEffect> world = layersOf(ScreenEffectStage.WORLD);
        int content = (screen.isEmpty() ? 0 : HEADER + screen.size() * ROW) + (world.isEmpty() ? 0 : HEADER + world.size() * ROW);
        this.layersScroll = Mth.clamp(this.layersScroll, 0, Math.max(0, content - (bottom - top)));
        if (content == 0) {
            ConfigStyle.centeredText(graphics, this.font, Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.layers.empty", "Turn effects on to stack them here"),
                    x + width / 2, top + 20, width - 16, ConfigStyle.colors().textMuted());
            return;
        }

        graphics.enableScissor(x + 1, top, x + width - 1, bottom);
        int y = top - (int) this.layersScroll;
        y = this.extractGroup(graphics, ScreenEffectStage.SCREEN, screen, x + 4, y, width - 8, mouseX, mouseY, top, bottom);
        this.extractGroup(graphics, ScreenEffectStage.WORLD, world, x + 4, y, width - 8, mouseX, mouseY, top, bottom);
        graphics.disableScissor();
        this.extractScrollbar(graphics, x + width - 4, top, bottom, content, this.layersScroll);

        if (this.minecraft.level == null && !world.isEmpty()) {
            ConfigStyle.text(graphics, this.font, Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.no_world", "World effects show while playing"),
                    x + 8, bottom - 10, width - 16, ConfigStyle.colors().warning());
        }
    }

    private int extractGroup(GuiGraphicsExtractor graphics, ScreenEffectStage stage, List<ScreenEffect> effects, int x, int y, int width, int mouseX, int mouseY, int top, int bottom) {
        if (effects.isEmpty()) return y;
        Component header = stage == ScreenEffectStage.SCREEN
                ? Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.stage.screen.header", "Over HUD and menus")
                : Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.stage.world.header", "Over the world");
        ConfigStyle.rect(graphics, x, y + HEADER / 2, 3, 1, stageColor(stage));
        ConfigStyle.text(graphics, this.font, header, x + 6, y + 4, width - 8, stageColor(stage));
        y += HEADER;

        for (int i = 0; i < effects.size(); i++) {
            ScreenEffect effect = effects.get(i);
            LayerRow row = new LayerRow(effect, stage, i, x, y, width);
            this.layerRows.add(row);
            if (y + ROW >= top && y <= bottom && effect != this.dragging) this.extractLayerRow(graphics, row, mouseX, mouseY, top, bottom, false);
            y += ROW;
        }
        // The dragged layer follows the mouse, over the others.
        if (this.dragging != null && this.dragging.stage() == stage) {
            for (LayerRow row : this.layerRows) {
                if (row.effect == this.dragging) {
                    int dropIndex = this.dropIndex(row.stage, mouseY);
                    int lineY = this.rowY(stage, dropIndex);
                    ConfigStyle.rect(graphics, x, lineY - 1, width, 2, ConfigStyle.accent());
                    this.extractLayerRow(graphics, new LayerRow(row.effect, stage, row.index, x, mouseY - (int) this.dragY, width), mouseX, mouseY, top, bottom, true);
                }
            }
        }
        return y;
    }

    private void extractLayerRow(GuiGraphicsExtractor graphics, LayerRow row, int mouseX, int mouseY, int top, int bottom, boolean lifted) {
        ScreenEffect effect = row.effect;
        int x = row.x;
        int y = row.y;
        int width = row.width;
        boolean hovered = !lifted && this.dragging == null && mouseY >= Math.max(y, top) && mouseY < Math.min(y + ROW, bottom) && mouseX >= x && mouseX < x + width;
        ConfigStyle.rect(graphics, x, y + 1, width, ROW - 2, lifted ? ConfigStyle.colors().buttonHover() : hovered ? ConfigStyle.colors().rowHover() : ConfigStyle.withAlpha(ConfigStyle.colors().button(), 0x80));
        if (lifted) ConfigStyle.outline(graphics, x, y + 1, width, ROW - 2, ConfigStyle.accent());

        // Grip: three bars.
        int gripColor = hovered && mouseX < x + 14 ? ConfigStyle.colors().text() : ConfigStyle.colors().textMuted();
        for (int i = 0; i < 3; i++) graphics.fill(x + 4, y + 9 + i * 3, x + 10, y + 10 + i * 3, gripColor);

        // First line: the name (dimmed while it fades out), then its state.
        int nameX = x + 16;
        int lineWidth = row.upX() - 4 - nameX;
        Component state = effect.isAutomatic() ? Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.auto", "Auto")
                : !effect.isEnabled() ? Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.fading", "Fading")
                : Component.literal("#" + (ScreenEffects.layers().indexOf(effect) + 1));
        int stateWidth = this.font.width(state);
        int color = effect.isEnabled() ? ConfigStyle.colors().text() : ConfigStyle.colors().textMuted();
        ConfigStyle.text(graphics, this.font, effect.displayName(), nameX, y + 3, lineWidth - stateWidth - 4, color);
        ConfigStyle.text(graphics, this.font, state, nameX + lineWidth - stateWidth, y + 3, stateWidth, ConfigStyle.colors().textMuted());

        // Second line: the strength slider (the player's strength, and the drawn one as a thin line under it).
        int sliderX = row.sliderX();
        int sliderY = y + 15;
        int sliderWidth = row.sliderWidth();
        float manual = effect instanceof ScreenEffectImpl impl ? impl.manualStrength() : effect.strength();
        ConfigStyle.rect(graphics, sliderX, sliderY, sliderWidth, 5, ConfigStyle.colors().toggleOff());
        ConfigStyle.rect(graphics, sliderX, sliderY, Math.max(2, Math.round(sliderWidth * manual)), 5, ConfigStyle.withAlpha(ConfigStyle.accent(), 0xD0));
        graphics.fill(sliderX, sliderY + 6, sliderX + Math.round(sliderWidth * effect.strength()), sliderY + 7, ConfigStyle.colors().textDim());
        Component percent = Component.literal(Math.round(manual * 100) + "%");
        ConfigStyle.text(graphics, this.font, percent, sliderX + sliderWidth + 4, sliderY - 1, 28, ConfigStyle.colors().textDim());

        // Up, down, remove.
        this.iconButton(graphics, ConfigIcons.UP, row.upX(), y, mouseX, mouseY, hovered && row.index > 0);
        this.iconButton(graphics, ConfigIcons.DOWN, row.downX(), y, mouseX, mouseY, hovered && row.index < layersOf(row.stage).size() - 1);
        this.iconButton(graphics, ConfigIcons.CLOSE, row.removeX(), y, mouseX, mouseY, hovered && !effect.isAutomatic() && effect.isEnabled());

        if (hovered && mouseX >= sliderX && mouseX < sliderX + sliderWidth && mouseY >= sliderY - 3) {
            graphics.setTooltipForNextFrame(Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.strength", "Strength: drag to change"), mouseX, mouseY);
        } else if (hovered && mouseX < x + 14) {
            graphics.setTooltipForNextFrame(Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.drag", "Drag to reorder"), mouseX, mouseY);
        }
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

    private static Component stageBadge(ScreenEffectStage stage) {
        return stage == ScreenEffectStage.SCREEN
                ? Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.stage.screen", "SCREEN")
                : Component.translatableWithFallback("screen_effect.coolcatcanvas.screen.stage.world", "WORLD");
    }

    private static int stageColor(ScreenEffectStage stage) {
        return stage == ScreenEffectStage.SCREEN ? 0xFFC78BFF : 0xFF6FC7A8;
    }

    // ---- Input ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.peek) {
            this.setPeek(false);
            return true;
        }
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0) return false;
        double mouseX = event.x();
        double mouseY = event.y();
        if (mouseY < this.listTop() || mouseY >= this.listBottom()) return false;

        // Library: a row toggles its effect.
        if (mouseX >= this.libraryX() && mouseX < this.libraryX() + this.libraryWidth()) {
            int index = (int) ((mouseY - this.listTop() + this.libraryScroll) / ROW);
            List<ScreenEffect> effects = this.library();
            if (index >= 0 && index < effects.size()) {
                this.toggle(effects.get(index));
                return true;
            }
            return false;
        }

        for (LayerRow row : this.layerRows) {
            if (mouseY < row.y || mouseY >= row.y + ROW || mouseX < row.x || mouseX >= row.x + row.width) continue;
            ScreenEffect effect = row.effect;
            if (mouseX < row.x + 14) {
                this.dragging = effect;
                this.dragY = mouseY - row.y;
            } else if (mouseX >= row.sliderX() && mouseX < row.sliderX() + row.sliderWidth() && mouseY >= row.y + 12) {
                this.sliding = effect;
                this.slideX = row.sliderX();
                this.slideWidth = row.sliderWidth();
                this.slide(mouseX);
            } else if (inIcon(mouseX, mouseY, row.upX(), row.y)) {
                ScreenEffects.layers().moveUp(effect);
                this.saveOrder();
            } else if (inIcon(mouseX, mouseY, row.downX(), row.y)) {
                ScreenEffects.layers().moveDown(effect);
                this.saveOrder();
            } else if (inIcon(mouseX, mouseY, row.removeX(), row.y) && !effect.isAutomatic() && effect.isEnabled()) {
                ScreenEffectManager.runAs(ScreenEffectEvents.ToggleCause.PLAYER, effect::disable);
                ScreenEffectPreferences.setEnabled(effect, effect.isEnabled());
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
        if (this.dragging != null) return true;
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.sliding != null) {
            ScreenEffectPreferences.setStrength(this.sliding, this.sliding instanceof ScreenEffectImpl impl ? impl.manualStrength() : 1.0F);
            this.sliding = null;
            return true;
        }
        if (this.dragging != null) {
            ScreenEffect effect = this.dragging;
            this.dragging = null;
            this.drop(effect, (int) event.y());
            return true;
        }
        return super.mouseReleased(event);
    }

    private void slide(double mouseX) {
        if (this.sliding == null) return;
        float strength = Mth.clamp((float) (mouseX - this.slideX) / this.slideWidth, 0.0F, 1.0F);
        this.sliding.setStrength(Math.round(strength * 20) / 20.0F);
    }

    /** Where in its stage's list (top first) a dragged layer would land. */
    private int dropIndex(ScreenEffectStage stage, int mouseY) {
        List<LayerRow> rows = this.layerRows.stream().filter(row -> row.stage == stage).toList();
        if (rows.isEmpty()) return 0;
        int index = (int) Math.floor((mouseY - rows.getFirst().y) / (double) ROW);
        return Mth.clamp(index, 0, rows.size() - 1);
    }

    private int rowY(ScreenEffectStage stage, int index) {
        for (LayerRow row : this.layerRows) {
            if (row.stage == stage && row.index == index) return row.y + (index > this.indexOfDragged(stage) ? ROW : 0);
        }
        return 0;
    }

    private int indexOfDragged(ScreenEffectStage stage) {
        for (LayerRow row : this.layerRows) if (row.stage == stage && row.effect == this.dragging) return row.index;
        return -1;
    }

    // Takes the slot of the layer it's dropped on, in layer terms (the list shows the top first).
    private void drop(ScreenEffect effect, int mouseY) {
        List<ScreenEffect> group = layersOf(effect.stage());
        int target = this.dropIndex(effect.stage(), mouseY);
        if (target < 0 || target >= group.size() || group.get(target) == effect) return;
        ScreenEffectLayers layers = ScreenEffects.layers();
        layers.moveTo(effect, layers.indexOf(group.get(target)));
        this.saveOrder();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.peek) return false;
        if (mouseX < this.layersX()) this.libraryScroll -= scrollY * ROW;
        else this.layersScroll -= scrollY * ROW;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.peek) {
            if (event.isEscape()) this.setPeek(false);
            return true;
        }
        return super.keyPressed(event);
    }

    // One layer row as laid out, top first within its stage.
    private record LayerRow(ScreenEffect effect, ScreenEffectStage stage, int index, int x, int y, int width) {
        int removeX() {
            return this.x + this.width - ICON_BUTTON - 2;
        }

        int downX() {
            return this.removeX() - ICON_BUTTON;
        }

        int upX() {
            return this.downX() - ICON_BUTTON;
        }

        int sliderX() {
            return this.x + 16;
        }

        // Up to the arrows, leaving room for the percentage.
        int sliderWidth() {
            return Math.max(20, this.upX() - 4 - 28 - this.sliderX());
        }
    }
}
