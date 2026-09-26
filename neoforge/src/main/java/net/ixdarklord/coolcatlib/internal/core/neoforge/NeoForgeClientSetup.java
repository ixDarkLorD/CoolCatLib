package net.ixdarklord.coolcatlib.internal.core.neoforge;

import net.ixdarklord.coolcatlib.api.item.ComponentItem;
import net.ixdarklord.coolcatlib.internal.core.CoolCatLib;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@Mod(value = CoolCatLib.MOD_ID, dist = Dist.CLIENT)
public class NeoForgeClientSetup {
    public NeoForgeClientSetup() {
        NeoForge.EVENT_BUS.addListener(ItemTooltipEvent.class, event -> ComponentItem.onTooltip(event.getItemStack(), event.getToolTip()));
    }
}
