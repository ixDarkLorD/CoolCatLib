package net.ixdarklord.coolcatcore.internal.config.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.ixdarklord.coolcatcore.api.config.ConfigEvents;
import net.ixdarklord.coolcatcore.api.config.ConfigScope;
import net.ixdarklord.coolcatcore.api.config.StartupSync;
import net.ixdarklord.coolcatcore.api.config.type.ValidationResult;
import net.ixdarklord.coolcatcore.api.config.client.ConfigScreens;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientCommandEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientPlayerEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientTickEvents;
import net.ixdarklord.coolcatcore.api.network.Network;
import net.ixdarklord.coolcatcore.internal.config.ConfigCommands;
import net.ixdarklord.coolcatcore.internal.config.ConfigImpl;
import net.ixdarklord.coolcatcore.internal.config.ConfigManager;
import net.ixdarklord.coolcatcore.internal.config.ConfigValueImpl;
import net.ixdarklord.coolcatcore.internal.config.client.gui.CategoryPopup;
import net.ixdarklord.coolcatcore.internal.config.client.gui.ConfigScreen;
import net.ixdarklord.coolcatcore.internal.config.client.gui.StartupMismatchScreen;
import net.ixdarklord.coolcatcore.internal.config.network.ConfigNetwork;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

// The client's side of synced configs: showing the server's values while connected (and restoring the client's own
// afterwards), sending a player's edits, and the client command.
public final class ClientConfigManager {
    private static final Map<ResourceLocation, JsonObject> OWN_VALUES = new HashMap<>();
    private static final Map<ResourceLocation, Boolean> CAN_EDIT = new HashMap<>();
    private static final int MISMATCH_LINES = 6;
    private static final int MISMATCH_SCREEN_TIMEOUT = 400;
    private static @Nullable Supplier<Screen> pendingScreen;
    // Offered once the disconnect a startup mismatch caused has shown its screen.
    private static @Nullable List<StartupMismatch> pendingMismatches;
    private static int mismatchTicks;

    private ClientConfigManager() {}

    /** Called once by each loader's client setup. */
    public static void init() {
        CoolCatCoreClientSettings.init();
        ClientTickEvents.END.register(minecraft -> {
            ConfigManager.processReloads(true);
            // A safety net for the leave event: once there's no connection, the server's values have no business here.
            if (!OWN_VALUES.isEmpty() && minecraft.getConnection() == null) onDisconnect();
            if (pendingScreen != null) {
                Screen screen = pendingScreen.get();
                pendingScreen = null;
                if (screen != null) minecraft.setScreen(screen);
            }
            if (pendingMismatches != null) {
                if (minecraft.screen instanceof DisconnectedScreen disconnected) {
                    minecraft.setScreen(new StartupMismatchScreen(disconnected, pendingMismatches));
                    pendingMismatches = null;
                } else if (++mismatchTicks > MISMATCH_SCREEN_TIMEOUT) {
                    pendingMismatches = null;
                }
            }
        });
        ClientPlayerEvents.LEAVE.register(player -> onDisconnect());
        ClientCommandEvents.REGISTER.register((dispatcher, context) -> {
            LiteralArgumentBuilder<SharedSuggestionProvider> config = LiteralArgumentBuilder.literal("config");
            config.then(LiteralArgumentBuilder.<SharedSuggestionProvider>literal("open")
                    .executes(command -> {
                        pendingScreen = () -> ConfigScreens.createModList(null);
                        return 1;
                    })
                    .then(RequiredArgumentBuilder.<SharedSuggestionProvider, String>argument("mod", StringArgumentType.word())
                            .suggests((command, builder) -> SharedSuggestionProvider.suggest(ConfigManager.modIds(), builder))
                            .executes(command -> {
                                String modId = StringArgumentType.getString(command, "mod");
                                if (ConfigManager.forMod(modId).isEmpty()) {
                                    ClientCommandEvents.sendError(Component.translatableWithFallback("config.coolcatcore.command.no_config", "No such config"));
                                    return 0;
                                }
                                pendingScreen = () -> ConfigScreens.create(null, modId);
                                return 1;
                            })
                            // One category in a small window, e.g. "client/example_category".
                            .then(RequiredArgumentBuilder.<SharedSuggestionProvider, String>argument("category", StringArgumentType.greedyString())
                                    .suggests((command, builder) -> SharedSuggestionProvider.suggest(
                                            CategoryPopup.paths(StringArgumentType.getString(command, "mod")), builder))
                                    .executes(command -> {
                                        String modId = StringArgumentType.getString(command, "mod");
                                        String category = StringArgumentType.getString(command, "category");
                                        if (CategoryPopup.create(null, modId, category) == null) {
                                            ClientCommandEvents.sendError(Component.translatableWithFallback("config.coolcatcore.command.no_category",
                                                    "No such category: %s", category));
                                            return 0;
                                        }
                                        pendingScreen = () -> ConfigScreens.categoryPopup(null, modId, category);
                                        return 1;
                                    }))));
            ConfigCommands.build(config, found -> found.scope() == ConfigScope.CLIENT, new ConfigCommands.Feedback<>() {
                @Override
                public void success(SharedSuggestionProvider source, Component message, boolean broadcast) {
                    ClientCommandEvents.sendFeedback(message);
                }

                @Override
                public void failure(SharedSuggestionProvider source, Component message) {
                    ClientCommandEvents.sendError(message);
                }
            });
            dispatcher.register(LiteralArgumentBuilder.<SharedSuggestionProvider>literal("coolcatcore_client").then(config));
        });
    }

