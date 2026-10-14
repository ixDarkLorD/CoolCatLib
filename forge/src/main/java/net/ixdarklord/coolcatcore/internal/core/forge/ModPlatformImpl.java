package net.ixdarklord.coolcatcore.internal.core.forge;

import net.ixdarklord.coolcatcore.internal.core.ModPlatform;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

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
        return true;
    }

    @Override
    public boolean isNeoforge() {
        return false;
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

    // Forge 1.21.1 has no FakePlayer class; mods' fake players are server players the player list doesn't know.
    @Override
    public boolean isFakePlayer(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        MinecraftServer server = serverPlayer.getServer();
        return server != null && server.getPlayerList().getPlayer(serverPlayer.getUUID()) != serverPlayer;
    }

    @Override
    public Optional<String> getModName(String modId) {
        return ModList.get().getModContainerById(modId).map(container -> container.getModInfo().getDisplayName());
    }

    @Override
    public Optional<byte[]> readModIcon(String modId) {
        return ModList.get().getModContainerById(modId).flatMap(container -> container.getModInfo().getLogoFile().flatMap(logo -> {
            Path path = container.getModInfo().getOwningFile().getFile().findResource(logo);
            if (path == null || !Files.isRegularFile(path)) return Optional.empty();
            try {
                return Optional.of(Files.readAllBytes(path));
            } catch (IOException e) {
                return Optional.empty();
            }
        }));
    }
}
