package net.ixdarklord.coolcatcore.internal.config.client.gui.style;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

// Draws into the same buffers as another GuiGraphics, at its current transform, but writes text without a shadow
// where the caller asks for the default (shadowed) text. Vanilla's text field has no switch for its shadow.
final class FlatTextGraphics extends GuiGraphics {
    FlatTextGraphics(GuiGraphics target) {
        super(Minecraft.getInstance(), target.bufferSource());
        this.pose().last().pose().set(target.pose().last().pose());
        this.pose().last().normal().set(target.pose().last().normal());
    }

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
