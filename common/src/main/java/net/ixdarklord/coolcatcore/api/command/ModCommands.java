package net.ixdarklord.coolcatcore.api.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.ixdarklord.coolcatcore.internal.config.CommandOwners;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.List;

/**
 * A mod's commands, under its own names: the first is the command, the others aliases that work the same (each gets
 * the whole command, not a redirect, so {@code /ua} alone runs like {@code /ultimine_addition} alone).
 * <pre>{@code
 * ModCommands.register("ftbultimineaddition", List.of("ultimine_addition", "ua"), (root, context, selection) -> root
 *         .then(Commands.literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
 *                 .executes(command -> reload(command.getSource()))));
 * }</pre>
 * A mod with CoolCatLib configs also gets {@code config} under each name of its server command, opening its config
 * screens: {@code /ua config}, {@code /ua config client}. Anyone can run it, so put permissions on the command's parts
 * rather than on {@code root}: a permission on the root keeps the config command from players too.
 * <p>
 * Client commands ({@link #registerClient}) run on the player's own game, whatever the server has. A client command
 * may share its name with the mod's server command: what the client's parts can run stays on the client, the rest goes
 * to the server ({@code /folios guide} on the client, {@code /folios config} on the server). Give the two different
 * parts: where both have one of the same name, the client's is the one that runs.
 * <pre>{@code
 * ModCommands.registerClient("folios", "folios", (root, context) -> root
 *         .then(LiteralArgumentBuilder.<SharedSuggestionProvider>literal("guide").executes(command -> openGuide())));
 * }</pre>
 * Reply to a client command with {@link net.ixdarklord.coolcatcore.api.event.v2.client.ClientCommandEvents#sendFeedback}
 * and {@code sendError}.
 * <p>
 * A name another command has already (vanilla's {@code /tm} for {@code /teammsg}, another mod's, or for a client
 * command another client command's) is skipped, with a warning in the log: the others still work.
 * <p>
 * Register while the mod is constructed: server commands are built each time the server makes its commands (on start
 * and on {@code /reload}), client commands each time the client makes its own (on joining a world or server).
 */
public final class ModCommands {
    private ModCommands() {}

    /**
     * Registers the mod's server command under its names.
     *
     * @param modId   the mod's id
     * @param names   the command's names: the first is its own, the others aliases; single words, at least one
     * @param builder adds the command's parts to the root it's given, once for each name
     */
    public static void register(String modId, List<String> names, Builder builder) {
        CommandOwners.declare(modId, names, builder);
    }

    /** Registers the mod's server command under one name. */
    public static void register(String modId, String name, Builder builder) {
        register(modId, List.of(name), builder);
    }

    /**
     * Registers the mod's client command under its names: it runs on the player's game and never reaches the server.
     * Safe to call on a dedicated server, where it does nothing.
     *
     * @param modId   the mod's id
     * @param names   the command's names: the first is its own, the others aliases; single words, at least one
     * @param builder adds the command's parts to the root it's given, once for each name
     */
    public static void registerClient(String modId, List<String> names, ClientBuilder builder) {
        CommandOwners.declareClient(modId, names, builder);
    }

    /** Registers the mod's client command under one name. */
    public static void registerClient(String modId, String name, ClientBuilder builder) {
        registerClient(modId, List.of(name), builder);
    }

    /**
     * Registers the mod's client command under the names of its server command ({@link #register}), merged with it
     * or beside it with {@code _client} added: see {@link ClientNames}. A mod without a server command uses its id.
     * <pre>{@code
     * ModCommands.registerClient("folios", ClientNames.MERGED, builder);          // /folios guide beside /folios config
     * ModCommands.registerClient("ultimine_addition", ClientNames.SUFFIXED, builder); // /ua_client, /ultimine_addition_client
     * }</pre>
     */
    public static void registerClient(String modId, ClientNames names, ClientBuilder builder) {
        CommandOwners.declareClient(modId, names, builder);
    }

    /** How a client command takes the names of its mod's server command. */
    public enum ClientNames {
        /**
         * The same names: one command for players, whose client parts run on the client and the rest on the server.
         * Give the two different parts: where both have one of the same name, the client's is the one that runs.
         */
        MERGED,
        /** Each name with {@code _client} after it ({@code /ua_client}): a command of its own beside the server's. */
        SUFFIXED;

        /** The client command's name for one of the server command's. */
        public String apply(String serverName) {
            return this == MERGED ? serverName : serverName + "_client";
        }
    }

    /** Every name of the mod's server commands, in the order registered; empty when it registered none here. */
    public static List<String> names(String modId) {
        return CommandOwners.declaredNames(modId);
    }

    /** Every name of the mod's client commands, in the order registered; empty when it registered none here. */
    public static List<String> clientNames(String modId) {
        return CommandOwners.declaredClientNames(modId);
    }

    @FunctionalInterface
    public interface Builder {
        /**
         * Adds the command's parts to its root.
         *
         * @param root      the command's top-level literal, named one of its names
         * @param context   for arguments that need registries (items, blocks)
         * @param selection which server the commands are for (dedicated, integrated or both)
         */
        void build(LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext context, Commands.CommandSelection selection);
    }

    @FunctionalInterface
    public interface ClientBuilder {
        /**
         * Adds the client command's parts to its root. The loaders' client command sources differ (Fabric's
         * {@code FabricClientCommandSource}, NeoForge's and Forge's {@code CommandSourceStack}); both are
         * {@link SharedSuggestionProvider}s, the type client commands are built with here.
         *
         * @param root    the command's top-level literal, named one of its names
         * @param context for arguments that need registries (items, blocks)
         */
        void build(LiteralArgumentBuilder<SharedSuggestionProvider> root, CommandBuildContext context);
    }
}
