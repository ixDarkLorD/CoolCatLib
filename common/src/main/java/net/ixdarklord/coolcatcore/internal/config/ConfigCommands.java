package net.ixdarklord.coolcatcore.internal.config;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.ixdarklord.coolcatcore.api.config.ConfigScope;
import net.ixdarklord.coolcatcore.api.network.Network;
import net.ixdarklord.coolcatcore.internal.config.network.ConfigNetwork;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

// The config commands. On the server: "/<name> config [config]" for every mod with a config (under its own commands,
// named by CommandOwners) and "/coolcatlib config [mod] [config]" for any, which ask the player's client to open the
// screen (they have to run on the server to sit beside a mod's own command of the same name), and "config reload ...",
// which reads the files again after they were edited by hand: the server's configs there (for operators), the
// player's client configs on their game. On the client: "/coolcatlib config [reload] [mod] [config]", which does
// it right there, for servers without CoolCatLib.
public final class ConfigCommands {
    // Who may reload the server's configs from their files.
    private static final Predicate<CommandSourceStack> CAN_RELOAD = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);

    private ConfigCommands() {}

    /** What a config command does: open the screens it names, or read their configs' files again. */
    public interface Actions<S> {
        /** Opens every mod's configs (no mod), a mod's, or one of its configs. */
        int open(CommandContext<S> context, @Nullable String modId, @Nullable String config);

        /** Reloads every config (no mod), a mod's, or one of its configs, from their files. */
        int reload(CommandContext<S> context, @Nullable String modId, @Nullable String config);
    }

    private static final Actions<CommandSourceStack> SERVER = new Actions<>() {
        @Override
        public int open(CommandContext<CommandSourceStack> context, @Nullable String modId, @Nullable String config) {
            return openOnClient(context, modId, config);
        }

        @Override
        public int reload(CommandContext<CommandSourceStack> context, @Nullable String modId, @Nullable String config) {
            return reloadOnServer(context, modId, config);
        }
    };

    // Called once every mod registered its commands: a config command joining a mod's command of the same name keeps
    // that command's permission (Brigadier keeps the first registration's), so it never opens up the mod's other parts.
    public static void registerServer(CommandDispatcher<CommandSourceStack> dispatcher) {
        Map<String, String> taken = new HashMap<>();
        for (String modId : ConfigManager.modIds()) {
            // CoolCatLib's own configs are under its command for any mod.
            if (modId.equals(CoolCatCore.MOD_ID)) continue;
            // The mod's own commands' names, or its id.
            List<String> names = CommandOwners.names(dispatcher, modId);
            if (names.isEmpty()) names = List.of(modId);
            for (String name : names) {
                if (!CommandOwners.NAME.matcher(name).matches()) {
                    CoolCatCore.LOGGER.warn("{}'s config command name \"{}\" isn't a single word; skipped", modId, name);
                } else if (taken.putIfAbsent(name, modId) != null) {
                    CoolCatCore.LOGGER.warn("{}'s config command /{} is {}'s already; skipped", modId, name, taken.get(name));
                } else {
                    dispatcher.register(Commands.literal(name).then(forMod(modId, SERVER, CAN_RELOAD)));
                }
            }
        }
        dispatcher.register(Commands.literal(CoolCatCore.MOD_ID).then(anyMod(SERVER, CAN_RELOAD)));
    }

    /** {@code config [config]} and {@code config reload [config]}: the mod's configs, or one by name. */
    public static <S> LiteralArgumentBuilder<S> forMod(String modId, Actions<S> actions, Predicate<S> canReload) {
        return LiteralArgumentBuilder.<S>literal("config")
                .executes(context -> actions.open(context, modId, null))
                .then(LiteralArgumentBuilder.<S>literal("reload").requires(canReload)
                        .executes(context -> actions.reload(context, modId, null))
                        .then(ConfigCommands.<S>configArgument(context -> modId)
                                .executes(context -> actions.reload(context, modId, StringArgumentType.getString(context, "config")))))
                .then(ConfigCommands.<S>configArgument(context -> modId)
                        .executes(context -> actions.open(context, modId, StringArgumentType.getString(context, "config"))));
    }

    /**
     * {@code config [mod] [config]} and {@code config reload [mod] [config]}: every mod's configs, a mod's, or one of
     * its configs by name.
     */
    public static <S> LiteralArgumentBuilder<S> anyMod(Actions<S> actions, Predicate<S> canReload) {
        return LiteralArgumentBuilder.<S>literal("config")
                .executes(context -> actions.open(context, null, null))
                .then(LiteralArgumentBuilder.<S>literal("reload").requires(canReload)
                        .executes(context -> actions.reload(context, null, null))
                        .then(modArgument(actions, true)))
                .then(modArgument(actions, false));
    }

    private static <S> RequiredArgumentBuilder<S, String> modArgument(Actions<S> actions, boolean reload) {
        return RequiredArgumentBuilder.<S, String>argument("mod", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(ConfigManager.modIds(), builder))
                .executes(context -> reload
                        ? actions.reload(context, StringArgumentType.getString(context, "mod"), null)
                        : actions.open(context, StringArgumentType.getString(context, "mod"), null))
                .then(ConfigCommands.<S>configArgument(context -> StringArgumentType.getString(context, "mod"))
                        .executes(context -> {
                            String modId = StringArgumentType.getString(context, "mod");
                            String config = StringArgumentType.getString(context, "config");
                            return reload ? actions.reload(context, modId, config) : actions.open(context, modId, config);
                        }));
    }

    private static <S> RequiredArgumentBuilder<S, String> configArgument(ModOf<S> modOf) {
        return RequiredArgumentBuilder.<S, String>argument("config", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(configNames(modOf.modId(context)), builder));
    }

    @FunctionalInterface
    private interface ModOf<S> {
        String modId(CommandContext<S> context);
    }

    /** What's wrong with what a command names, or null when there's a config for it. */
    public static @Nullable Component problem(@Nullable String modId, @Nullable String config) {
        if (modId == null) return null;
        if (ConfigManager.forMod(modId).isEmpty()) {
            return Component.translatableWithFallback("config.coolcatlib.command.unknown_mod", "%s has no configs", modId);
        }
        if (config != null && !configNames(modId).contains(config)) {
            return Component.translatableWithFallback("config.coolcatlib.command.unknown_config", "%s has no config named %s", modId, config);
        }
        return null;
    }

    /** The configs a command names: every one (no mod), a mod's, or one of its configs. */
    public static List<ConfigImpl> named(@Nullable String modId, @Nullable String config) {
        if (modId == null) return new ArrayList<>(ConfigManager.all());
        return ConfigManager.forMod(modId).stream().filter(found -> config == null || found.name().equals(config)).toList();
    }

    /**
     * Reloads the configs from their files, where they're kept on this thread: client configs on the client, the rest
     * on the server (or on the client while no server runs), as the file watcher does. Returns the ids reloaded.
     */
    public static List<String> reload(List<ConfigImpl> configs, boolean clientThread, boolean serverRunning) {
        List<String> reloaded = new ArrayList<>();
        for (ConfigImpl config : configs) {
            boolean clientSide = config.scope() == ConfigScope.CLIENT || !serverRunning;
            if (clientSide != clientThread || config.isRemote() || config.filePath() == null) continue;
            config.reload();
            reloaded.add(config.id().toString());
        }
        return reloaded;
    }

    /** The reply after a reload: the configs reloaded, or that there were none here. */
    public static Component reloaded(List<String> ids) {
        return ids.isEmpty()
                ? Component.translatableWithFallback("config.coolcatlib.command.reloaded_none", "No config files to reload here")
                : Component.translatableWithFallback("config.coolcatlib.command.reloaded", "Reloaded %s", String.join(", ", ids));
    }

    private static List<String> configNames(String modId) {
        return ConfigManager.forMod(modId).stream().map(ConfigImpl::name).toList();
    }

    private static int openOnClient(CommandContext<CommandSourceStack> context, @Nullable String modId, @Nullable String config) {
        CommandSourceStack source = context.getSource();
        Component problem = problem(modId, config);
        if (problem != null) {
            source.sendFailure(problem);
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        if (player == null || !Network.canPlayerReceive(player, ConfigNetwork.OpenScreenPayload.TYPE)) {
            source.sendFailure(Component.translatableWithFallback("config.coolcatlib.command.no_screen", "Config screens open for players with CoolCatLib installed"));
            return 0;
        }
        Network.sendToPlayer(player, new ConfigNetwork.OpenScreenPayload(modId == null ? "" : modId, config == null ? "" : config));
        return 1;
    }

    // The server's configs here, and the player's client configs on their game (which replies itself).
    private static int reloadOnServer(CommandContext<CommandSourceStack> context, @Nullable String modId, @Nullable String config) {
        CommandSourceStack source = context.getSource();
        Component problem = problem(modId, config);
        if (problem != null) {
            source.sendFailure(problem);
            return 0;
        }
        List<ConfigImpl> configs = named(modId, config);
        List<String> reloaded = reload(configs, false, true);
        ServerPlayer player = source.getPlayer();
        boolean clientConfigs = configs.stream().anyMatch(found -> found.scope() == ConfigScope.CLIENT);
        boolean clientReloads = clientConfigs && player != null && Network.canPlayerReceive(player, ConfigNetwork.ReloadPayload.TYPE);
        if (clientReloads) {
            Network.sendToPlayer(player, new ConfigNetwork.ReloadPayload(modId == null ? "" : modId, config == null ? "" : config, reloaded.isEmpty()));
        }
        if (!reloaded.isEmpty() || !clientReloads) source.sendSuccess(() -> reloaded(reloaded), true);
        return Math.max(1, reloaded.size());
    }
}
