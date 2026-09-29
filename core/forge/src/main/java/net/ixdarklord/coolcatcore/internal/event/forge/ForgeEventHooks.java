package net.ixdarklord.coolcatcore.internal.event.forge;

import net.ixdarklord.coolcatcore.api.event.v2.common.BlockEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.CommandEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.EntityEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.ServerLifecycleEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.ServerTickEvents;
import net.ixdarklord.coolcatcore.api.hooks.ServerLifecycleHooks;
import net.ixdarklord.coolcatcore.internal.platform.forge.ForgeNetworking;
import net.ixdarklord.coolcatcore.internal.platform.forge.ForgeRegistrations;
import net.ixdarklord.coolcatcore.internal.platform.forge.ForgeTransferCompat;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.world.level.Level;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.network.GatherLoginConfigurationTasksEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.ConnectionType;
import net.minecraftforge.network.NetworkContext;
import net.minecraftforge.network.config.SimpleConfigurationTask;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

// Fires CoolCatLib: Core's common events from Forge's, and drains the registrations Forge takes through events.
public final class ForgeEventHooks {
    private static final ConfigurationTask.Type CONFIGURATION_PAYLOADS = new ConfigurationTask.Type(CoolCatCore.rl("configuration_payloads").toString());

    private ForgeEventHooks() {}

    public static void register() {
        IEventBus bus = MinecraftForge.EVENT_BUS;

        listen(bus, ServerStartingEvent.class, event -> {
            ServerLifecycleHooks.updateServerState(event.getServer());
            ServerLifecycleEvents.STARTING.invoker().onServerStarting(event.getServer());
        });
        listen(bus, ServerStartedEvent.class, event -> ServerLifecycleEvents.STARTED.invoker().onServerStarted(event.getServer()));
        listen(bus, ServerStoppingEvent.class, event -> ServerLifecycleEvents.STOPPING.invoker().onServerStopping(event.getServer()));
        listen(bus, ServerStoppedEvent.class, event -> {
            ServerLifecycleEvents.STOPPED.invoker().onServerStopped(event.getServer());
            ServerLifecycleHooks.updateServerState(null);
        });

        listen(bus, TickEvent.ServerTickEvent.Pre.class, event -> ServerTickEvents.START.invoker().onStartTick(event.getServer()));
        listen(bus, TickEvent.ServerTickEvent.Post.class, event -> ServerTickEvents.END.invoker().onEndTick(event.getServer()));
        listen(bus, TickEvent.LevelTickEvent.Pre.class, event -> {
            if (event.level instanceof ServerLevel level) ServerTickEvents.START_LEVEL.invoker().onStartLevelTick(level);
        });
        listen(bus, TickEvent.LevelTickEvent.Post.class, event -> {
            if (event.level instanceof ServerLevel level) ServerTickEvents.END_LEVEL.invoker().onEndLevelTick(level);
        });

        listen(bus, PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) PlayerEvents.JOIN.invoker().onJoin(player);
        });
        listen(bus, PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) PlayerEvents.LEAVE.invoker().onLeave(player);
        });
        listen(bus, TickEvent.PlayerTickEvent.Pre.class, event -> PlayerEvents.START_TICK.invoker().onStartTick(event.player));
        listen(bus, TickEvent.PlayerTickEvent.Post.class, event -> PlayerEvents.END_TICK.invoker().onEndTick(event.player));

        listen(bus, EntityJoinLevelEvent.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) EntityEvents.LOAD.invoker().onLoad(event.getEntity(), level);
        });

        listen(bus, BlockEvent.BreakEvent.class, event -> {
            if (event.getLevel() instanceof ServerLevel level && event.getPlayer() instanceof ServerPlayer player
                    && BlockEvents.BREAK.invoker().onBreak(level, event.getPos(), event.getState(), player).isInterrupt()) {
                event.setCanceled(true);
            }
        });
        listen(bus, BlockEvent.EntityPlaceEvent.class, event -> {
            if (event.getLevel() instanceof Level level && !level.isClientSide()) {
                BlockEvents.PLACED.invoker().onPlaced(level, event.getPos(), event.getPlacedBlock(), event.getEntity());
            }
        });

        listen(bus, RegisterCommandsEvent.class, event ->
                CommandEvents.REGISTER.invoker().register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection()));

        listen(bus, AddReloadListenerEvent.class, event -> {
            synchronized (ForgeRegistrations.SERVER_RELOAD_LISTENERS) {
                ForgeRegistrations.SERVER_RELOAD_LISTENERS.values().forEach(event::addListener);
            }
        });

        listen(bus, OnDatapackSyncEvent.class, event -> {
            // A player is set when they log in; after /reload it is null and every player is relevant.
            boolean joined = event.getPlayer() != null;
            List<ServerPlayer> players = joined ? List.of(event.getPlayer()) : event.getPlayers();
            players.forEach(player -> ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.invoker().onSyncDataPackContents(player, joined));
        });

        ForgeTransferCompat.register(bus);

        // After Forge's own tasks, so the client's channels are known by the time it runs.
        bus.addListener(EventPriority.LOWEST, false, GatherLoginConfigurationTasksEvent.class, event -> {
            if (ForgeRegistrations.CONFIGURATION_PAYLOADS.isEmpty() || NetworkContext.get(event.getConnection()).getType() != ConnectionType.MODDED) return;
            event.addTask(new SimpleConfigurationTask(CONFIGURATION_PAYLOADS, context -> sendConfigurationPayloads(context.getConnection())));
        });
    }

    // Sends the configuration payloads and finishes at once; nothing waits for a reply.
    private static void sendConfigurationPayloads(Connection connection) {
        for (Supplier<List<CustomPacketPayload>> payloads : ForgeRegistrations.CONFIGURATION_PAYLOADS) {
            for (CustomPacketPayload payload : payloads.get()) {
                if (ForgeNetworking.canSend(payload, connection)) ForgeNetworking.send(payload, connection);
            }
        }
    }

    static <T extends Event> void listen(IEventBus bus, Class<T> type, Consumer<T> listener) {
        bus.addListener(EventPriority.NORMAL, false, type, listener);
    }
}
