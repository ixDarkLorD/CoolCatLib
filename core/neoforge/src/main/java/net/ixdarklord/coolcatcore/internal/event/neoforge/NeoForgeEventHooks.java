package net.ixdarklord.coolcatcore.internal.event.neoforge;

import net.ixdarklord.coolcatcore.api.event.v2.common.BlockEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.CommandEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.EntityEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.ServerLifecycleEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.ServerTickEvents;
import net.ixdarklord.coolcatcore.api.hooks.ServerLifecycleHooks;
import net.ixdarklord.coolcatcore.internal.platform.neoforge.NeoForgeTransferCompat;
import net.ixdarklord.coolcatcore.internal.platform.neoforge.NeoForgeRegistrations;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.world.level.Level;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

// Fires CoolCatLib: Core's common events from NeoForge's, and drains the registrations NeoForge takes through events.
public final class NeoForgeEventHooks {
    private static final String NETWORK_VERSION = "1";

    private NeoForgeEventHooks() {}

    public static void register(IEventBus modBus) {
        IEventBus bus = NeoForge.EVENT_BUS;

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

        bus.addListener((ServerTickEvent.Pre event) -> ServerTickEvents.START.invoker().onStartTick(event.getServer()));
        bus.addListener((ServerTickEvent.Post event) -> ServerTickEvents.END.invoker().onEndTick(event.getServer()));
        bus.addListener((LevelTickEvent.Pre event) -> {
            if (event.getLevel() instanceof ServerLevel level) ServerTickEvents.START_LEVEL.invoker().onStartLevelTick(level);
        });
        bus.addListener((LevelTickEvent.Post event) -> {
            if (event.getLevel() instanceof ServerLevel level) ServerTickEvents.END_LEVEL.invoker().onEndLevelTick(level);
        });

        bus.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) PlayerEvents.JOIN.invoker().onJoin(player);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) PlayerEvents.LEAVE.invoker().onLeave(player);
        });
        bus.addListener((PlayerTickEvent.Pre event) -> PlayerEvents.START_TICK.invoker().onStartTick(event.getEntity()));
        bus.addListener((PlayerTickEvent.Post event) -> PlayerEvents.END_TICK.invoker().onEndTick(event.getEntity()));

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

        bus.addListener((AddReloadListenerEvent event) ->
                NeoForgeRegistrations.SERVER_RELOAD_LISTENERS.values().forEach(event::addListener));

        modBus.addListener((RegisterPayloadHandlersEvent event) -> {
            PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
            NeoForgeRegistrations.PAYLOADS.forEach(registration -> registration.accept(registrar));
            NeoForgeRegistrations.PAYLOADS.clear();
            NeoForgeRegistrations.payloadsRegistered = true;
        });
        modBus.addListener(NeoForgeTransferCompat::register);
        modBus.addListener((RegisterConfigurationTasksEvent event) -> {
            if (!NeoForgeRegistrations.CONFIGURATION_PAYLOADS.isEmpty()) event.register(new ConfigurationPayloadsTask(event.getListener()));
        });
    }

    // Sends the configuration payloads and finishes at once; nothing waits for a reply.
    private record ConfigurationPayloadsTask(ServerConfigurationPacketListener listener) implements ICustomConfigurationTask {
        private static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(CoolCatCore.rl("configuration_payloads").toString());

        @Override
        public void run(Consumer<CustomPacketPayload> sender) {
            for (Supplier<List<CustomPacketPayload>> payloads : NeoForgeRegistrations.CONFIGURATION_PAYLOADS) {
                payloads.get().forEach(sender);
            }
            this.listener.finishCurrentTask(TYPE);
        }

        @Override
        public ConfigurationTask.Type type() {
            return TYPE;
        }
    }
}
