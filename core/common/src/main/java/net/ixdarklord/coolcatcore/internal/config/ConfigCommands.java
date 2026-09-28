package net.ixdarklord.coolcatcore.internal.config;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.ixdarklord.coolcatcore.api.config.ConfigPreset;
import net.ixdarklord.coolcatcore.api.config.ConfigScope;
import net.ixdarklord.coolcatcore.api.config.RestartRequirement;
import net.ixdarklord.coolcatcore.api.config.type.ValidationResult;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

// "/coolcatcore config ..." on the server (for common, server and world configs) and "/coolcatcore_client config ..."
// on the client (for client configs), built from the same tree for any command source.
public final class ConfigCommands {
    private static final Feedback<CommandSourceStack> SERVER_FEEDBACK = new Feedback<>() {
        @Override
        public void success(CommandSourceStack source, Component message, boolean broadcast) {
            source.sendSuccess(() -> message, broadcast);
        }

        @Override
        public void failure(CommandSourceStack source, Component message) {
            source.sendFailure(message);
        }
    };

    private ConfigCommands() {}

    static void registerServer(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> config = LiteralArgumentBuilder.<CommandSourceStack>literal("config")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        dispatcher.register(Commands.literal("coolcatcore").then(build(config, found -> found.scope() != ConfigScope.CLIENT, SERVER_FEEDBACK)));
    }

    /** Adds list/get/set/reset/reload/preset under {@code root}, for the configs {@code filter} accepts. */
    public static <S> LiteralArgumentBuilder<S> build(LiteralArgumentBuilder<S> root, Predicate<ConfigImpl> filter, Feedback<S> feedback) {
        SuggestionProvider<S> configs = (context, builder) ->
                SharedSuggestionProvider.suggest(ConfigManager.all().stream().filter(filter).map(config -> config.id().toString()), builder);
        SuggestionProvider<S> paths = (context, builder) -> {
            ConfigImpl config = config(context, filter);
            List<String> values = new ArrayList<>();
            if (config != null) config.allValues().forEach(value -> values.add(value.path()));
            return SharedSuggestionProvider.suggest(values, builder);
        };
        SuggestionProvider<S> presets = (context, builder) -> {
            ConfigImpl config = config(context, filter);
            return SharedSuggestionProvider.suggest(config == null ? List.of() : config.presets().keySet(), builder);
        };
        SuggestionProvider<S> newValues = (context, builder) -> {
            ConfigValueImpl<?> value = value(context, filter);
            return SharedSuggestionProvider.suggest(value == null ? List.of() : valueSuggestions(value), builder);
        };

        return root
                .then(LiteralArgumentBuilder.<S>literal("list").executes(context -> {
                    List<ConfigImpl> found = ConfigManager.all().stream().filter(filter).toList();
                    if (found.isEmpty()) {
                        feedback.failure(context.getSource(), Component.translatableWithFallback("config.coolcatcore.command.none", "No configs"));
                        return 0;
                    }
                    for (ConfigImpl config : found) {
                        feedback.success(context.getSource(), Component.literal(config.id().toString()).withStyle(ChatFormatting.AQUA)
                                .append(Component.literal(" (" + config.scope().name().toLowerCase(Locale.ROOT) + ", " + config.fileName() + ")").withStyle(ChatFormatting.GRAY)), false);
                    }
                    return found.size();
                }))
                .then(RequiredArgumentBuilder.<S, Identifier>argument("config", IdentifierArgument.id()).suggests(configs)
                        .then(LiteralArgumentBuilder.<S>literal("reload").executes(context -> {
                            ConfigImpl config = requireConfig(context, filter, feedback);
                            if (config == null) return 0;
                            config.reload();
                            feedback.success(context.getSource(), Component.translatableWithFallback("config.coolcatcore.command.reloaded", "Reloaded %s", config.id().toString()), true);
                            return 1;
                        }))
                        .then(LiteralArgumentBuilder.<S>literal("get")
                                .executes(context -> {
                                    ConfigImpl config = requireConfig(context, filter, feedback);
                                    if (config == null) return 0;
                                    int count = 0;
                                    for (ConfigValueImpl<?> value : config.allValues()) {
                                        feedback.success(context.getSource(), describe(value), false);
                                        count++;
                                    }
                                    return count;
                                })
                                .then(RequiredArgumentBuilder.<S, String>argument("path", StringArgumentType.word()).suggests(paths).executes(context -> {
                                    ConfigValueImpl<?> value = requireValue(context, filter, feedback);
                                    if (value == null) return 0;
                                    feedback.success(context.getSource(), describe(value), false);
                                    return 1;
                                })))
                        .then(LiteralArgumentBuilder.<S>literal("set")
                                .then(RequiredArgumentBuilder.<S, String>argument("path", StringArgumentType.word()).suggests(paths)
                                        .then(RequiredArgumentBuilder.<S, String>argument("value", StringArgumentType.greedyString()).suggests(newValues).executes(context -> {
                                            ConfigValueImpl<?> value = requireValue(context, filter, feedback);
                                            if (value == null) return 0;
                                            return set(context, value, StringArgumentType.getString(context, "value"), feedback);
                                        }))))
                        .then(LiteralArgumentBuilder.<S>literal("reset")
                                .executes(context -> {
                                    ConfigImpl config = requireConfig(context, filter, feedback);
                                    if (config == null) return 0;
                                    config.resetAll();
                                    config.save();
                                    feedback.success(context.getSource(), Component.translatableWithFallback("config.coolcatcore.command.reset_all", "Reset every value of %s", config.id().toString()), true);
                                    return 1;
                                })
                                .then(RequiredArgumentBuilder.<S, String>argument("path", StringArgumentType.word()).suggests(paths).executes(context -> {
                                    ConfigValueImpl<?> value = requireValue(context, filter, feedback);
                                    if (value == null) return 0;
                                    value.reset();
                                    value.config().save();
                                    feedback.success(context.getSource(), Component.translatableWithFallback("config.coolcatcore.command.reset", "Reset %s", value.path()).append(restartNotice(value.restartRequirement())), true);
                                    return 1;
                                })))
                        .then(LiteralArgumentBuilder.<S>literal("preset")
                                .then(RequiredArgumentBuilder.<S, String>argument("preset", StringArgumentType.word()).suggests(presets).executes(context -> {
                                    ConfigImpl config = requireConfig(context, filter, feedback);
                                    if (config == null) return 0;
                                    ConfigPreset preset = config.presets().get(StringArgumentType.getString(context, "preset"));
                                    if (preset == null) {
                                        feedback.failure(context.getSource(), Component.translatableWithFallback("config.coolcatcore.command.no_preset", "No such preset"));
                                        return 0;
                                    }
                                    config.applyPreset(preset);
                                    config.save();
                                    feedback.success(context.getSource(), Component.translatableWithFallback("config.coolcatcore.command.preset", "Applied preset %s", preset.displayName())
                                            .append(restartNotice(ConfigImpl.restartFor(preset.values().keySet()))), true);
                                    return 1;
                                }))));
    }

