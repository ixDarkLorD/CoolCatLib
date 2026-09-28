package net.ixdarklord.coolcatcore.internal.config.client.gui.editor;

import net.ixdarklord.coolcatcore.api.config.client.EditSlot;
import net.ixdarklord.coolcatcore.api.config.client.ValueEditor;
import net.ixdarklord.coolcatcore.api.config.type.EnumType;
import net.ixdarklord.coolcatcore.internal.config.client.gui.style.ConfigIcons;
import net.ixdarklord.coolcatcore.internal.config.client.gui.style.ConfigStyle;
import net.ixdarklord.coolcatcore.internal.config.client.gui.style.FlatButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.CommonComponents;

// A selector between arrows: click (or Enter) for the next constant, right-click or shift for the previous.
public final class EnumEditor<E extends Enum<E>> implements ValueEditor {
    private static final ConfigIcons.Icon LEFT = ConfigIcons.CHEVRON_LEFT;

    private final EditSlot<E> slot;
    private final EnumType<E> type;
    private final CycleButton button;

    public EnumEditor(EditSlot<E> slot, int width, int height) {
        this.slot = slot;
        this.type = (EnumType<E>) slot.type();
        this.button = new CycleButton(width, height);
        this.refresh();
    }

    @Override
    public FlatButton widget() {
        return this.button;
    }

    @Override
    public void refresh() {
        this.button.setMessage(this.type.displayName(this.slot.get()));
    }

    private final class CycleButton extends FlatButton {
        CycleButton(int width, int height) {
            super(width, height, CommonComponents.EMPTY, button -> {});
        }

        @Override
        public void onPress(InputWithModifiers input) {
            boolean backwards = input.hasShiftDown() || (input instanceof MouseButtonEvent event && event.button() == 1);
            EnumEditor.this.slot.set(EnumEditor.this.type.cycle(EnumEditor.this.slot.get(), backwards));
            EnumEditor.this.refresh();
        }

        @Override
        protected boolean isValidClickButton(MouseButtonInfo buttonInfo) {
            return buttonInfo.button() == 0 || buttonInfo.button() == 1;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            super.extractContents(graphics, mouseX, mouseY, a);
            int color = !this.active ? ConfigStyle.colors().textMuted() : this.isHoveredOrFocused() ? ConfigStyle.accent() : ConfigStyle.colors().textDim();
            int y = this.getY() + (this.getHeight() - LEFT.height()) / 2;
            LEFT.draw(graphics, this.getX() + 5, y, color);
            ConfigIcons.CHEVRON.draw(graphics, this.getRight() - 5 - ConfigIcons.CHEVRON.width(), y, color);
        }
    }
}
