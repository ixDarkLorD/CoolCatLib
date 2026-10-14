package net.ixdarklord.coolcatcore.internal.core;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.entity.player.Player;

import java.nio.file.Path;
import java.util.Optional;

// The loader's own facts. Public code goes through net.ixdarklord.coolcatcore.api.platform.Platform.
public interface ModPlatform {
    @ExpectPlatform
    static ModPlatform get() {
        throw new UnsupportedOperationException("This method has not been implemented in the loader.");
    }

    boolean isDevelopmentEnvironment();

    boolean isFabric();

    boolean isForge();

    boolean isNeoforge();

    boolean isForgeLike();

    boolean isModLoaded(String modId);

    Path getGameFolder();

    Path getConfigFolder();

    boolean isClient();

    boolean isFakePlayer(Player player);

    /** The mod's display name, if it's loaded. */
    Optional<String> getModName(String modId);

    /** The bytes of the mod's icon image (its logo on NeoForge), if it has one. */
    Optional<byte[]> readModIcon(String modId);
}
