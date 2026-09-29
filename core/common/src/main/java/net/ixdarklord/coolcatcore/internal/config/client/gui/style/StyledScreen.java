package net.ixdarklord.coolcatcore.internal.config.client.gui.style;

import net.ixdarklord.coolcatcore.api.config.ConfigTheme;
import net.ixdarklord.coolcatcore.internal.config.client.CoolCatCoreClientSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

// A full-screen config screen: the theme's background, a top bar (title), a body, and a bottom bar (actions).
// Subclasses place widgets with the frame's coordinates and draw static content in extractPanels.
public abstract class StyledScreen extends Screen {
    protected static final int MARGIN = 10;
    protected static final int BAR_HEIGHT = 28;
    protected static final int GAP = 6;
    protected static final int TITLE_ICON = 16;

    // Page transitions: the page's panels glide a little way into place (up from below for a new page, down from above
    // going back, and a shorter rise when opened from elsewhere) while growing from slightly smaller. The background
    // stays still.
    private static final float TRANSITION_MILLIS = 220;
    private static final int TRANSITION_SLIDE = 14;
    private static final int TRANSITION_RISE = 8;
    private static final float TRANSITION_SCALE = 0.03F;
    private static @Nullable Screen lastRemoved;

    protected final ConfigTheme theme;
    private long openedAt;
    private Transition transition = Transition.NONE;

    private enum Transition {
        NONE,
        FORWARD,
        BACK,
        RISE
    }

    protected StyledScreen(Component title, ConfigTheme theme) {
        super(title);
        this.theme = theme;
    }

    public ConfigTheme theme() {
        return this.theme;
    }

    protected int topBarY() {
        return MARGIN;
    }

    /** The top bar's height; a screen with a larger header makes it taller. */
    protected int topBarHeight() {
        return BAR_HEIGHT;
    }

    protected int bodyTop() {
        return this.topBarY() + this.topBarHeight() + GAP;
    }

    protected int bottomBarY() {
        return this.height - MARGIN - BAR_HEIGHT;
    }

    protected int bodyBottom() {
        return this.bottomBarY() - GAP;
    }

    protected int frameLeft() {
        return MARGIN;
    }

    protected int frameWidth() {
        return this.width - MARGIN * 2;
    }

    /** The y of a 20-pixel widget centered in a bar. */
    protected int barWidgetY(int barY) {
        return barY + (BAR_HEIGHT - 20) / 2;
    }

    /** The y of a widget of this height centered in the top bar. */
    protected int topBarWidgetY(int widgetHeight) {
        return this.topBarY() + (this.topBarHeight() - widgetHeight) / 2;
    }

    /** The screen this one returns to, for choosing which way it slides in. */
    protected @Nullable Screen parentScreen() {
        return null;
    }

    @Override
    public void added() {
        ConfigStyle.use(this.theme);
        Screen previous = lastRemoved;
        lastRemoved = null;
        // Leaving through a popup or dropdown (like "Discard changes?") counts as leaving the page under it.
        while (previous instanceof Overlay overlay && overlay.overlayParent() != this) previous = overlay.overlayParent();
        if (previous instanceof Overlay overlay && overlay.overlayParent() == this) {
            // Back from a popup or dropdown over this page: it never left.
            this.transition = Transition.NONE;
        } else if (previous != null && previous == this.parentScreen()) {
            this.transition = Transition.FORWARD;
        } else if (previous instanceof StyledScreen screen && screen.parentScreen() == this) {
            this.transition = Transition.BACK;
        } else {
            this.transition = Transition.RISE;
        }
        if (!CoolCatCoreClientSettings.transitions()) this.transition = Transition.NONE;
        this.openedAt = System.nanoTime();
    }

    @Override
    public void removed() {
        noteRemoved(this);
    }

    /** Remembers the screen that just closed, for the next page's transition. */
    static void noteRemoved(Screen screen) {
        lastRemoved = screen;
    }

    // From 1 as the page opens to 0 once it's in place: a smooth ease-in-out, so it neither jumps nor crawls.
    private float transitionLeft() {
        if (this.transition == Transition.NONE) return 0;
        float progress = Math.min(1, (System.nanoTime() - this.openedAt) / 1_000_000F / TRANSITION_MILLIS);
        if (progress >= 1) {
            this.transition = Transition.NONE;
            return 0;
        }
        float eased = progress * progress * progress * (progress * (progress * 6 - 15) + 10);
        return 1 - eased;
    }

