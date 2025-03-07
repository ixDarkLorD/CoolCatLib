package net.ixdarklord.coolcatlib.api.util;

import net.ixdarklord.coolcatlib.internal.chat.FormattedContentSink;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.stream.Collectors;

public class ComponentHelper {

    /**
     * Converts an instance of {@link FormattedText} to a {@link Component}.
     *
     * @param formattedText the text to convert
     * @return the new component
     */
    public static Component toComponent(FormattedText formattedText) {
        return new FormattedContentSink(formattedText).getComponent();
    }

    /**
     * Converts an instance of {@link FormattedCharSequence} to a {@link Component}.
     *
     * @param formattedCharSequence the text to convert
     * @return the new component
     */
    public static Component toComponent(FormattedCharSequence formattedCharSequence) {
        return new FormattedContentSink(formattedCharSequence).getComponent();
    }

    /**
     * Converts an instance of {@link FormattedText} to a string which includes formatting codes supplied via configured
     * {@link net.minecraft.network.chat.Style Styles}.
     * <p>
     * This is mostly useful when working with instances where vanilla still renders raw strings, which inherently
     * support the old chat formatting system, such as in
     * {@link net.minecraft.client.gui.components.DebugScreenOverlay}.
     *
     * @param formattedText the text to convert
     * @return the string
     */
    public static String toString(FormattedText formattedText) {
        return new FormattedContentSink(formattedText).getString();
    }

    /**
     * Converts an instance of {@link FormattedCharSequence} to a string which includes formatting codes supplied via
     * configured {@link net.minecraft.network.chat.Style Styles}.
     * <p>
     * This is mostly useful when working with instances where vanilla still renders raw strings, which inherently
     * support the old chat formatting system, such as in
     * {@link net.minecraft.client.gui.components.DebugScreenOverlay}.
     *
     * @param formattedCharSequence the text to convert
     * @return the string
     */
    public static String toString(FormattedCharSequence formattedCharSequence) {
        return new FormattedContentSink(formattedCharSequence).getString();
    }

    public static Component limitComponent(Component component, int maxWidth) {
        String text = component.getString(maxWidth);
        return component.getString().length() >= maxWidth
                ? Component.literal(text.substring(0, text.length() - 3) + "...").withStyle(component.getStyle())
                : component;
    }

    public static List<Component> splitComponent(Component component, int maxWidth) {
        Font font = Minecraft.getInstance().font;
        return font.getSplitter().splitLines(component, maxWidth, component.getStyle()).stream()
                .map(text -> Component.literal(text.getString()).withStyle(component.getStyle()))
                .collect(Collectors.toList());
    }
}