    private static <S, T> int set(CommandContext<S> context, ConfigValueImpl<T> value, String text, Feedback<S> feedback) {
        ValidationResult<T> result = value.type().parse(text).then(value::validate);
        if (!result.isOk()) {
            feedback.failure(context.getSource(), Component.literal(value.path() + ": ").append(result.message().orElse(Component.empty())));
            return 0;
        }
        value.set(result.value());
        value.config().save();
        feedback.success(context.getSource(), Component.translatableWithFallback("config.coolcatcore.command.set", "Set %s to %s",
                value.path(), Component.literal(value.type().format(result.value())).withStyle(ChatFormatting.GREEN)).append(restartNotice(value.restartRequirement())), true);
        return 1;
    }

    private static <T> MutableComponent describe(ConfigValueImpl<T> value) {
        T stored = value.getStored();
        MutableComponent text = Component.literal(value.path()).withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" = ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(value.type().format(stored)).withStyle(value.type().equals(stored, value.getDefault()) ? ChatFormatting.WHITE : ChatFormatting.GREEN));
        if (value.isRestartPending()) {
            text.append(Component.literal("  ").append(Component.translatableWithFallback("config.coolcatcore.info.active", "In use until restart: %s",
                    value.type().format(value.get()))).withStyle(ChatFormatting.GOLD));
        }
        return text.append(Component.literal("  ").append(Component.translatableWithFallback("config.coolcatcore.info.default", "Default: %s", value.type().format(value.getDefault())))
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static <T> List<String> valueSuggestions(ConfigValueImpl<T> value) {
        List<String> suggestions = new ArrayList<>(value.type().suggestions());
        String current = value.type().format(value.getStored());
        String defaultValue = value.type().format(value.getDefault());
        if (!suggestions.contains(current)) suggestions.add(current);
        if (!suggestions.contains(defaultValue)) suggestions.add(defaultValue);
        return suggestions;
    }

    private static Component restartNotice(RestartRequirement restart) {
        return switch (restart) {
            case GAME -> Component.literal(" ").append(Component.translatableWithFallback("config.coolcatcore.info.restart_game", "Requires restarting the game").withStyle(ChatFormatting.GOLD));
            case WORLD -> Component.literal(" ").append(Component.translatableWithFallback("config.coolcatcore.info.restart_world", "Requires rejoining the world").withStyle(ChatFormatting.GOLD));
            case NONE -> Component.empty();
        };
    }

    private static <S> @Nullable ConfigImpl config(CommandContext<S> context, Predicate<ConfigImpl> filter) {
        ConfigImpl config;
        try {
            config = ConfigManager.get(context.getArgument("config", Identifier.class));
        } catch (IllegalArgumentException e) {
            return null;
        }
        return config != null && filter.test(config) ? config : null;
    }

    private static <S> @Nullable ConfigValueImpl<?> value(CommandContext<S> context, Predicate<ConfigImpl> filter) {
        ConfigImpl config = config(context, filter);
        if (config == null) return null;
        try {
            return config.value(context.getArgument("path", String.class));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static <S> @Nullable ConfigImpl requireConfig(CommandContext<S> context, Predicate<ConfigImpl> filter, Feedback<S> feedback) {
        ConfigImpl config = config(context, filter);
        if (config == null) {
            feedback.failure(context.getSource(), Component.translatableWithFallback("config.coolcatcore.command.no_config", "No such config"));
        } else if (!config.isLoaded() || config.isRemote()) {
            feedback.failure(context.getSource(), Component.translatableWithFallback("config.coolcatcore.error.unavailable", "That config isn't available here"));
            return null;
        }
        return config;
    }

    private static <S> @Nullable ConfigValueImpl<?> requireValue(CommandContext<S> context, Predicate<ConfigImpl> filter, Feedback<S> feedback) {
        if (requireConfig(context, filter, feedback) == null) return null;
        ConfigValueImpl<?> value = value(context, filter);
        if (value == null) feedback.failure(context.getSource(), Component.translatableWithFallback("config.coolcatcore.command.no_value", "No such setting"));
        return value;
    }

    /** How a command source is answered. */
    public interface Feedback<S> {
        void success(S source, Component message, boolean broadcast);

        void failure(S source, Component message);
    }
}
