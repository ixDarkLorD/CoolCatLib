package net.ixdarklord.coolcatcore.internal.event.neoforge;

import net.ixdarklord.coolcatcore.api.event.v2.common.BlockEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.CommandEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.EntityEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.InteractionEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.LivingEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.ServerLifecycleEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.ServerTickEvents;
import net.ixdarklord.coolcatcore.api.hooks.ServerLifecycleHooks;
import net.ixdarklord.coolcatcore.internal.platform.neoforge.NeoForgeTransferCompat;
import net.ixdarklord.coolcatcore.internal.platform.neoforge.NeoForgeRegistrations;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
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

// Fires CoolCatLib's common events from NeoForge's, and drains the registrations NeoForge takes through events.
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
        bus.addListener((OnDatapackSyncEvent event) -> {
            // A player is set when they log in; after /reload it is null and every player is relevant.
            boolean joined = event.getPlayer() != null;
            event.getRelevantPlayers().forEach(player -> ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.invoker().onSyncDataPackContents(player, joined));
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
        bus.addListener((EntityLeaveLevelEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level) EntityEvents.UNLOAD.invoker().onUnload(event.getEntity(), level);
        });

        bus.addListener((PlayerEvent.Clone event) -> {
            if (event.getOriginal() instanceof ServerPlayer original && event.getEntity() instanceof ServerPlayer player) {
                PlayerEvents.CLONE.invoker().onClone(original, player, event.isWasDeath());
            }
        });
        bus.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) PlayerEvents.RESPAWN.invoker().onRespawn(player, event.isEndConquered());
        });
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) PlayerEvents.CHANGE_DIMENSION.invoker().onChangeDimension(player, event.getFrom(), event.getTo());
        });

        bus.addListener((LivingIncomingDamageEvent event) -> {
            if (LivingEvents.DAMAGE.invoker().onDamage(event.getEntity(), event.getSource(), event.getAmount()).isInterrupt()) event.setCanceled(true);
        });
        bus.addListener((LivingDeathEvent event) -> {
            if (LivingEvents.DEATH.invoker().onDeath(event.getEntity(), event.getSource()).isInterrupt()) event.setCanceled(true);
        });

        bus.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            InteractionResult result = InteractionEvents.USE_BLOCK.invoker().onUseBlock(event.getEntity(), event.getHand(), event.getHitVec());
            if (result != InteractionResult.PASS) {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
        });
        bus.addListener((PlayerInteractEvent.RightClickItem event) -> {
            InteractionResult result = InteractionEvents.USE_ITEM.invoker().onUseItem(event.getEntity(), event.getHand());
            if (result != InteractionResult.PASS) {
                ((ICancellableEvent) event).setCanceled(true);
                event.setCancellationResult(result);
            }
        });
        bus.addListener((PlayerInteractEvent.EntityInteract event) -> {
            InteractionResult result = InteractionEvents.USE_ENTITY.invoker().onUseEntity(event.getEntity(), event.getHand(), event.getTarget());
            if (result != InteractionResult.PASS) {
                ((ICancellableEvent) event).setCanceled(true);
                event.setCancellationResult(result);
            }
        });
        // Fired again as the block is let go of or given up on; only the first click counts, as on Fabric.
        bus.addListener((PlayerInteractEvent.LeftClickBlock event) -> {
            if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START
                    && InteractionEvents.ATTACK_BLOCK.invoker().onAttackBlock(event.getEntity(), event.getHand(), event.getPos(), event.getFace()).isInterrupt()) {
                event.setCanceled(true);
            }
        });
        bus.addListener((AttackEntityEvent event) -> {
            if (InteractionEvents.ATTACK_ENTITY.invoker().onAttackEntity(event.getEntity(), event.getTarget()).isInterrupt()) {
                ((ICancellableEvent) event).setCanceled(true);
            }
        });

        registerBreakListener(bus);
        bus.addListener((BlockEvent.EntityPlaceEvent event) -> {
            if (event.getLevel() instanceof Level level && !level.isClientSide()) {
                BlockEvents.PLACED.invoker().onPlaced(level, event.getPos(), event.getPlacedBlock(), event.getEntity());
            }
        });

        bus.addListener((RegisterCommandsEvent event) ->
                CommandEvents.REGISTER.invoker().register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection()));

        bus.addListener((AddServerReloadListenersEvent event) ->
                NeoForgeRegistrations.SERVER_RELOAD_LISTENERS.forEach(event::addListener));

        modBus.addListener((RegisterPayloadHandlersEvent event) -> {
            // Optional, CoolCatLib's own included, so a client with CoolCatLib (and client-only mods built on it) can
            // join a server without it, and the other way round, as on Fabric: NeoForge otherwise disconnects whichever
            // side lacks a channel. Senders check canPlayerReceive first.
            PayloadRegistrar registrar = event.registrar(NETWORK_VERSION).optional();
            NeoForgeRegistrations.PAYLOADS.forEach(registration -> registration.accept(registrar));
            NeoForgeRegistrations.PAYLOADS.clear();
            NeoForgeRegistrations.payloadsRegistered = true;
        });
        modBus.addListener(NeoForgeTransferCompat::register);
        modBus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) ->
                NeoForgeRegistrations.ENTITY_ATTRIBUTES.forEach(registration -> registration.accept(event)));
        modBus.addListener((RegisterConfigurationTasksEvent event) -> {
            if (!NeoForgeRegistrations.CONFIGURATION_PAYLOADS.isEmpty()) event.register(new ConfigurationPayloadsTask(event.getListener()));
        });
    }

    // NeoForge 26.1.2 renamed BlockEvent.BreakEvent to block.BreakBlockEvent. Neither is named here, so one jar runs on
    // 26.1 through 26.1.2: whichever the running NeoForge has is listened to as a BlockEvent.
    private static final String[] BREAK_EVENTS = {
            "net.neoforged.neoforge.event.level.block.BreakBlockEvent",
            "net.neoforged.neoforge.event.level.BlockEvent$BreakEvent"
    };

    @SuppressWarnings("unchecked")
    private static void registerBreakListener(IEventBus bus) {
        for (String name : BREAK_EVENTS) {
            Class<? extends BlockEvent> type;
            MethodHandle getPlayer;
            try {
                type = Class.forName(name).asSubclass(BlockEvent.class);
                getPlayer = MethodHandles.publicLookup().unreflect(type.getMethod("getPlayer"));
            } catch (ReflectiveOperationException e) {
                continue;
            }
            bus.addListener(EventPriority.NORMAL, false, (Class<BlockEvent>) type, event -> {
                if (event.getLevel() instanceof ServerLevel level && player(getPlayer, event) instanceof ServerPlayer player
                        && BlockEvents.BREAK.invoker().onBreak(level, event.getPos(), event.getState(), player).isInterrupt()) {
                    ((ICancellableEvent) event).setCanceled(true);
                }
            });
            return;
        }
        CoolCatCore.LOGGER.error("No block break event found in this NeoForge; BlockEvents.BREAK won't fire");
    }

    private static Object player(MethodHandle getPlayer, BlockEvent event) {
        try {
            return getPlayer.invoke(event);
        } catch (Throwable e) {
            throw new IllegalStateException("Couldn't read the block break event's player", e);
        }
    }

    // Sends the configuration payloads the client can receive and finishes at once; nothing waits for a reply. A client
    // without CoolCatLib has none of its (optional) channels and is sent nothing.
    private static final class ConfigurationPayloadsTask implements ICustomConfigurationTask {
        private static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(CoolCatCore.rl("configuration_payloads").toString());

        private final ServerConfigurationPacketListener listener;

        private ConfigurationPayloadsTask(ServerConfigurationPacketListener listener) {
            this.listener = listener;
        }

        @Override
        public void run(Consumer<CustomPacketPayload> sender) {
            for (Supplier<List<CustomPacketPayload>> payloads : NeoForgeRegistrations.CONFIGURATION_PAYLOADS) {
                payloads.get().stream().filter(this.listener::hasChannel).forEach(sender);
            }
        }

        @Override
        public boolean tick() {
            return true;
        }

        @Override
        public ConfigurationTask.Type type() {
            return TYPE;
        }
    }
}
