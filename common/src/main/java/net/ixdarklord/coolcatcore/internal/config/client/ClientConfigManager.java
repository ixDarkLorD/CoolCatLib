package net.ixdarklord.coolcatcore.internal.config.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.brigadier.context.CommandContext;
import net.ixdarklord.coolcatcore.api.command.ModCommands;
import net.ixdarklord.coolcatcore.api.config.Config;
import net.ixdarklord.coolcatcore.api.config.ConfigEvents;
import net.ixdarklord.coolcatcore.api.config.ConfigScope;
import net.ixdarklord.coolcatcore.api.config.StartupSync;
import net.ixdarklord.coolcatcore.api.config.client.ConfigScreens;
import net.ixdarklord.coolcatcore.api.config.type.ValidationResult;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientCommandEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientPlayerEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientTickEvents;
import net.ixdarklord.coolcatcore.api.network.Network;
import net.ixdarklord.coolcatcore.api.platform.Platform;
import net.ixdarklord.coolcatcore.internal.config.ConfigCommands;
import net.ixdarklord.coolcatcore.internal.config.ConfigImpl;
import net.ixdarklord.coolcatcore.internal.config.ConfigManager;
import net.ixdarklord.coolcatcore.internal.config.ConfigValueImpl;
import net.ixdarklord.coolcatcore.internal.config.network.ConfigNetwork;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

// The client's side of synced configs: showing the server's values while connected (and restoring the client's own
// afterwards), sending a player's edits, and the client command. The screens are Glazed Menu's, when it's installed.
public final class ClientConfigManager {
    private static final Map<Identifier, JsonObject> OWN_VALUES = new HashMap<>();
    private static final Map<Identifier, Boolean> CAN_EDIT = new HashMap<>();
    private static final int MISMATCH_LINES = 6;
    private static final int MISMATCH_SCREEN_TIMEOUT = 400;
    private static @Nullable Supplier<Screen> pendingScreen;
    // Whether the server has CoolCatLib's config commands, which the client's own then leave to.
    private static boolean serverConfigCommands;
    // Offered once the disconnect a startup mismatch caused has shown its screen.
    private static @Nullable List<StartupMismatch> pendingMismatches;
    private static int mismatchTicks;
    // The screen offering to adopt a server's startup values, over the disconnect screen; Glazed Menu sets it.
    private static @Nullable BiFunction<Screen, List<StartupMismatch>, Screen> mismatchScreen;

    private ClientConfigManager() {}

