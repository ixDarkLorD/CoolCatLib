package net.ixdarklord.coolcatcore.internal.config.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.ixdarklord.coolcatcore.api.config.ConfigScope;
import net.ixdarklord.coolcatcore.api.config.ConfigValue;
import net.ixdarklord.coolcatcore.api.config.StartupSync;
import net.ixdarklord.coolcatcore.api.network.Network;
import net.ixdarklord.coolcatcore.internal.config.ConfigImpl;
import net.ixdarklord.coolcatcore.internal.config.ConfigJson;
import net.ixdarklord.coolcatcore.internal.config.ConfigManager;
import net.ixdarklord.coolcatcore.internal.config.ConfigValueImpl;
import net.ixdarklord.coolcatcore.internal.config.client.ClientConfigManager;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.ixdarklord.coolcatcore.internal.platform.CommonServices;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Synced configs travel as flat JSON objects (dotted path -> value encoded by its codec): the server sends its values
// on join and after every change; a permitted player sends the values they changed.
public final class ConfigNetwork {
    // Serverbound custom payloads are capped at 32 KiB by vanilla; clientbound ones at 1 MiB.
    private static final int MAX_UPDATE_LENGTH = 30_000;
    private static final int MAX_SYNC_LENGTH = 500_000;

    private ConfigNetwork() {}

    public static void init() {
        Network.registerClientbound(SyncPayload.TYPE, SyncPayload.STREAM_CODEC, (payload, context) -> ClientConfigManager.onSync(payload));
        Network.registerClientbound(UpdateResultPayload.TYPE, UpdateResultPayload.STREAM_CODEC, (payload, context) -> ClientConfigManager.onUpdateResult(payload));
        Network.registerServerbound(UpdatePayload.TYPE, UpdatePayload.STREAM_CODEC, (payload, context) -> {
            if (context.getPlayer() instanceof ServerPlayer player) handleUpdate(payload, player);
        });
        CommonServices.get().registerConfigurationClientbound(StartupCheckPayload.TYPE, StartupCheckPayload.STREAM_CODEC,
                (payload, disconnect) -> ClientConfigManager.onStartupCheck(payload, disconnect));
        CommonServices.get().addConfigurationPayloads(ConfigNetwork::startupCheck);
    }

    // The startup values clients must match, sent before they join so a mismatch never reaches the world.
    private static List<CustomPacketPayload> startupCheck() {
        JsonObject configs = new JsonObject();
        for (ConfigImpl config : ConfigManager.all()) {
            if (config.scope() != ConfigScope.STARTUP || !config.isLoaded()) continue;
            JsonObject values = new JsonObject();
            for (ConfigValueImpl<?> value : config.allValues()) {
                if (value.isSynced() && value.startupSync() == StartupSync.REQUIRE_MATCH) values.add(value.path(), value.encodeCurrent());
            }
            if (!values.isEmpty()) configs.add(config.id().toString(), values);
        }
        return configs.isEmpty() ? List.of() : List.of(new StartupCheckPayload(configs.toString()));
    }

    /** Whether the player may change the config: the singleplayer host always may, others need its edit permission. */
    public static boolean canEdit(ServerPlayer player, ConfigImpl config) {
        MinecraftServer server = player.server;
        return server.isSingleplayerOwner(player.getGameProfile()) || player.hasPermissions(config.editPermission());
    }

    public static void sendAll(ServerPlayer player) {
        for (ConfigImpl config : ConfigManager.all()) {
            if (config.scope().isSynced() && config.isLoaded()) send(player, config);
        }
    }

