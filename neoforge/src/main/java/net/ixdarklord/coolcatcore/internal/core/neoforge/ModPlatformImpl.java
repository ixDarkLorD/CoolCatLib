package net.ixdarklord.coolcatcore.internal.core.neoforge;

import net.ixdarklord.coolcatcore.internal.core.ModPlatform;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
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
        return !FMLEnvironment.production;
    }

    @Override
    public boolean isFabric() {
        return false;
    }

    @Override
    public boolean isForge() {
        return false;
    }

    @Override
    public boolean isNeoforge() {
        return true;
    }

    @Override
    public boolean isForgeLike() {
        return true;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public Path getGameFolder() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public Path getConfigFolder() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    @Override
    public boolean isFakePlayer(Player player) {
        return player.isFakePlayer();
    }

    @Override
    public Optional<String> getModName(String modId) {
        return ModList.get().getModContainerById(modId).map(container -> container.getModInfo().getDisplayName());
    }

    @Override
    public Optional<byte[]> readModIcon(String modId) {
        return ModList.get().getModContainerById(modId).flatMap(container -> container.getModInfo().getLogoFile().flatMap(logo -> {
            Path path = container.getModInfo().getOwningFile().getFile().findResource(logo);
            if (!Files.isRegularFile(path)) return Optional.empty();
            try {
                return Optional.of(Files.readAllBytes(path));
            } catch (IOException e) {
                return Optional.empty();
            }
        }));
    }
}
