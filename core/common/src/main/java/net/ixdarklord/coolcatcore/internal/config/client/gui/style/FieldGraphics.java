package net.ixdarklord.coolcatcore.internal.config.client.gui.style;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

// What StyledEditBox hands vanilla's text field to draw with: the same buffers as the screen's GuiGraphics, at its
// current transform, but text without a shadow where the field asks for the default (shadowed) text, and no plain
// fills, which the field only uses for its frame (its cursor and selection use their own render types). Vanilla's
// field has no switch for either.
final class FieldGraphics extends GuiGraphics {
    FieldGraphics(GuiGraphics target) {
        super(Minecraft.getInstance(), target.bufferSource());
        this.pose().last().pose().set(target.pose().last().pose());
        this.pose().last().normal().set(target.pose().last().normal());
    }

    @Override
    public void fill(int minX, int minY, int maxX, int maxY, int color) {}

    @Override
    public int drawString(Font font, @Nullable String text, int x, int y, int color) {
        return this.drawString(font, text, x, y, color, false);
    }

    @Override
    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color) {
        return this.drawString(font, text, x, y, color, false);
    }

    @Override
    public int drawString(Font font, Component text, int x, int y, int color) {
        return this.drawString(font, text, x, y, color, false);
    }
}
