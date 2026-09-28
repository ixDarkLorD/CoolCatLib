package net.ixdarklord.coolcatcore.internal.config;

import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Watches the folders holding config files, on a daemon thread, and queues a reload when one is written. Whether the
// text really changed (rather than being our own save) is decided when the reload runs, on a game thread.
final class ConfigFileWatcher implements Runnable {
    private final WatchService service;
    private final Map<Path, ConfigImpl> files = new ConcurrentHashMap<>();
    private final Map<Path, WatchKey> folders = new ConcurrentHashMap<>();

    private ConfigFileWatcher(WatchService service) {
        this.service = service;
    }

    static @Nullable ConfigFileWatcher start() {
        try {
            ConfigFileWatcher watcher = new ConfigFileWatcher(FileSystems.getDefault().newWatchService());
            Thread thread = new Thread(watcher, "CoolCatLib: Core Config Watcher");
            thread.setDaemon(true);
            thread.start();
            return watcher;
        } catch (IOException | UnsupportedOperationException e) {
            CoolCatCore.LOGGER.warn("Config files can't be watched; edits made while the game runs need /coolcatcore config <id> reload", e);
            return null;
        }
    }

    synchronized void watch(Path file, ConfigImpl config) {
        Path path = file.toAbsolutePath().normalize();
        Path folder = path.getParent();
        this.files.put(path, config);
        if (this.folders.containsKey(folder)) return;
        try {
            Files.createDirectories(folder);
            this.folders.put(folder, folder.register(this.service, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY));
        } catch (IOException e) {
            CoolCatCore.LOGGER.warn("Couldn't watch {} for config changes", folder, e);
        }
    }

    synchronized void unwatch(Path file) {
        Path path = file.toAbsolutePath().normalize();
        Path folder = path.getParent();
        this.files.remove(path);
        if (this.files.keySet().stream().noneMatch(other -> other.getParent().equals(folder))) {
            WatchKey key = this.folders.remove(folder);
            if (key != null) key.cancel();
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                WatchKey key = this.service.take();
                Path folder = (Path) key.watchable();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        this.files.forEach((path, config) -> {
                            if (path.getParent().equals(folder)) ConfigManager.queueReload(config);
                        });
                    } else if (event.context() instanceof Path name) {
                        ConfigImpl config = this.files.get(folder.resolve(name).toAbsolutePath().normalize());
                        if (config != null) ConfigManager.queueReload(config);
                    }
                }
                key.reset();
            }
        } catch (InterruptedException | ClosedWatchServiceException e) {
            // Shutting down.
        }
    }
}
