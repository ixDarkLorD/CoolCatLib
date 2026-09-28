package net.ixdarklord.coolcatcore.internal.event.fabric;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientCommandEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientGuiEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientPlayerEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ItemTooltipEvents;
import net.minecraft.commands.SharedSuggestionProvider;

// Fires CoolCatLib: Core's client events from Fabric's.
public final class FabricClientEventHooks {
    private FabricClientEventHooks() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(minecraft ->
                net.ixdarklord.coolcatcore.api.event.v2.client.ClientTickEvents.START.invoker().onStartTick(minecraft));
        ClientTickEvents.END_CLIENT_TICK.register(minecraft ->
                net.ixdarklord.coolcatcore.api.event.v2.client.ClientTickEvents.END.invoker().onEndTick(minecraft));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> {
            if (minecraft.player != null) ClientPlayerEvents.JOIN.invoker().onJoin(minecraft.player);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> ClientPlayerEvents.LEAVE.invoker().onLeave(minecraft.player));

        // Drawn after vanilla's HUD.
        HudRenderCallback.EVENT.register((graphics, partialTick) -> {
            if (ClientGuiEvents.RENDER_HUD.hasListeners()) ClientGuiEvents.RENDER_HUD.invoker().render(graphics, partialTick);
        });

        ItemTooltipCallback.EVENT.register((stack, flag, lines) -> ItemTooltipEvents.MODIFY.invoker().modify(stack, flag, lines));

        // FabricClientCommandSource is a SharedSuggestionProvider, the source type client commands are built with.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
                ClientCommandEvents.REGISTER.invoker().register((CommandDispatcher<SharedSuggestionProvider>) (CommandDispatcher) dispatcher, context));
    }
}