    public static void broadcast(MinecraftServer server, ConfigImpl config) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) send(player, config);
    }

    private static void send(ServerPlayer player, ConfigImpl config) {
        // Clients without CoolCatLib: Core and GameTest mock players have no channel for it (NeoForge throws on send).
        if (!Network.canPlayerReceive(player, SyncPayload.TYPE)) return;
        String values = ConfigJson.compact(config.syncSnapshot());
        if (values.length() > MAX_SYNC_LENGTH) {
            CoolCatCore.LOGGER.error("Config {} is too large to sync ({} characters)", config.id(), values.length());
            return;
        }
        Network.sendToPlayer(player, new SyncPayload(config.id(), values, canEdit(player, config)));
    }

    private static void handleUpdate(UpdatePayload payload, ServerPlayer player) {
        ConfigImpl config = ConfigManager.get(payload.configId());
        if (config == null || !config.scope().isSynced() || !config.isLoaded()) {
            reply(player, payload.configId(), false, List.of(Component.translatableWithFallback("config.coolcatcore.error.unavailable_server", "That config isn't available on this server")));
            return;
        }
        if (!canEdit(player, config)) {
            CoolCatCore.LOGGER.warn("{} tried to change config {} without permission", player.getName().getString(), config.id());
            reply(player, config.id(), false, List.of(Component.translatableWithFallback("config.coolcatcore.error.permission", "You don't have permission to change this config")));
            return;
        }
        JsonObject changes;
        try {
            JsonElement json = JsonParser.parseString(payload.values());
            changes = json.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            reply(player, config.id(), false, List.of(Component.translatableWithFallback("config.coolcatcore.error.json", "Not valid JSON")));
            return;
        }

        // Server-only values are never shown to other players, so only the host of a singleplayer world edits them.
        List<Component> errors = new ArrayList<>();
        boolean host = player.server.isSingleplayerOwner(player.getGameProfile());
        for (Map.Entry<String, JsonElement> entry : List.copyOf(changes.entrySet())) {
            ConfigValueImpl<?> value = config.value(entry.getKey());
            if (value != null && !value.isSynced() && !host) {
                changes.remove(entry.getKey());
                errors.add(Component.translatableWithFallback("config.coolcatcore.error.server_only", "%s can only be changed on the server", entry.getKey()));
            }
        }

        ConfigImpl.SnapshotResult result = config.applySnapshot(changes, true);
        errors.addAll(result.errors());
        if (!result.changed().isEmpty()) {
            config.save();
            CoolCatCore.LOGGER.info("{} changed config {}: {}", player.getName().getString(), config.id(),
                    result.changed().stream().map(ConfigValue::path).collect(Collectors.joining(", ")));
        }
        reply(player, config.id(), true, errors);
    }

    private static void reply(ServerPlayer player, ResourceLocation configId, boolean accepted, List<Component> errors) {
        Network.sendToPlayer(player, new UpdateResultPayload(configId, accepted, errors.subList(0, Math.min(errors.size(), 64))));
    }

    /** The server's values of a synced config, and whether the receiving player may edit them. */
    public record SyncPayload(ResourceLocation configId, String values, boolean canEdit) implements CustomPacketPayload {
        public static final Type<SyncPayload> TYPE = new Type<>(CoolCatCore.rl("config_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, SyncPayload::configId,
                ByteBufCodecs.stringUtf8(MAX_SYNC_LENGTH), SyncPayload::values,
                ByteBufCodecs.BOOL, SyncPayload::canEdit,
                SyncPayload::new);

        @Override
        public Type<SyncPayload> type() {
            return TYPE;
        }
    }

    /** The startup values of every startup config that clients must match: {@code {"mod:config": {path: value}}}. */
    public record StartupCheckPayload(String values) implements CustomPacketPayload {
        public static final Type<StartupCheckPayload> TYPE = new Type<>(CoolCatCore.rl("config_startup_check"));
        public static final StreamCodec<FriendlyByteBuf, StartupCheckPayload> STREAM_CODEC =
                ByteBufCodecs.stringUtf8(MAX_SYNC_LENGTH).map(StartupCheckPayload::new, StartupCheckPayload::values).cast();

        @Override
        public Type<StartupCheckPayload> type() {
            return TYPE;
        }
    }

    /** The values a player changed in a synced config. */
    public record UpdatePayload(ResourceLocation configId, String values) implements CustomPacketPayload {
        public static final Type<UpdatePayload> TYPE = new Type<>(CoolCatCore.rl("config_update"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UpdatePayload> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, UpdatePayload::configId,
                ByteBufCodecs.stringUtf8(MAX_UPDATE_LENGTH), UpdatePayload::values,
                UpdatePayload::new);

        /** Whether the payload fits in a serverbound packet. */
        public boolean fits() {
            return this.values.length() <= MAX_UPDATE_LENGTH;
        }

        @Override
        public Type<UpdatePayload> type() {
            return TYPE;
        }
    }

    /** Whether the server took a player's changes, and what it rejected. */
    public record UpdateResultPayload(ResourceLocation configId, boolean accepted, List<Component> errors) implements CustomPacketPayload {
        public static final Type<UpdateResultPayload> TYPE = new Type<>(CoolCatCore.rl("config_update_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UpdateResultPayload> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, UpdateResultPayload::configId,
                ByteBufCodecs.BOOL, UpdateResultPayload::accepted,
                ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs.list(64)), UpdateResultPayload::errors,
                UpdateResultPayload::new);

        @Override
        public Type<UpdateResultPayload> type() {
            return TYPE;
        }
    }
}