    /** Where a config's edits go from this client. */
    public enum Access {
        /** Changed and saved here. */
        LOCAL,
        /** Sent to the server, which applies and saves them. */
        REMOTE,
        /** The server's values, which this player may not change. */
        READ_ONLY,
        /** A world config with no world to hold it. */
        UNAVAILABLE
    }

    public static Access access(ConfigImpl config) {
        boolean inWorld = Minecraft.getInstance().getConnection() != null;
        return switch (config.scope()) {
            // A startup config is read from each side's own file, so it's always edited here, for the next start.
            case CLIENT, COMMON, STARTUP -> Access.LOCAL;
            case SERVER -> !inWorld ? Access.LOCAL : CAN_EDIT.getOrDefault(config.id(), false) ? Access.REMOTE : Access.READ_ONLY;
            case WORLD -> !inWorld || !CAN_EDIT.containsKey(config.id()) ? Access.UNAVAILABLE
                    : CAN_EDIT.get(config.id()) ? Access.REMOTE : Access.READ_ONLY;
        };
    }

    /** Whether the server sent its values of this config in this session. */
    public static boolean hasServerValues(ConfigImpl config) {
        return CAN_EDIT.containsKey(config.id());
    }

    public static void onSync(ConfigNetwork.SyncPayload payload) {
        ConfigImpl config = ConfigManager.get(payload.configId());
        if (config == null) {
            CoolCatCore.LOGGER.debug("The server synced config {}, which this client doesn't have", payload.configId());
            return;
        }
        CAN_EDIT.put(config.id(), payload.canEdit());
        Minecraft minecraft = Minecraft.getInstance();
        // The integrated server shares this game's config objects, so its values are already here.
        if (!minecraft.isLocalServer()) {
            JsonObject values;
            try {
                values = JsonParser.parseString(payload.values()).getAsJsonObject();
            } catch (JsonParseException | IllegalStateException e) {
                CoolCatCore.LOGGER.error("The server sent unreadable values for config {}", config.id(), e);
                return;
            }
            if (!config.isRemote()) {
                OWN_VALUES.put(config.id(), config.snapshot(true));
                config.setRemote(true);
            }
            ConfigImpl.SnapshotResult result = config.applySnapshot(values, false, true);
            if (!result.errors().isEmpty()) {
                CoolCatCore.LOGGER.warn("Some of the server's values for config {} were rejected: {}", config.id(),
                        result.errors().stream().map(Component::getString).toList());
            }
        }
        ConfigEvents.SYNCED.invoker().onSynced(config);
        if (minecraft.screen instanceof ConfigScreen screen) screen.onConfigSynced(config);
        if (minecraft.screen instanceof CategoryPopup popup) popup.onConfigSynced(config);
    }

    /** A startup value this client has but the server doesn't. */
    public record StartupMismatch(ConfigImpl config, ConfigValueImpl<?> value, Object serverValue) {
        public String format(Object value) {
            return this.formatValue(this.value, value);
        }

        @SuppressWarnings("unchecked")
        private <T> String formatValue(ConfigValueImpl<T> configValue, Object value) {
            return configValue.type().format((T) value);
        }
    }

