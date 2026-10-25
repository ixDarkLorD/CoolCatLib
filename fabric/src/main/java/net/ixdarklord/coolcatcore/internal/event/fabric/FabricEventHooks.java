package net.ixdarklord.coolcatcore.internal.event.fabric;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.BlockEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.CommandEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.EntityEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.InteractionEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.ixdarklord.coolcatcore.api.hooks.ServerLifecycleHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;

// Fires CoolCatLib's common events from Fabric's. Player ticks and block placing come from mixins (Fabric has no
// events for them): PlayerMixin, BlockItemMixin; and travel to another dimension from ServerPlayerTravelMixin.
public final class FabricEventHooks {
    private FabricEventHooks() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ServerLifecycleHooks.updateServerState(server);
            net.ixdarklord.coolcatcore.api.event.v2.common.ServerLifecycleEvents.STARTING.invoker().onServerStarting(server);
        });
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) ->
                net.ixdarklord.coolcatcore.api.event.v2.common.ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.invoker().onSyncDataPackContents(player, joined));
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                net.ixdarklord.coolcatcore.api.event.v2.common.ServerLifecycleEvents.STARTED.invoker().onServerStarted(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                net.ixdarklord.coolcatcore.api.event.v2.common.ServerLifecycleEvents.STOPPING.invoker().onServerStopping(server));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            net.ixdarklord.coolcatcore.api.event.v2.common.ServerLifecycleEvents.STOPPED.invoker().onServerStopped(server);
            ServerLifecycleHooks.updateServerState(null);
        });

        ServerTickEvents.START_SERVER_TICK.register(server ->
                net.ixdarklord.coolcatcore.api.event.v2.common.ServerTickEvents.START.invoker().onStartTick(server));
        ServerTickEvents.END_SERVER_TICK.register(server ->
                net.ixdarklord.coolcatcore.api.event.v2.common.ServerTickEvents.END.invoker().onEndTick(server));
        ServerTickEvents.START_WORLD_TICK.register(level ->
                net.ixdarklord.coolcatcore.api.event.v2.common.ServerTickEvents.START_LEVEL.invoker().onStartLevelTick(level));
        ServerTickEvents.END_WORLD_TICK.register(level ->
                net.ixdarklord.coolcatcore.api.event.v2.common.ServerTickEvents.END_LEVEL.invoker().onEndLevelTick(level));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> PlayerEvents.JOIN.invoker().onJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PlayerEvents.LEAVE.invoker().onLeave(handler.player));

        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> EntityEvents.LOAD.invoker().onLoad(entity, level));

        // "alive" is true for a player coming back from the End, false after death.
        ServerPlayerEvents.COPY_FROM.register((original, player, alive) -> PlayerEvents.CLONE.invoker().onClone(original, player, !alive));
        ServerPlayerEvents.AFTER_RESPAWN.register((original, player, alive) -> PlayerEvents.RESPAWN.invoker().onRespawn(player, alive));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, from, to) ->
                PlayerEvents.CHANGE_DIMENSION.invoker().onChangeDimension(player, from.dimension(), to.dimension()));

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> InteractionEvents.USE_BLOCK.invoker().onUseBlock(player, hand, hit));
        // Fabric's item use answers with the stack held: the same stack, with the event's result.
        UseItemCallback.EVENT.register((player, level, hand) ->
                new InteractionResultHolder<>(InteractionEvents.USE_ITEM.invoker().onUseItem(player, hand), player.getItemInHand(hand)));
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> InteractionEvents.USE_ENTITY.invoker().onUseEntity(player, hand, entity));
        // Fabric stops an attack on any result but PASS; FAIL also keeps the arm from swinging.
        AttackBlockCallback.EVENT.register((player, level, hand, pos, face) ->
                InteractionEvents.ATTACK_BLOCK.invoker().onAttackBlock(player, hand, pos, face).isInterrupt() ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                InteractionEvents.ATTACK_ENTITY.invoker().onAttackEntity(player, entity).isInterrupt() ? InteractionResult.FAIL : InteractionResult.PASS);

        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                !(level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer)
                        || !BlockEvents.BREAK.invoker().onBreak(serverLevel, pos, state, serverPlayer).isInterrupt());

        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                CommandEvents.REGISTER.invoker().register(dispatcher, context, selection));
    }
}
