package net.ixdarklord.coolcatcore.api.event.v2.client;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * Client-side commands. The loaders use different command sources (Fabric's {@code FabricClientCommandSource},
 * NeoForge's {@code CommandSourceStack}); both are {@link SharedSuggestionProvider}s, which is the type commands are
 * built with here. Reply through {@link #sendFeedback} / {@link #sendError}.
 */
public final class ClientCommandEvents {
    public static final EventInvoker<Register> REGISTER = EventInvoker.create(Register.class, listeners -> (dispatcher, context) -> {
        for (Register listener : listeners) listener.register(dispatcher, context);
    });

    private ClientCommandEvents() {}

    /** A message in the chat, as a command's reply. */
    public static void sendFeedback(Component message) {
        Minecraft.getInstance().gui.getChat().addClientSystemMessage(message);
    }

    /** An error reply, in red. */
    public static void sendError(Component message) {
        sendFeedback(message.copy().withStyle(ChatFormatting.RED));
    }

    @FunctionalInterface
    public interface Register {
        void register(CommandDispatcher<SharedSuggestionProvider> dispatcher, CommandBuildContext context);
    }
}
