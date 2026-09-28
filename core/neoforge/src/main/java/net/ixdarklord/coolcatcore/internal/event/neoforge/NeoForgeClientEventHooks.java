package net.ixdarklord.coolcatcore.internal.event.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientCommandEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientGuiEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientPlayerEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientTickEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ItemTooltipEvents;
import net.ixdarklord.coolcatcore.internal.platform.neoforge.ClientServicesImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

// Fires CoolCatLib: Core's client events from NeoForge's, and drains the client registrations NeoForge takes through events.
public final class NeoForgeClientEventHooks {
    private NeoForgeClientEventHooks() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register(IEventBus modBus) {
        IEventBus bus = NeoForge.EVENT_BUS;

        bus.addListener((ClientTickEvent.Pre event) -> ClientTickEvents.START.invoker().onStartTick(Minecraft.getInstance()));
        bus.addListener((ClientTickEvent.Post event) -> ClientTickEvents.END.invoker().onEndTick(Minecraft.getInstance()));

        bus.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> ClientPlayerEvents.JOIN.invoker().onJoin(event.getPlayer()));
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientPlayerEvents.LEAVE.invoker().onLeave(event.getPlayer()));

        bus.addListener((RenderGuiEvent.Post event) -> {
            if (ClientGuiEvents.RENDER_HUD.hasListeners()) ClientGuiEvents.RENDER_HUD.invoker().render(event.getGuiGraphics(), event.getPartialTick());
        });

        bus.addListener((ItemTooltipEvent event) ->
                ItemTooltipEvents.MODIFY.invoker().modify(event.getItemStack(), event.getContext(), event.getFlags(), event.getToolTip()));

        // CommandSourceStack is a SharedSuggestionProvider, the source type client commands are built with.
        bus.addListener((RegisterClientCommandsEvent event) ->
                ClientCommandEvents.REGISTER.invoker().register((CommandDispatcher<SharedSuggestionProvider>) (CommandDispatcher) event.getDispatcher(), event.getBuildContext()));

        modBus.addListener((RegisterKeyMappingsEvent event) -> ClientServicesImpl.onRegisterKeyMappings(event));
        modBus.addListener((RegisterMenuScreensEvent event) -> ClientServicesImpl.onRegisterMenuScreens(event));
        modBus.addListener((RegisterClientTooltipComponentFactoriesEvent event) -> ClientServicesImpl.onRegisterTooltipComponents(event));
    }
}
