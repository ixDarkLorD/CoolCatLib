package net.ixdarklord.coolcatcore.internal.core.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.loader.api.FabricLoader;
import net.ixdarklord.coolcatcore.internal.core.ModPlatform;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class ModPlatformImpl implements ModPlatform {
    private static final ModPlatform INSTANCE = new ModPlatformImpl();

    public static ModPlatform get() {
        return INSTANCE;
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public boolean isFabric() {
        return true;
    }

    @Override
    public boolean isForge() {
        return false;
    }

    @Override
    public boolean isNeoforge() {
        return false;
    }

    @Override
    public boolean isForgeLike() {
        return false;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public Path getGameFolder() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path getConfigFolder() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public boolean isFakePlayer(Player player) {
        return player instanceof FakePlayer;
    }

    @Override
    public Optional<String> getModName(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).map(container -> container.getMetadata().getName());
    }

    @Override
    public Optional<byte[]> readModIcon(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .flatMap(container -> container.getMetadata().getIconPath(64).flatMap(container::findPath))
                .flatMap(path -> {
                    try {
                        return Optional.of(Files.readAllBytes(path));
                    } catch (IOException e) {
                        return Optional.empty();
                    }
                });
    }
}