    private boolean pushTransition(GuiGraphicsExtractor graphics) {
        float left = this.transitionLeft();
        if (left <= 0) return false;
        float y = switch (this.transition) {
            case FORWARD -> TRANSITION_SLIDE * left;
            case BACK -> -TRANSITION_SLIDE * left;
            default -> TRANSITION_RISE * left;
        };
        float scale = 1 - TRANSITION_SCALE * left;
        graphics.pose().pushMatrix();
        graphics.pose().translate(this.width / 2F, this.height / 2F + y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-this.width / 2F, -this.height / 2F);
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ConfigStyle.use(this.theme);
        ConfigStyle.background(graphics, this.width, this.height, () -> this.extractPanorama(graphics, a));
        ThemeEffects.background(graphics, this.width, this.height, mouseX, mouseY, a);
        boolean moved = this.pushTransition(graphics);
        this.extractPanels(graphics, mouseX, mouseY, a);
        if (moved) graphics.pose().popMatrix();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ConfigStyle.use(this.theme);
        boolean moved = this.pushTransition(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, a);
        if (moved) graphics.pose().popMatrix();
        ThemeEffects.foreground(graphics, this.width, this.height, mouseX, mouseY, a);
    }

    /** Draws the bars, panels and fixed text under the widgets. */
    protected void extractPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ConfigStyle.bar(graphics, this.frameLeft(), this.topBarY(), this.frameWidth(), this.topBarHeight());
        ConfigStyle.bar(graphics, this.frameLeft(), this.bottomBarY(), this.frameWidth(), BAR_HEIGHT);
    }

    /** The sun/moon button switching every config screen between dark and light, at the given spot in the top bar. */
    protected FlatButton addModeToggle(int x) {
        FlatButton toggle = this.addRenderableWidget(FlatButton.icon(modeIcon(), modeTooltip(), button -> {
            CoolCatCoreClientSettings.toggleThemeMode();
            FlatButton self = (FlatButton) button;
            self.withIcon(modeIcon());
            self.tooltip(modeTooltip());
        }));
        toggle.setPosition(x, this.topBarWidgetY(20));
        return toggle;
    }

    // The icon shows the mode a click switches to.
    private static ConfigIcons.Icon modeIcon() {
        return ConfigStyle.mode() == ConfigTheme.Mode.DARK ? ConfigIcons.SUN : ConfigIcons.MOON;
    }

    private static Component modeTooltip() {
        return ConfigStyle.mode() == ConfigTheme.Mode.DARK
                ? Component.translatableWithFallback("config.coolcatcore.mode.light", "Light mode")
                : Component.translatableWithFallback("config.coolcatcore.mode.dark", "Dark mode");
    }

    /** The title in the top bar, with an accent mark before it; returns where the text ends. */
    protected int extractTitle(GuiGraphicsExtractor graphics, Component title, int maxWidth) {
        return this.extractTitle(graphics, title, maxWidth, null);
    }

    /** The title with an icon at the far left of the bar, before the accent mark. */
    protected int extractTitle(GuiGraphicsExtractor graphics, Component title, int maxWidth, @Nullable TitleIcon icon) {
        int x = this.frameLeft() + 10;
        int y = this.topBarY() + (BAR_HEIGHT - 8) / 2;
        if (icon != null && icon.draw(graphics, this.frameLeft() + 7, this.topBarY() + (BAR_HEIGHT - TITLE_ICON) / 2, TITLE_ICON)) {
            x += TITLE_ICON + 5;
            maxWidth -= TITLE_ICON + 5;
        }
        ConfigStyle.rect(graphics, x - 4, y - 1, 2, 10, ConfigStyle.accent());
        Component bold = title.copy().withStyle(ChatFormatting.BOLD);
        ConfigStyle.text(graphics, this.font, bold, x + 2, y, maxWidth, ConfigStyle.colors().text());
        return x + 2 + Math.min(this.font.width(bold), maxWidth);
    }

    /** Draws an icon in a square; returns whether there was one. */
    @FunctionalInterface
    protected interface TitleIcon {
        boolean draw(GuiGraphicsExtractor graphics, int x, int y, int size);
    }
}
