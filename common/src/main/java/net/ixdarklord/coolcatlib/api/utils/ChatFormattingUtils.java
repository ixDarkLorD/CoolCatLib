package net.ixdarklord.coolcatlib.api.utils;

import net.minecraft.ChatFormatting;
import java.util.Comparator;

public final class ChatFormattingUtils {

    private ChatFormattingUtils() {}

    /** Returns the chat formatting array based on percentage thresholds. */
    public static ChatFormatting[] getAssignedResult(int currentValue, int maxValue, ChatFormattingMapping mapping) {
        double percentage = (double) currentValue / maxValue * 100.0;

        return mapping.entries().stream()
                .sorted(Comparator.comparingInt(ChatFormattingMapping.Entry::threshold).reversed())
                .filter(entry -> percentage >= entry.threshold() && entry.formatting().length > 0)
                .map(ChatFormattingMapping.Entry::formatting)
                .findFirst()
                .orElse(new ChatFormatting[]{ChatFormatting.WHITE});
    }

    /** Returns a single color based on 3-level progress (red, yellow, green). */
    public static ChatFormatting getProgressColor(int currentValue, int maxValue) {
        ChatFormattingMapping mapping = new ChatFormattingMapping(
                new ChatFormattingMapping.Entry(0, new ChatFormatting[]{ChatFormatting.RED}),
                new ChatFormattingMapping.Entry(50, new ChatFormatting[]{ChatFormatting.YELLOW}),
                new ChatFormattingMapping.Entry(100, new ChatFormatting[]{ChatFormatting.GREEN})
        );

        ChatFormatting[] result = getAssignedResult(currentValue, maxValue, mapping);
        return result.length > 0 ? result[0] : ChatFormatting.WHITE;
    }
}
