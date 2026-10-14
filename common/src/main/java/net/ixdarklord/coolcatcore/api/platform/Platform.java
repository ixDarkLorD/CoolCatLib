package net.ixdarklord.coolcatcore.api.platform;

import net.ixdarklord.coolcatcore.api.hooks.ServerLifecycleHooks;
import net.ixdarklord.coolcatcore.internal.core.ModPlatform;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Facts about the running game and loader, the same way on every loader.
 */
public final class Platform {
    private static final ModPlatform PLATFORM = ModPlatform.get();

    private Platform() {}

    public static Loader getLoader() {
        if (PLATFORM.isFabric()) return Loader.FABRIC;
        return PLATFORM.isForge() ? Loader.FORGE : Loader.NEOFORGE;
    }

    public static boolean isFabric() {
        return PLATFORM.isFabric();
    }

    public static boolean isForge() {
        return PLATFORM.isForge();
    }

    public static boolean isNeoForge() {
        return PLATFORM.isNeoforge();
    }

    public static boolean isModLoaded(String modId) {
        return PLATFORM.isModLoaded(modId);
    }

    /** A loaded mod's display name. */
    public static Optional<String> getModName(String modId) {
        return PLATFORM.getModName(modId);
    }

    public static boolean isDevelopmentEnvironment() {
        return PLATFORM.isDevelopmentEnvironment();
    }

    /** The game's folder ({@code .minecraft}, or the server's folder). */
    public static Path getGameFolder() {
        return PLATFORM.getGameFolder();
    }

    /** The {@code config} folder. */
    public static Path getConfigFolder() {
        return PLATFORM.getConfigFolder();
    }

    /** Which side this game is: a client (which may also run an integrated server) or a dedicated server. */
    public static Env getEnvironment() {
        return PLATFORM.isClient() ? Env.CLIENT : Env.SERVER;
    }

    public static boolean isClient() {
        return PLATFORM.isClient();
    }

    /**
     * Runs a task only on the client. The supplier keeps the task's (client-only) class from loading on a dedicated
     * server: {@code Platform.runOnClient(() -> ClientSetup::init)}.
     */
    public static void runOnClient(Supplier<Runnable> task) {
        if (isClient()) task.get().run();
    }

    /** The running server (dedicated or integrated), or null when there's none. */
    public static @Nullable MinecraftServer getServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    /** Whether the player is a mod's fake player (machines, deployers...) rather than a real one. */
    public static boolean isFakePlayer(Player player) {
        return PLATFORM.isFakePlayer(player);
    }

    public enum Loader {
        FABRIC,
        FORGE,
        NEOFORGE
    }
}
