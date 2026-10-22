package net.ixdarklord.coolcatcore.api.client.item;

import net.ixdarklord.coolcatcore.api.attachment.Attachment;
import net.ixdarklord.coolcatcore.api.attachment.AttachmentRegistry;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentBundle;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * When the item in a player's hand is shown being put away and taken out again. Vanilla plays that swap whenever the
 * held stack changes in any way but a few (durability, for one), so data a mod keeps on a held item (a mark, a
 * charge, a page) makes the item dip out of view and back each time it changes. Here a mod says which changes are
 * quiet: the stack shown simply becomes the new one, in place.
 * <pre>{@code
 * HeldItemSwaps.quiet(MyComponents.CHARGE);                         // changes to these components alone
 * HeldItemSwaps.register((shown, next) -> shown.is(MyItems.WAND)    // or any test of the old and new stacks
 *         && next.is(MyItems.WAND));
 * }</pre>
 * Changes to a mod's attachments ({@code net.ixdarklord.coolcatcore.api.attachment}) are quiet already, unless the
 * attachment was built with {@code swapAnimation()}.
 * Client only; call it while the client sets up.
 */
public final class HeldItemSwaps {
    private static final Set<DataComponentType<?>> QUIET = new CopyOnWriteArraySet<>();
    private static final List<Supplier<? extends DataComponentType<?>>> QUIET_LATER = new CopyOnWriteArrayList<>();
    private static final List<SwapFilter> FILTERS = new CopyOnWriteArrayList<>();

    private HeldItemSwaps() {}

    /** Changes to these components alone, and to those that vanilla already ignores, don't play the swap. */
    public static void quiet(DataComponentType<?>... types) {
        QUIET.addAll(List.of(types));
    }

    /**
     * The same for a component registered later: it is looked up only once stacks are compared, so a mod can call
     * this while it sets up, before its components are registered (as they are then on NeoForge).
     */
    public static void quiet(Supplier<? extends DataComponentType<?>> type) {
        QUIET_LATER.add(type);
    }

    /** A test of the stack shown and the one now held: when it says they are the same item, no swap plays. */
    public static void register(SwapFilter filter) {
        FILTERS.add(filter);
    }

    /** Whether going from the stack shown to the one now held is quiet: the new one simply takes its place. */
    public static boolean isQuiet(ItemStack shown, ItemStack next) {
        if (shown.isEmpty() || next.isEmpty()) return false;
        if (!QUIET_LATER.isEmpty()) {
            for (Supplier<? extends DataComponentType<?>> type : QUIET_LATER) QUIET.add(type.get());
            QUIET_LATER.clear();
        }
        // Every other component the same, and each mod's attachments differing only in quiet ones.
        Set<DataComponentType<?>> bundles = new java.util.HashSet<>();
        for (AttachmentRegistry registry : AttachmentRegistry.all()) bundles.add(registry.bundleComponent());
        if (ItemStack.matchesIgnoringComponents(shown, next, type -> type.ignoreSwapAnimation() || QUIET.contains(type) || bundles.contains(type))
                && quietAttachments(shown, next)) return true;
        for (SwapFilter filter : FILTERS) {
            if (filter.sameItem(shown, next)) return true;
        }
        return false;
    }

    // Whether each mod's attachments on the two stacks differ only in attachments whose changes are quiet.
    private static boolean quietAttachments(ItemStack shown, ItemStack next) {
        for (AttachmentRegistry registry : AttachmentRegistry.all()) {
            DataComponentType<AttachmentBundle> type = registry.bundleComponent();
            AttachmentBundle before = shown.getOrDefault(type, AttachmentBundle.EMPTY);
            AttachmentBundle after = next.getOrDefault(type, AttachmentBundle.EMPTY);
            if (before.equals(after)) continue;
            Set<Attachment<?>> keys = new java.util.HashSet<>(before.values().keySet());
            keys.addAll(after.values().keySet());
            for (Attachment<?> attachment : keys) {
                if (attachment.playsSwapAnimation() && !java.util.Objects.equals(before.get(attachment), after.get(attachment))) return false;
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
