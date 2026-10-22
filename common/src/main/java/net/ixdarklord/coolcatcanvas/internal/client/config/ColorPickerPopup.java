package net.ixdarklord.coolcatcanvas.internal.client.config;

import net.ixdarklord.coolcatcanvas.internal.client.gui.style.ConfigStyle;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.FlatButton;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.StyledEditBox;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.StyledPopup;
import net.ixdarklord.coolcatcore.api.config.ConfigTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.IntConsumer;

// A color picker over the config screen: a swatch of the color, a slider per channel (alpha too when the color has
// one), and its hex code to type or paste.
public final class ColorPickerPopup extends StyledPopup {
    private static final int WIDTH = 230;
    private static final int SWATCH = 44;
    private static final int CHANNEL_HEIGHT = 14;
    private static final String[] CHANNELS = {"R", "G", "B", "A"};

    private final boolean alpha;
    private final IntConsumer onDone;
    private int color;
    private StyledEditBox hex;
    private int dragging = -1;

    public ColorPickerPopup(@Nullable Screen parent, ConfigTheme theme, Component title, int color, boolean alpha, IntConsumer onDone) {
        super(parent, title, theme);
        this.alpha = alpha;
        this.onDone = onDone;
        this.color = alpha ? color : color | 0xFF000000;
    }

    private int channels() {
        return this.alpha ? 4 : 3;
    }

    @Override
    protected void initPopup() {
        this.setPanel(WIDTH, PADDING + TITLE_HEIGHT + Math.max(SWATCH, this.channels() * CHANNEL_HEIGHT) + 8 + FOOTER + PADDING);
        this.hex = this.addRenderableWidget(new StyledEditBox(this.font, this.contentWidth() - 4 - 100, 20, Component.translatableWithFallback("coolcatlib.config.color.hex", "Hex code")));
        this.hex.setPosition(this.contentLeft(), this.footerButtonY());
        this.hex.setMaxLength(9);
        this.hex.setValue(this.format());
        this.hex.setResponder(text -> {
            Integer parsed = this.parse(text);
            this.hex.setInvalid(parsed == null);
            if (parsed != null) this.color = parsed;
        });
        FlatButton done = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_DONE, 100, button -> {
            this.onDone.accept(this.color);
            this.minecraft.gui.setScreen(this.parent);
        }).style(FlatButton.Style.PRIMARY));
        done.setPosition(this.contentLeft() + this.contentWidth() - 100, this.footerButtonY());
    }

    private String format() {
        return this.alpha && (this.color >>> 24) != 0xFF
                ? String.format(Locale.ROOT, "#%08X", this.color)
                : String.format(Locale.ROOT, "#%06X", this.color & 0xFFFFFF);
    }

    private @Nullable Integer parse(String text) {
        String digits = text.trim();
        if (digits.startsWith("#")) digits = digits.substring(1);
        if (digits.length() != 6 && !(this.alpha && digits.length() == 8)) return null;
        try {
            int value = (int) Long.parseLong(digits, 16);
            return digits.length() == 6 ? value | 0xFF000000 : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- Channels: 0 red, 1 green, 2 blue, 3 alpha ----

    private int shift(int channel) {
        return channel == 3 ? 24 : 16 - channel * 8;
    }

    private int channel(int channel) {
        return this.color >>> this.shift(channel) & 0xFF;
    }

    private int withChannel(int channel, int value) {
        int shift = this.shift(channel);
        return this.color & ~(0xFF << shift) | (value & 0xFF) << shift;
    }

    private int sliderX() {
        return this.contentLeft() + SWATCH + 10 + 10;
    }

    private int sliderWidth() {
        return this.contentLeft() + this.contentWidth() - 26 - this.sliderX();
    }

    private int sliderY(int channel) {
        return this.contentTop() + 2 + channel * CHANNEL_HEIGHT;
    }

    @Override
    protected void extractPopup(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int x = this.contentLeft();
        int y = this.contentTop() + 2;
        // The swatch, over a checkerboard so a translucent color shows as one.
        ConfigStyle.rect(graphics, x, y, SWATCH, SWATCH, ConfigStyle.colors().fieldBorder());
        for (int cy = 0; cy < SWATCH - 2; cy += 6) {
            for (int cx = 0; cx < SWATCH - 2; cx += 6) {
                int shade = (cx / 6 + cy / 6) % 2 == 0 ? 0xFFCCCCCC : 0xFF888888;
                graphics.fill(x + 1 + cx, y + 1 + cy, x + 1 + Math.min(cx + 6, SWATCH - 2), y + 1 + Math.min(cy + 6, SWATCH - 2), shade);
            }
        }
        graphics.fill(x + 1, y + 1, x + SWATCH - 1, y + SWATCH - 1, this.color);

        int sliderX = this.sliderX();
        int width = this.sliderWidth();
        for (int channel = 0; channel < this.channels(); channel++) {
            int sliderY = this.sliderY(channel);
            ConfigStyle.text(graphics, this.font, Component.literal(CHANNELS[channel]), sliderX - 10, sliderY + 2, 8, ConfigStyle.colors().textDim());
            // The track runs from this channel at 0 to it at 255, the others as they are.
            int steps = Math.max(1, width / 2);
            for (int i = 0; i < steps; i++) {
                int left = sliderX + i * width / steps;
                int right = sliderX + (i + 1) * width / steps;
                int shade = this.withChannel(channel, Math.round(255F * i / Math.max(1, steps - 1)));
                graphics.fill(left, sliderY + 3, right, sliderY + 9, channel == 3 ? shade : shade | 0xFF000000);
            }
            ConfigStyle.outline(graphics, sliderX - 1, sliderY + 2, width + 2, 8, ConfigStyle.colors().fieldBorder());
            int value = this.channel(channel);
            int knobX = sliderX + Math.round((width - 3) * value / 255F);
            ConfigStyle.rect(graphics, knobX - 1, sliderY, 5, 12, ConfigStyle.colors().knob());
            ConfigStyle.outline(graphics, knobX - 1, sliderY, 5, 12, ConfigStyle.colors().panelBorder());
            ConfigStyle.text(graphics, this.font, Component.literal(String.valueOf(value)), sliderX + width + 5, sliderY + 2, 22, ConfigStyle.colors().textMuted());
        }
    }

    private int channelAt(double mouseX, double mouseY) {
        if (mouseX < this.sliderX() - 2 || mouseX > this.sliderX() + this.sliderWidth() + 2) return -1;
        for (int channel = 0; channel < this.channels(); channel++) {
            int y = this.sliderY(channel);
            if (mouseY >= y && mouseY < y + CHANNEL_HEIGHT - 1) return channel;
        }
        return -1;
    }

    private void drag(double mouseX) {
        int value = Mth.clamp(Math.round(255F * (float) (mouseX - this.sliderX()) / (this.sliderWidth() - 3)), 0, 255);
        this.color = this.withChannel(this.dragging, value);
        this.hex.setValue(this.format());
        this.hex.setInvalid(false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int channel = event.button() == 0 ? this.channelAt(event.x(), event.y()) : -1;
        if (channel >= 0) {
            this.dragging = channel;
            this.drag(event.x());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.dragging >= 0) {
            this.drag(event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.dragging >= 0) {
            this.dragging = -1;
            return true;
        }
        return super.mouseReleased(event);
    }
}
