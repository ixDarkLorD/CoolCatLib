package net.ixdarklord.coolcatcore.internal.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.jetbrains.annotations.Nullable;

// Client only. When a client command and a server command share a name (a mod's client "/folios mention" beside its
// server "/folios config"), the loaders run everything starting with that name on the client, so the server's parts
// fail there ("incorrect argument") and never reach the server. This tells them apart: a command the client's commands
// can't run but the server's can (as the client knows them) goes to the server.
public final class ClientCommandFallback {
    private ClientCommandFallback() {}

    /** Whether the command, run as a client command, belongs to the server instead. */
    public static <S> boolean forServer(String command, @Nullable CommandDispatcher<S> clientCommands, @Nullable S source) {
        if (clientCommands == null || source == null) return false;
        try {
            if (runnable(clientCommands.parse(command, source))) return false;
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            return connection != null && runnable(connection.getCommands().parse(command, connection.getSuggestionsProvider()));
        } catch (RuntimeException e) {
            // A command that can't even be parsed is left to the loader, as before.
            return false;
        }
    }

    // Read to the end, and ending on a part that runs: what the dispatcher would execute.
    private static boolean runnable(ParseResults<?> parse) {
        return !parse.getReader().canRead() && parse.getContext().getLastChild().getCommand() != null;
    }
}
