package net.ixdarklord.coolcatlib.internal.core.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.ixdarklord.coolcatlib.api.item.ComponentItem;

public class FabricClientSetup implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> ComponentItem.onTooltip(stack, lines));
    }
}
