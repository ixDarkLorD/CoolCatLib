package net.ixdarklord.coolcatcore.internal.core.neoforge;

import net.ixdarklord.coolcatcore.api.core.neoforge.NeoForgeModEntrypoint;
import net.ixdarklord.coolcatcore.internal.core.client.CoolCatCoreClientConstructor;
import net.ixdarklord.coolcatcore.internal.event.neoforge.NeoForgeClientEventHooks;
import net.ixdarklord.coolcatcore.api.item.ComponentItem;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@Mod(value = CoolCatCore.MOD_ID, dist = Dist.CLIENT)
public class NeoForgeClientSetup extends NeoForgeModEntrypoint {
    public NeoForgeClientSetup(ModContainer container) {
        super(container);
        NeoForgeClientEventHooks.register(this.modEventBus);
        NeoForge.EVENT_BUS.addListener(ItemTooltipEvent.class, event -> ComponentItem.onTooltip(event.getItemStack(), event.getToolTip()));
        this.client(() -> CoolCatCoreClientConstructor::new);
    }
}
