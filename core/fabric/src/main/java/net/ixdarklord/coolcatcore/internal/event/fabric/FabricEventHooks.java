package net.ixdarklord.coolcatcore.internal.event.fabric;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.BlockEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.CommandEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.EntityEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.ixdarklord.coolcatcore.api.hooks.ServerLifecycleHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// Fires CoolCatLib: Core's common events from Fabric's. Player ticks and block placing come from mixins (Fabric has no
// events for them): PlayerMixin, BlockItemMixin.
public final class FabricEventHooks {
    private FabricEventHooks() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ServerLifecycleHooks.updateServerState(server);
            net.ixdarklord.coolcatcore.api.event.v2.common.ServerLifecycleEvents.STARTING.invoker().onServerStarting(server);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                net.ixdarklord.coolcatcore.api.event.v2.common.ServerLifecycleEvents.STARTED.invoker().onServerStarted(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                net.ixdarklord.coolcatcore.api.event.v2.common.ServerLifecycleEvents.STOPPING.invoker().onServerStopping(server));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            net.ixdarklord.coolcatcore.api.event.v2.common.ServerLifecycleEvents.STOPPED.invoker().onServerStopped(server);
            ServerLifecycleHooks.updateServerState(null);
        });
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) ->
                net.ixdarklord.coolcatcore.api.event.v2.common.ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.invoker().onSyncDataPackContents(player, joined));

        ServerTickEvents.START_SERVER_TICK.register(server ->
                net.ixdarklord.coolcatcore.api.event.v2.common.ServerTickEvents.START.invoker().onStartTick(server));
        ServerTickEvents.END_SERVER_TICK.register(server ->
                net.ixdarklord.coolcatcore.api.event.v2.common.ServerTickEvents.END.invoker().onEndTick(server));
        ServerTickEvents.START_LEVEL_TICK.register(level ->
                net.ixdarklord.coolcatcore.api.event.v2.common.ServerTickEvents.START_LEVEL.invoker().onStartLevelTick(level));
        ServerTickEvents.END_LEVEL_TICK.register(level ->
                net.ixdarklord.coolcatcore.api.event.v2.common.ServerTickEvents.END_LEVEL.invoker().onEndLevelTick(level));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> PlayerEvents.JOIN.invoker().onJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PlayerEvents.LEAVE.invoker().onLeave(handler.player));

        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> EntityEvents.LOAD.invoker().onLoad(entity, level));

        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                !(level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer)
                        || !BlockEvents.BREAK.invoker().onBreak(serverLevel, pos, state, serverPlayer).isInterrupt());

        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                CommandEvents.REGISTER.invoker().register(dispatcher, context, selection));
    }
}
