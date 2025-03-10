package net.ixdarklord.coolcatlib.api.util;

import net.minecraft.ChatFormatting;

import java.util.List;

/**
 * A mapping of percentage thresholds to corresponding ChatFormatting styles.
 */
public class ChatFormattingMapping {
    private final List<Entry> entries;

    /**
     * Constructs a new ChatFormattingMapping with the provided entries.
     *
     * @param entries The percentage-based formatting entries.
     */
    public ChatFormattingMapping(Entry... entries) {
        this.entries = List.of(entries);
    }

    /**
     * Retrieves the list of formatting entries.
     *
     * @return The list of formatting mappings.
     */
    public List<Entry> getEntries() {
        return entries;
    }

    /**
     * Represents an individual percentage threshold and its associated chat formatting styles.
     */
    public record Entry(int threshold, ChatFormatting... formatting) {

        /**
         * Constructs a new Entry with the specified threshold and formatting styles.
         *
         * @param threshold  The percentage threshold.
         * @param formatting The associated ChatFormatting styles.
         */
        public Entry {}

        /**
         * Retrieves the threshold value.
         *
         * @return The percentage threshold.
         */
        @Override
        public int threshold() {
            return threshold;
        }

        /**
         * Retrieves the assigned formatting styles.
         *
         * @return An array of ChatFormatting styles.
         */
        @Override
        public ChatFormatting[] formatting() {
            return formatting;
        }
    }
}
