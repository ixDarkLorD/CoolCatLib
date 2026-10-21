package net.ixdarklord.coolcatcore.api.client.item;

import net.ixdarklord.coolcatcore.api.attachment.Attachment;
import net.ixdarklord.coolcatcore.api.attachment.AttachmentRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * When the item in a player's hand is shown being put away and taken out again. Vanilla plays that swap whenever the
 * held stack changes in any way but a few (durability, for one), so data a mod keeps on a held item (a mark, a
 * charge, a page) makes the item dip out of view and back each time it changes. Here a mod says which changes are
 * quiet: the stack shown simply becomes the new one, in place.
 * <pre>{@code
 * HeldItemSwaps.quiet("mymod_charge");                            // changes to these NBT keys alone
 * HeldItemSwaps.register((shown, next) -> shown.is(MyItems.WAND)    // or any test of the old and new stacks
 *         && next.is(MyItems.WAND));
 * }</pre>
 * Changes to a mod's attachments ({@code net.ixdarklord.coolcatcore.api.attachment}) are quiet already, unless the
 * attachment was built with {@code swapAnimation()}.
 * Client only; call it while the client sets up.
 */
public final class HeldItemSwaps {
    private static final Set<String> QUIET = new CopyOnWriteArraySet<>();
    private static final List<SwapFilter> FILTERS = new CopyOnWriteArrayList<>();

    private HeldItemSwaps() {}

    /** Changes to these keys of a stack's NBT alone don't play the swap. */
    public static void quiet(String... keys) {
        QUIET.addAll(List.of(keys));
    }

    /** A test of the stack shown and the one now held: when it says they are the same item, no swap plays. */
    public static void register(SwapFilter filter) {
        FILTERS.add(filter);
    }

    /** Whether going from the stack shown to the one now held is quiet: the new one simply takes its place. */
    public static boolean isQuiet(ItemStack shown, ItemStack next) {
        if (shown.isEmpty() || next.isEmpty()) return false;
        // Every other key the same, and each mod's attachments differing only in quiet ones.
        Set<String> bundles = new HashSet<>();
        for (AttachmentRegistry registry : AttachmentRegistry.all()) bundles.add(registry.tagName());
        if (sameIgnoring(shown, next, key -> QUIET.contains(key) || bundles.contains(key)) && quietAttachments(shown, next)) return true;
        for (SwapFilter filter : FILTERS) {
            if (filter.sameItem(shown, next)) return true;
        }
        return false;
    }

    /** The same item and count, and the same NBT but for the keys ignored. */
    public static boolean sameIgnoring(ItemStack shown, ItemStack next, java.util.function.Predicate<String> ignored) {
        if (!shown.is(next.getItem()) || shown.getCount() != next.getCount()) return false;
        CompoundTag before = shown.getTag();
        CompoundTag after = next.getTag();
        Set<String> keys = new HashSet<>();
        if (before != null) keys.addAll(before.getAllKeys());
        if (after != null) keys.addAll(after.getAllKeys());
        for (String key : keys) {
            if (!ignored.test(key) && !Objects.equals(get(before, key), get(after, key))) return false;
        }
        return true;
    }

    private static @Nullable Tag get(@Nullable CompoundTag tag, String key) {
        return tag != null ? tag.get(key) : null;
    }

    // Whether each mod's attachments on the two stacks differ only in attachments whose changes are quiet.
    private static boolean quietAttachments(ItemStack shown, ItemStack next) {
        for (AttachmentRegistry registry : AttachmentRegistry.all()) {
            CompoundTag before = shown.getTag() != null ? shown.getTag().getCompound(registry.tagName()) : new CompoundTag();
            CompoundTag after = next.getTag() != null ? next.getTag().getCompound(registry.tagName()) : new CompoundTag();
            if (before.equals(after)) continue;
            Set<String> names = new HashSet<>(before.getAllKeys());
            names.addAll(after.getAllKeys());
            for (String name : names) {
                if (Objects.equals(before.get(name), after.get(name))) continue;
                Attachment<?> attachment = registry.get(name);
                if (attachment == null || attachment.playsSwapAnimation()) return false;
            }
        }
        return true;
    }

    @FunctionalInterface
    public interface SwapFilter {
        /** Whether the stack shown and the one now held are to be seen as the same item, with no swap between them. */
        boolean sameItem(ItemStack shown, ItemStack next);
    }
}
