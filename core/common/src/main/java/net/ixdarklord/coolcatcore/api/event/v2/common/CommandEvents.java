package net.ixdarklord.coolcatcore.api.event.v2.common;

import com.mojang.brigadier.CommandDispatcher;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Server commands.
 */
public final class CommandEvents {
    public static final EventInvoker<Register> REGISTER = EventInvoker.create(Register.class, listeners -> (dispatcher, context, selection) -> {
        for (Register listener : listeners) listener.register(dispatcher, context, selection);
    });

    private CommandEvents() {}

    @FunctionalInterface
    public interface Register {
        void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection);
    }
}
