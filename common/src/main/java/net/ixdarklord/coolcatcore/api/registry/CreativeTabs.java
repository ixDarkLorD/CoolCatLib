package net.ixdarklord.coolcatcore.api.registry;

import net.ixdarklord.coolcatcore.internal.platform.CommonServices;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * Creative mode tabs.
 */
public final class CreativeTabs {
    private CreativeTabs() {}

    /**
     * A builder for a mod's creative tab, placed by the loader; register the tab it builds in
     * {@code Registries.CREATIVE_MODE_TAB}.
     */
    public static CreativeModeTab.Builder builder() {
        return CommonServices.get().creativeTabBuilder();
    }

    /**
     * A builder for a mod's creative tab, placed by the loader, whose items are listed by {@code items} (each time the
     * tab is filled). Use this from common code instead of {@link CreativeModeTab.Builder#displayItems}, whose output
     * type isn't public on every version.
     */
    public static CreativeModeTab.Builder builder(ItemLister items) {
        return builder().displayItems((parameters, output) -> items.list(parameters, output::accept));
    }

    /** Lists a creative tab's items, in order. */
    @FunctionalInterface
    public interface ItemLister {
        /**
         * Adds the tab's items to {@code items}.
         *
         * @param parameters the enabled features, whether the player is an operator, and the registries
         */
        void list(CreativeModeTab.ItemDisplayParameters parameters, Consumer<ItemStack> items);
    }
}
