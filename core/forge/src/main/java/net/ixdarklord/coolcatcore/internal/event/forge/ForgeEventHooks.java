package net.ixdarklord.coolcatcore.internal.event.forge;

import net.ixdarklord.coolcatcore.api.event.v2.common.BlockEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.CommandEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.EntityEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.ServerLifecycleEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.ServerTickEvents;
import net.ixdarklord.coolcatcore.api.hooks.ServerLifecycleHooks;
import net.ixdarklord.coolcatcore.internal.platform.forge.ForgeRegistrations;
import net.ixdarklord.coolcatcore.internal.platform.forge.ForgeTransferCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;

// Fires CoolCatLib: Core's common events from Forge's, and hooks up the registrations Forge takes through events.
public final class ForgeEventHooks {
    private ForgeEventHooks() {}

    public static void register(IEventBus modBus) {
        IEventBus bus = MinecraftForge.EVENT_BUS;

        bus.addListener((ServerStartingEvent event) -> {
            ServerLifecycleHooks.updateServerState(event.getServer());
            ServerLifecycleEvents.STARTING.invoker().onServerStarting(event.getServer());
        });
        bus.addListener((ServerStartedEvent event) -> ServerLifecycleEvents.STARTED.invoker().onServerStarted(event.getServer()));
        bus.addListener((ServerStoppingEvent event) -> ServerLifecycleEvents.STOPPING.invoker().onServerStopping(event.getServer()));
        bus.addListener((ServerStoppedEvent event) -> {
            ServerLifecycleEvents.STOPPED.invoker().onServerStopped(event.getServer());
            ServerLifecycleHooks.updateServerState(null);
        });

        bus.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.START) ServerTickEvents.START.invoker().onStartTick(event.getServer());
            else ServerTickEvents.END.invoker().onEndTick(event.getServer());
        });
        bus.addListener((TickEvent.LevelTickEvent event) -> {
            if (!(event.level instanceof ServerLevel level)) return;
            if (event.phase == TickEvent.Phase.START) ServerTickEvents.START_LEVEL.invoker().onStartLevelTick(level);
            else ServerTickEvents.END_LEVEL.invoker().onEndLevelTick(level);
        });

        bus.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) PlayerEvents.JOIN.invoker().onJoin(player);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) PlayerEvents.LEAVE.invoker().onLeave(player);
        });
        bus.addListener((TickEvent.PlayerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.START) PlayerEvents.START_TICK.invoker().onStartTick(event.player);
            else PlayerEvents.END_TICK.invoker().onEndTick(event.player);
        });

        bus.addListener((EntityJoinLevelEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level) EntityEvents.LOAD.invoker().onLoad(event.getEntity(), level);
        });

        bus.addListener((BlockEvent.BreakEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level && event.getPlayer() instanceof ServerPlayer player
                    && BlockEvents.BREAK.invoker().onBreak(level, event.getPos(), event.getState(), player).isInterrupt()) {
                event.setCanceled(true);
            }
        });
        bus.addListener((BlockEvent.EntityPlaceEvent event) -> {
            if (event.getLevel() instanceof Level level && !level.isClientSide()) {
                BlockEvents.PLACED.invoker().onPlaced(level, event.getPos(), event.getPlacedBlock(), event.getEntity());
            }
        });

        bus.addListener((RegisterCommandsEvent event) ->
                CommandEvents.REGISTER.invoker().register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection()));

        ForgeRegistrations.register(modBus);
        ForgeTransferCompat.register(bus);
    }
}
