package net.ixdarklord.coolcatcore.api.registry;

import net.ixdarklord.coolcatcore.internal.platform.CommonServices;
import net.minecraft.world.item.CreativeModeTab;

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
}
