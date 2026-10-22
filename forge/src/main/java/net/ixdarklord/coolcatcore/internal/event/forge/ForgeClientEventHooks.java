package net.ixdarklord.coolcatcore.internal.event.forge;

import com.mojang.brigadier.CommandDispatcher;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientCommandEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientGuiEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientPlayerEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientTickEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ItemTooltipEvents;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventPhase;
import net.ixdarklord.coolcatcore.internal.platform.forge.ClientServicesImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

// Fires CoolCatLib's client events from Forge's, and hooks up the client registrations Forge takes through events.
public final class ForgeClientEventHooks {
    private ForgeClientEventHooks() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register(IEventBus modBus) {
        IEventBus bus = MinecraftForge.EVENT_BUS;

        bus.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.START) ClientTickEvents.START.invoker().onStartTick(Minecraft.getInstance());
            else ClientTickEvents.END.invoker().onEndTick(Minecraft.getInstance());
        });

        bus.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> ClientPlayerEvents.JOIN.invoker().onJoin(event.getPlayer()));
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientPlayerEvents.LEAVE.invoker().onLeave(event.getPlayer()));

        bus.addListener((RenderGuiEvent.Post event) -> {
            if (ClientGuiEvents.RENDER_HUD.hasListeners()) ClientGuiEvents.RENDER_HUD.invoker().render(event.getGuiGraphics(), event.getPartialTick());
        });

        // Each band at its own priority: FIRST before other mods' tooltip listeners, LAST after them.
        tooltipBand(bus, EventPriority.HIGHEST, EventPhase.Band.FIRST);
        tooltipBand(bus, EventPriority.NORMAL, EventPhase.Band.MIDDLE);
        tooltipBand(bus, EventPriority.LOWEST, EventPhase.Band.LAST);

        // CommandSourceStack is a SharedSuggestionProvider, the source type client commands are built with.
        bus.addListener((RegisterClientCommandsEvent event) ->
                ClientCommandEvents.REGISTER.invoker().register((CommandDispatcher<SharedSuggestionProvider>) (CommandDispatcher) event.getDispatcher(), event.getBuildContext()));

        ClientServicesImpl.register(modBus);
    }

    private static void tooltipBand(IEventBus bus, EventPriority priority, EventPhase.Band band) {
        bus.addListener(priority, false, ItemTooltipEvent.class, event -> {
            if (ItemTooltipEvents.MODIFY.hasListeners(band)) {
                ItemTooltipEvents.MODIFY.invoker(band).modify(event.getItemStack(), event.getFlags(), event.getToolTip());
            }
        });
    }
}
