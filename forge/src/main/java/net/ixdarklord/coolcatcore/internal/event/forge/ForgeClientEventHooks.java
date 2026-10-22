package net.ixdarklord.coolcatcore.internal.event.forge;

import com.mojang.brigadier.CommandDispatcher;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientCommandEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientPlayerEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientTickEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ItemTooltipEvents;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventPhase;
import net.ixdarklord.coolcatcore.api.item.ComponentItem;
import net.ixdarklord.coolcatcore.internal.platform.forge.ClientServicesImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

import static net.ixdarklord.coolcatcore.internal.event.forge.ForgeEventHooks.listen;

// Fires CoolCatLib's client events from Forge's, and drains the client registrations Forge takes through events.
public final class ForgeClientEventHooks {
    private ForgeClientEventHooks() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register(IEventBus modBus) {
        IEventBus bus = MinecraftForge.EVENT_BUS;

        listen(bus, TickEvent.ClientTickEvent.Pre.class, event -> ClientTickEvents.START.invoker().onStartTick(Minecraft.getInstance()));
        listen(bus, TickEvent.ClientTickEvent.Post.class, event -> ClientTickEvents.END.invoker().onEndTick(Minecraft.getInstance()));

        listen(bus, ClientPlayerNetworkEvent.LoggingIn.class, event -> ClientPlayerEvents.JOIN.invoker().onJoin(event.getPlayer()));
        listen(bus, ClientPlayerNetworkEvent.LoggingOut.class, event -> ClientPlayerEvents.LEAVE.invoker().onLeave(event.getPlayer()));

        // ClientGuiEvents.RENDER_HUD comes from GuiMixin (Forge 52.1 has no HUD render event).

        listen(bus, ItemTooltipEvent.class, event -> ComponentItem.onTooltip(event.getItemStack(), event.getToolTip()));
        // Each band at its own priority: FIRST before other mods' tooltip listeners, LAST after them.
        tooltipBand(bus, EventPriority.HIGHEST, EventPhase.Band.FIRST);
        tooltipBand(bus, EventPriority.NORMAL, EventPhase.Band.MIDDLE);
        tooltipBand(bus, EventPriority.LOWEST, EventPhase.Band.LAST);

        // CommandSourceStack is a SharedSuggestionProvider, the source type client commands are built with.
        listen(bus, RegisterClientCommandsEvent.class, event ->
                ClientCommandEvents.REGISTER.invoker().register((CommandDispatcher<SharedSuggestionProvider>) (CommandDispatcher) event.getDispatcher(), event.getBuildContext()));

        listen(modBus, RegisterKeyMappingsEvent.class, ClientServicesImpl::onRegisterKeyMappings);
        listen(modBus, RegisterClientTooltipComponentFactoriesEvent.class, ClientServicesImpl::onRegisterTooltipComponents);
        listen(modBus, net.minecraftforge.client.event.ModelEvent.RegisterAdditional.class, ClientServicesImpl::onRegisterAdditionalModels);
    }

    // Forge's tooltip event has no tooltip context; the client level's is the one vanilla uses for tooltips.
    private static void tooltipBand(IEventBus bus, EventPriority priority, EventPhase.Band band) {
        bus.addListener(priority, false, ItemTooltipEvent.class, event -> {
            if (ItemTooltipEvents.MODIFY.hasListeners(band)) {
                ItemTooltipEvents.MODIFY.invoker(band).modify(event.getItemStack(), Item.TooltipContext.of(Minecraft.getInstance().level), event.getFlags(), event.getToolTip());
            }
        });
    }
}
