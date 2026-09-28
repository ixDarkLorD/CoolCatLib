package net.ixdarklord.coolcatcore.internal.config.client.gui.style;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

// A text field in the config screens' style. It keeps the vanilla layout (text inset as if bordered) but draws its
// own field instead of the vanilla frame, outlined in the accent while focused and in red while invalid.
public class StyledEditBox extends EditBox {
    // Vanilla's cursor blinks with the ticks a screen hands its fields; this one keeps time itself.
    private static final long TICK_MILLIS = 50;

    private boolean bordered = true;
    private boolean invalid;
    private @Nullable Component placeholder;
    private long lastTick;

    public StyledEditBox(Font font, int width, int height, Component narration) {
        super(font, 0, 0, width, height, narration);
    }

    /** Text shown while the field is empty and unfocused, drawn in the scheme's muted color. */
    public void setPlaceholder(@Nullable Component placeholder) {
        this.placeholder = placeholder;
    }

    public void setInvalid(boolean invalid) {
        this.invalid = invalid;
    }

    @Override
    public void setBordered(boolean bordered) {
        this.bordered = bordered;
        super.setBordered(bordered);
    }

    // The field ticks itself as it draws, so a screen doesn't have to (and ticking it too doesn't blink it faster).
    @Override
    public void tick() {}

    private void advanceCursor() {
        long now = Util.getMillis();
        if (this.lastTick == 0 || now - this.lastTick > TICK_MILLIS * 20) this.lastTick = now;
        while (now - this.lastTick >= TICK_MILLIS) {
            super.tick();
            this.lastTick += TICK_MILLIS;
        }
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        if (!this.isVisible()) return;
        this.advanceCursor();
        if (this.bordered) {
            int border = this.invalid ? ConfigStyle.colors().error()
                    : this.isFocused() ? ConfigStyle.accent()
                    : this.active && this.isHovered() ? ConfigStyle.mix(ConfigStyle.colors().fieldBorder(), ConfigStyle.accent(), 0.4F)
                    : ConfigStyle.colors().fieldBorder();
            ConfigStyle.rect(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight(), this.active ? ConfigStyle.colors().field() : ConfigStyle.colors().buttonDisabled());
            ConfigStyle.outline(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight(), border);
        }
        // The scheme can change between screens, so the text takes its colors as it draws.
        this.setTextColor(this.invalid ? ConfigStyle.colors().error() : ConfigStyle.colors().text());
        this.setTextColorUneditable(ConfigStyle.colors().textMuted());
        // Vanilla draws its own frame, and its text with a shadow (which smudges on light schemes); FieldGraphics
        // drops both.
        super.renderWidget(new FieldGraphics(graphics), mouseX, mouseY, a);
        if (this.placeholder != null && this.getValue().isEmpty() && !this.isFocused()) {
            ConfigStyle.text(graphics, Minecraft.getInstance().font, this.placeholder, this.getX() + 4, this.getY() + (this.getHeight() - 8) / 2,
                    this.getWidth() - 8, ConfigStyle.colors().textMuted());
        }
    }
}
