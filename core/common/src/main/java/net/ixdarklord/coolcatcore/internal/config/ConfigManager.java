package net.ixdarklord.coolcatcore.internal.config;

import net.ixdarklord.coolcatcore.api.config.ConfigScope;
import net.ixdarklord.coolcatcore.api.event.v2.common.CommandEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.ServerLifecycleEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.ServerTickEvents;
import net.ixdarklord.coolcatcore.api.platform.Platform;
import net.ixdarklord.coolcatcore.internal.config.network.ConfigNetwork;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Every registered config: loading their files, hot reloading, and queueing
// synced configs for sending once per tick.
public final class ConfigManager {
    private static final Map<Identifier, ConfigImpl> CONFIGS = new LinkedHashMap<>();
    private static final Set<ConfigImpl> PENDING_RELOAD = ConcurrentHashMap.newKeySet();
    private static final Set<ConfigImpl> PENDING_SYNC = ConcurrentHashMap.newKeySet();
    private static @Nullable ConfigFileWatcher watcher;
    private static boolean watcherStarted;

    private ConfigManager() {}

    /** Called once by each loader's common setup. */
    public static void init() {
        ConfigNetwork.init();
        ServerLifecycleEvents.STOPPED.register(server -> PENDING_SYNC.clear());
        ServerTickEvents.END.register(server -> {
            processReloads(false);
            flushSyncs();
        });
        PlayerEvents.JOIN.register(ConfigNetwork::sendAll);
        CommandEvents.REGISTER.register((dispatcher, context, selection) -> ConfigCommands.registerServer(dispatcher));
    }

    public static void register(ConfigImpl config) {
        synchronized (CONFIGS) {
            if (CONFIGS.putIfAbsent(config.id(), config) != null) {
                throw new IllegalArgumentException("A config with the id " + config.id() + " is already registered");
            }
        }
        // A dedicated server keeps client configs at their defaults, without a file.
        if (config.scope() == ConfigScope.CLIENT && !Platform.isClient()) return;
        bindAndLoad(config, Platform.getConfigFolder().resolve(config.fileName()));
        // Content is created from startup values right after this, so they're fixed from now on.
        if (config.scope() == ConfigScope.STARTUP) config.freeze();
    }

    public static @Nullable ConfigImpl get(Identifier id) {
        synchronized (CONFIGS) {
            return CONFIGS.get(id);
        }
    }

    public static List<ConfigImpl> all() {
        synchronized (CONFIGS) {
            return new ArrayList<>(CONFIGS.values());
        }
    }

    public static List<ConfigImpl> forMod(String modId) {
        return all().stream().filter(config -> config.modId().equals(modId)).toList();
    }

    /** Mods with at least one registered config. */
    public static Set<String> modIds() {
        Set<String> ids = new LinkedHashSet<>();
        all().forEach(config -> ids.add(config.modId()));
        return ids;
    }

    private static synchronized void bindAndLoad(ConfigImpl config, Path file) {
        config.bindFile(file);
        config.load();
        if (config.hotReload()) {
            if (!watcherStarted) {
                watcherStarted = true;
                watcher = ConfigFileWatcher.start();
            }
            if (watcher != null) watcher.watch(file, config);
        }
    }

    // --- Hot reloading ---

    static void queueReload(ConfigImpl config) {
        PENDING_RELOAD.add(config);
    }

    /**
     * Reloads configs whose files changed. Client configs reload on the client thread; the rest on the server
     * thread, or on the client thread while no server runs.
     */
    public static void processReloads(boolean clientThread) {
        if (PENDING_RELOAD.isEmpty()) return;
        boolean serverRunning = Platform.getServer() != null;
        for (Iterator<ConfigImpl> iterator = PENDING_RELOAD.iterator(); iterator.hasNext(); ) {
            ConfigImpl config = iterator.next();
            boolean clientSide = config.scope() == ConfigScope.CLIENT || !serverRunning;
            if (clientSide != clientThread) continue;
            iterator.remove();
            Path file = config.filePath();
            if (file == null || config.isRemote() || Files.notExists(file)) continue;
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                if (config.isExternalChange(text)) {
                    CoolCatCore.LOGGER.info("Reloading config {}: its file changed", config.id());
                    config.readText(text, false);
                }
            } catch (IOException e) {
                // Likely still being written; the next write event queues it again.
                CoolCatCore.LOGGER.debug("Couldn't read config {} yet", config.id(), e);
            }
        }
    }

    // --- Syncing ---

    static void onChanged(ConfigImpl config) {
        if (config.scope().isSynced() && !config.isRemote() && Platform.getServer() != null) PENDING_SYNC.add(config);
    }

    private static void flushSyncs() {
        if (PENDING_SYNC.isEmpty()) return;
        MinecraftServer server = Platform.getServer();
        for (Iterator<ConfigImpl> iterator = PENDING_SYNC.iterator(); iterator.hasNext(); ) {
            ConfigImpl config = iterator.next();
            iterator.remove();
            if (server != null) ConfigNetwork.broadcast(server, config);
        }
    }
}
