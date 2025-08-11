package net.ixdarklord.coolcatlib.api.util;

import net.minecraft.ChatFormatting;
import java.util.Comparator;

/**
 * Utility class for handling ChatFormatting.
 */
public class ChatFormattingUtils {

    /**
     * Determines the appropriate {@link ChatFormatting} array based on the given percentage of progress.
     *
     * @param currentValue The current progress value.
     * @param maxValue     The maximum possible value.
     * @param mapping      A {@link ChatFormattingMapping} containing percentage thresholds and their associated formatting.
     * @return An array of {@link ChatFormatting} corresponding to the highest applicable threshold, or {@link ChatFormatting#WHITE} if none match.
     */
    public static ChatFormatting[] getAssignedResult(int currentValue, int maxValue, ChatFormattingMapping mapping) {
        double percentage = (double) currentValue / maxValue * 100.0;

        return mapping.getEntries().stream()
                .sorted(Comparator.comparingInt(ChatFormattingMapping.Entry::threshold).reversed()) // Sort descending
                .filter(entry -> percentage >= entry.threshold() && entry.formatting().length > 0)
                .map(ChatFormattingMapping.Entry::formatting)
                .findFirst()
                .orElse(new ChatFormatting[]{ChatFormatting.WHITE});
    }

    /**
     * Retrieves a three-level chat formatting scheme based on percentage completion.
     * <ul>
     *     <li>0% - {@link ChatFormatting#RED}</li>
     *     <li>50% - {@link ChatFormatting#YELLOW}</li>
     *     <li>100% - {@link ChatFormatting#GREEN}</li>
     * </ul>
     *
     * @param currentValue The current progress value.
     * @param maxValue     The maximum possible value.
     * @return An array of {@link ChatFormatting} corresponding to the highest applicable threshold.
     */
    public static ChatFormatting[] get3LevelChatFormatting(int currentValue, int maxValue) {
        ChatFormattingMapping mapping = new ChatFormattingMapping(
                new ChatFormattingMapping.Entry(0, ChatFormatting.RED),
                new ChatFormattingMapping.Entry(50, ChatFormatting.YELLOW),
                new ChatFormattingMapping.Entry(100, ChatFormatting.GREEN)
        );
        return getAssignedResult(currentValue, maxValue, mapping);
    }
}