    // Before joining: startup values that built content on both sides must be the same. A client that differs
    // disconnects, listing the differences, and is then offered to adopt the server's values.
    public static void onStartupCheck(ConfigNetwork.StartupCheckPayload payload, Consumer<Component> disconnect) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isLocalServer()) return;
        JsonObject configs;
        try {
            configs = JsonParser.parseString(payload.values()).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            CoolCatCore.LOGGER.error("The server sent unreadable startup values", e);
            return;
        }
        List<StartupMismatch> mismatches = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : configs.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            ConfigImpl config = id == null ? null : ConfigManager.get(id);
            if (config == null || config.scope() != ConfigScope.STARTUP || !entry.getValue().isJsonObject()) continue;
            for (Map.Entry<String, JsonElement> serverValue : entry.getValue().getAsJsonObject().entrySet()) {
                ConfigValueImpl<?> value = config.value(serverValue.getKey());
                if (value == null || !value.isSynced() || value.startupSync() != StartupSync.REQUIRE_MATCH) continue;
                ValidationResult<?> result = value.decode(serverValue.getValue());
                if (result.hasValue() && !value.sameValue(result.value(), value.get())) {
                    mismatches.add(new StartupMismatch(config, value, result.value()));
                }
            }
        }
        if (mismatches.isEmpty()) return;

        MutableComponent reason = Component.translatableWithFallback("config.coolcatcore.startup.mismatch", "This server's startup settings differ from yours:")
                .withStyle(ChatFormatting.GOLD);
        for (StartupMismatch mismatch : mismatches.subList(0, Math.min(mismatches.size(), MISMATCH_LINES))) {
            reason.append(Component.literal("\n• ").withStyle(ChatFormatting.GRAY))
                    .append(mismatch.value().displayName().copy().withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(": " + mismatch.format(mismatch.value().get()) + " → " + mismatch.format(mismatch.serverValue())).withStyle(ChatFormatting.GRAY));
        }
        if (mismatches.size() > MISMATCH_LINES) {
            reason.append(Component.literal("\n").append(Component.translatableWithFallback("config.coolcatcore.startup.more", "…and %s more", mismatches.size() - MISMATCH_LINES)));
        }
        CoolCatCore.LOGGER.warn("Startup config values differ from the server's: {}", mismatches.stream()
                .map(m -> m.config().id() + " " + m.value().path() + ": " + m.format(m.value().get()) + " -> " + m.format(m.serverValue())).toList());
        pendingMismatches = mismatches;
        mismatchTicks = 0;
        disconnect.accept(reason);
    }

    public static void onUpdateResult(ConfigNetwork.UpdateResultPayload payload) {
        ConfigImpl config = ConfigManager.get(payload.configId());
        Component name = config != null ? config.title() : Component.literal(payload.configId().toString());
        if (payload.accepted() && payload.errors().isEmpty()) {
            toast(Component.translatableWithFallback("config.coolcatcore.toast.server_saved", "Server config saved"), name);
        } else {
            payload.errors().forEach(error -> CoolCatCore.LOGGER.warn("The server rejected a change to config {}: {}", payload.configId(), error.getString()));
            toast(Component.translatableWithFallback("config.coolcatcore.toast.server_rejected", "The server rejected changes"),
                    payload.errors().isEmpty() ? name : payload.errors().getFirst());
        }
    }

    public static void sendUpdate(ConfigImpl config, JsonObject changes) {
        ConfigNetwork.UpdatePayload payload = new ConfigNetwork.UpdatePayload(config.id(), changes.toString());
        if (!payload.fits()) {
            toast(Component.translatableWithFallback("config.coolcatcore.toast.too_large", "Too many changes to send at once"), config.title());
            return;
        }
        Network.sendToServer(payload);
    }

    private static void onDisconnect() {
        if (!OWN_VALUES.isEmpty()) CoolCatCore.LOGGER.debug("Restoring this client's own values of {}", OWN_VALUES.keySet());
        OWN_VALUES.forEach((id, values) -> {
            ConfigImpl config = ConfigManager.get(id);
            if (config == null) return;
            config.applySnapshot(values, false, true);
            config.setRemote(false);
        });
        OWN_VALUES.clear();
        CAN_EDIT.clear();
    }

    /** Opens a screen next tick, once whatever is closing (the chat, after a command) has closed. */
    public static void openLater(Supplier<Screen> screen) {
        pendingScreen = screen;
    }

    public static void toast(Component title, @Nullable Component message) {
        SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, message);
    }
}
