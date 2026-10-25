package net.ixdarklord.coolcatcore.internal.event;

import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// PlayerEvents.TRAVEL_TO_DIMENSION, asked from every way a player changes dimension on this version: teleportTo with
// another level (commands, ServerPlayerMixin), and changeDimension (portals, other mods' teleporters), whose signature
// differs by loader (vanilla's on Fabric, Forge's with an ITeleporter), so each loader's own ServerPlayerMixin asks.
public final class DimensionTravelHooks {
    private DimensionTravelHooks() {}

    /** Whether the player may go to {@code destination}; if not, they're given a portal cooldown. */
    public static boolean mayTravel(ServerPlayer player, ServerLevel destination) {
        if (player.isRemoved() || destination == player.level() || !PlayerEvents.TRAVEL_TO_DIMENSION.hasListeners()) return true;
        if (!PlayerEvents.TRAVEL_TO_DIMENSION.invoker().onTravelToDimension(player, player.level().dimension(), destination.dimension()).isInterrupt()) return true;
        // Standing in a portal, they'd be asked again every tick.
        player.setPortalCooldown();
        return false;
    }
}
