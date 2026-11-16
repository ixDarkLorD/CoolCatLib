package net.ixdarklord.coolcatcore.api.event.v2.common;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.ixdarklord.coolcatcore.api.command.ModCommands;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventResult;
import net.ixdarklord.coolcatcore.internal.config.CommandOwners;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Server commands: registered, and run.
 */
public final class CommandEvents {
    /**
     * The server's commands are being made: on start and on {@code /reload}.
     *
     * @deprecated Use {@link ModCommands#register}, which takes the mod's id and its command's names (and aliases), so
     * CoolCatLib knows which commands are the mod's (its config command joins them). Listeners here still work: their
     * commands are matched to a mod by package, the way the config command found them before.
     */
    @Deprecated
    public static final EventInvoker<Register> REGISTER = EventInvoker.create(Register.class, listeners -> (dispatcher, context, selection) -> {
        CommandOwners.registerDeclared(dispatcher, context, selection);
        for (Register listener : listeners) CommandOwners.register(dispatcher, listener, () -> listener.register(dispatcher, context, selection));
    });

    public static final EventInvoker<Execute> EXECUTE = EventInvoker.create(Execute.class, listeners -> (parse, command) -> {
        for (Execute listener : listeners) {
            EventResult result = listener.onExecute(parse, command);
            if (result.isInterrupt()) return result;
        }
        return EventResult.PASS;
    });

    private CommandEvents() {}

    /** @deprecated Use {@link ModCommands.Builder}, through {@link ModCommands#register}. */
    @Deprecated
    @FunctionalInterface
    public interface Register {
        void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection);
    }

    /**
     * A command is about to run: typed by a player, sent from the console or a command block, or run by another mod
     * through the server's command dispatcher (not each line of a function). Interrupting stops it; tell the source why
     * with {@code parse.getContext().getSource().sendFailure(...)}.
     *
     * @param parse   the parsed command; its source is who runs it ({@code getContext().getSource()})
     * @param command the command as written, without its slash
     */
    @FunctionalInterface
    public interface Execute {
        EventResult onExecute(ParseResults<CommandSourceStack> parse, String command);
    }
}
