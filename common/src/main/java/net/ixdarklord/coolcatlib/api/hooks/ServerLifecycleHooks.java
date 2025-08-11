package net.ixdarklord.coolcatlib.api.hooks;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * A utility class that helps track the current Minecraft server instance
 * during the server lifecycle. This allows easy access to the server instance
 * across your mod.
 */
public class ServerLifecycleHooks {

    // The current MinecraftServer instance, initially null until the server starts.
    @Nullable
    private static MinecraftServer currentServer;

    /**
     * Updates the current server instance.
     *
     * @param server The new server instance, or null if the server is stopping.
     */
    public static void updateServerState(@Nullable MinecraftServer server) {
        currentServer = server;
    }

    /**
     * Retrieves the current Minecraft server instance.
     *
     * @return The current server instance, or null if the server is not started.
     */
    @Nullable
    public static MinecraftServer getCurrentServer() {
        return currentServer;
    }

    /**
     * Checks if the current server instance is valid (not null).
     *
     * @return true if the server is available, false if not.
     */
    public static boolean isServerAvailable() {
        return currentServer != null;
    }
}