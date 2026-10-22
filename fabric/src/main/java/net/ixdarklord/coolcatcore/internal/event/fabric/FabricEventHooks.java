package net.ixdarklord.coolcatcore.internal.event.fabric;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
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
import net.ixdarklord.coolcatcore.api.event.v2.common.LivingEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.ixdarklord.coolcatcore.api.hooks.ServerLifecycleHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

// Fires CoolCatLib's common events from Fabric's. Player ticks and block placing come from mixins (Fabric has no
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
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> EntityEvents.UNLOAD.invoker().onUnload(entity, level));

        // "alive" is true for a player coming back from the End, false after death.
        ServerPlayerEvents.COPY_FROM.register((original, player, alive) -> PlayerEvents.CLONE.invoker().onClone(original, player, !alive));
        ServerPlayerEvents.AFTER_RESPAWN.register((original, player, alive) -> PlayerEvents.RESPAWN.invoker().onRespawn(player, alive));
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) ->
                PlayerEvents.CHANGE_DIMENSION.invoker().onChangeDimension(player, from.dimension(), to.dimension()));

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !LivingEvents.DAMAGE.invoker().onDamage(entity, source, amount).isInterrupt());
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> !LivingEvents.DEATH.invoker().onDeath(entity, source).isInterrupt());

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> InteractionEvents.USE_BLOCK.invoker().onUseBlock(player, hand, hit));
        UseItemCallback.EVENT.register((player, level, hand) -> InteractionEvents.USE_ITEM.invoker().onUseItem(player, hand));
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