    /** Called once by each loader's client setup. */
    public static void init() {
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
                if (mismatchScreen == null) {
                    pendingMismatches = null;
                } else if (minecraft.screen instanceof DisconnectedScreen disconnected) {
                    minecraft.setScreen(mismatchScreen.apply(disconnected, pendingMismatches));
                    pendingMismatches = null;
                } else if (++mismatchTicks > MISMATCH_SCREEN_TIMEOUT) {
                    pendingMismatches = null;
                }
            }
        });
        ClientPlayerEvents.LEAVE.register(player -> onDisconnect());
        // "/coolcatlib config" on the client, for servers without CoolCatLib, where the server's (which asks the client
        // to open a screen, and reloads the server's configs) isn't there. On a server with it, this one stands aside.
        ModCommands.registerClient(CoolCatCore.MOD_ID, "coolcatlib", (root, context) -> root.then(ConfigCommands.<SharedSuggestionProvider>anyMod(new ConfigCommands.Actions<>() {
                    @Override
                    public int open(CommandContext<SharedSuggestionProvider> command, @Nullable String modId, @Nullable String config) {
                        Component problem = ConfigCommands.problem(modId, config);
                        if (problem != null) {
                            ClientCommandEvents.sendError(problem);
                            return 0;
                        }
                        openScreen(modId, config);
                        return 1;
                    }

                    @Override
                    public int reload(CommandContext<SharedSuggestionProvider> command, @Nullable String modId, @Nullable String config) {
                        Component problem = ConfigCommands.problem(modId, config);
                        if (problem != null) {
                            ClientCommandEvents.sendError(problem);
                            return 0;
                        }
                        reloadHere(modId, config);
                        return 1;
                    }
                }, source -> true).requires(source -> !serverConfigCommands)));
    }

    /** Where a config's edits go from this client. */
    public enum Access {
        /** Changed and saved here. */
        LOCAL,
        /** Sent to the server, which applies and saves them. */
        REMOTE,
        /** The server's values, which this player may not change. */
        READ_ONLY,
        /** Not editable from here: there's no client (a dedicated server). */
        UNAVAILABLE
    }

    public static Access access(Config config) {
        boolean inWorld = Minecraft.getInstance().getConnection() != null;
        return switch (config.scope()) {
            // A startup config is read from each side's own file, so it's always edited here, for the next start.
            case CLIENT, COMMON, STARTUP -> Access.LOCAL;
            case SERVER -> !inWorld ? Access.LOCAL : CAN_EDIT.getOrDefault(config.id(), false) ? Access.REMOTE : Access.READ_ONLY;
        };
    }

    /** Whether the server sent its values of this config in this session. */
    public static boolean hasServerValues(Config config) {
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
            Identifier id = Identifier.tryParse(entry.getKey());
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

        MutableComponent reason = Component.translatableWithFallback("config.coolcatlib.startup.mismatch", "This server's startup settings differ from yours:")
                .withStyle(ChatFormatting.GOLD);
        for (StartupMismatch mismatch : mismatches.subList(0, Math.min(mismatches.size(), MISMATCH_LINES))) {
            reason.append(Component.literal("\n• ").withStyle(ChatFormatting.GRAY))
                    .append(mismatch.value().displayName().copy().withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(": " + mismatch.format(mismatch.value().get()) + " → " + mismatch.format(mismatch.serverValue())).withStyle(ChatFormatting.GRAY));
        }
        if (mismatches.size() > MISMATCH_LINES) {
            reason.append(Component.literal("\n").append(Component.translatableWithFallback("config.coolcatlib.startup.more", "…and %s more", mismatches.size() - MISMATCH_LINES)));
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
            toast(Component.translatableWithFallback("config.coolcatlib.toast.server_saved", "Server config saved"), name);
        } else {
            payload.errors().forEach(error -> CoolCatCore.LOGGER.warn("The server rejected a change to config {}: {}", payload.configId(), error.getString()));
            toast(Component.translatableWithFallback("config.coolcatlib.toast.server_rejected", "The server rejected changes"),
                    payload.errors().isEmpty() ? name : payload.errors().getFirst());
        }
    }

    public static void sendUpdate(ConfigImpl config, JsonObject changes) {
        ConfigNetwork.UpdatePayload payload = new ConfigNetwork.UpdatePayload(config.id(), changes.toString());
        if (!payload.fits()) {
            toast(Component.translatableWithFallback("config.coolcatlib.toast.too_large", "Too many changes to send at once"), config.title());
            return;
        }
        Network.sendToServer(payload);
    }

    private static void onDisconnect() {
        serverConfigCommands = false;
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

    /**
     * Sets the screen shown after a startup mismatch disconnected the player, over the disconnect screen. Without one,
     * the disconnect message (which lists the differences) is all.
     */
    public static void setMismatchScreen(BiFunction<Screen, List<StartupMismatch>, Screen> factory) {
        mismatchScreen = factory;
    }

    /** The server has CoolCatLib's config commands: the client's step aside until the player leaves. */
    public static void onServerCommands(ConfigNetwork.CommandsPayload payload) {
        serverConfigCommands = payload.configCommands();
    }

    /** A config reload command's client configs: reloaded from their files here. */
    public static void onReload(ConfigNetwork.ReloadPayload payload) {
        List<ConfigImpl> configs = ConfigCommands.named(payload.modId().isEmpty() ? null : payload.modId(), payload.config().isEmpty() ? null : payload.config());
        List<String> reloaded = ConfigCommands.reload(configs, true, Platform.getServer() != null);
        if (!reloaded.isEmpty() || payload.replyIfNone()) ClientCommandEvents.sendFeedback(ConfigCommands.reloaded(reloaded));
    }

    // The client command's reload: the configs kept on this thread now, and in singleplayer the world's too, on the
    // server thread, so a player without cheats can reload them as well. One reply once both are done.
    private static void reloadHere(@Nullable String modId, @Nullable String config) {
        List<ConfigImpl> configs = ConfigCommands.named(modId, config);
        MinecraftServer server = Platform.getServer();
        List<String> reloaded = new ArrayList<>(ConfigCommands.reload(configs, true, server != null));
        if (server == null) {
            ClientCommandEvents.sendFeedback(ConfigCommands.reloaded(reloaded));
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        server.submit(() -> ConfigCommands.reload(configs, false, true)).thenAccept(onServer -> minecraft.execute(() -> {
            reloaded.addAll(onServer);
            ClientCommandEvents.sendFeedback(ConfigCommands.reloaded(reloaded));
        }));
    }

    /** A config command's screen: every mod's configs, a mod's, or one of them. */
    public static void onOpenScreen(ConfigNetwork.OpenScreenPayload payload) {
        openScreen(payload.modId().isEmpty() ? null : payload.modId(), payload.config().isEmpty() ? null : payload.config());
    }

    private static void openScreen(@Nullable String modId, @Nullable String name) {
        Config config = modId == null || name == null ? null
                : ConfigManager.forMod(modId).stream().filter(found -> found.name().equals(name)).findFirst().orElse(null);
        Screen screen = modId == null ? ConfigScreens.createModList(null)
                : config != null ? ConfigScreens.create(null, config) : ConfigScreens.create(null, modId);
        if (screen != null) {
            openLater(() -> screen);
        } else if (config != null) {
            ConfigScreens.tellUnavailable(config);
        } else {
            ClientCommandEvents.sendError(Component.translatableWithFallback("config.coolcatlib.command.no_screens",
                    "There are no config screens to open"));
        }
    }

    /** Opens a screen next tick, once whatever is closing (the chat, after a command) has closed. */
    public static void openLater(Supplier<Screen> screen) {
        pendingScreen = screen;
    }

    // The widest a toast's message line gets before it wraps.
    private static final int TOAST_LINE_WIDTH = 170;

    /**
     * A notice in the corner: a short title (usually the config's name) over a message, which wraps so it never runs
     * past the toast. It replaces the previous one.
     */
    public static void toast(Component title, @Nullable Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        ToastManager toasts = minecraft.getToastManager();
        SystemToast.SystemToastId id = SystemToast.SystemToastId.PERIODIC_NOTIFICATION;
        SystemToast.forceHide(toasts, id);
        // Sized to its text, like vanilla's own; only a message too long for one line wraps (in a wider toast).
        boolean fits = message == null || minecraft.font.width(message) <= TOAST_LINE_WIDTH;
        toasts.addToast(fits ? new SystemToast(id, title, message) : SystemToast.multiline(minecraft, id, title, message));
    }
}
