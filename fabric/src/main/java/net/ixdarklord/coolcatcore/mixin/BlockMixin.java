package net.ixdarklord.coolcatcore.mixin;

import net.ixdarklord.coolcatcore.api.event.v2.common.BlockEvents;
import net.ixdarklord.coolcatcore.internal.event.BlockDrops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// BlockEvents.DROPS (Fabric has no drops event): while anyone listens, each way a broken block drops its items does what
// the game does, with the items and the experience it pops held back until they've been told about (BlockDrops).
@Mixin(Block.class)
public abstract class BlockMixin {
    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"), cancellable = true)
    private static void coolcatlib$drops(BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel) || !BlockEvents.DROPS.hasListeners()) return;
        BlockDrops.handle(serverLevel, pos, state, null, ItemStack.EMPTY, Block.getDrops(state, serverLevel, pos, null),
                () -> state.spawnAfterBreak(serverLevel, pos, ItemStack.EMPTY, true));
        ci.cancel();
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
            at = @At("HEAD"), cancellable = true)
    private static void coolcatlib$dropsOf(BlockState state, LevelAccessor level, BlockPos pos, @Nullable BlockEntity blockEntity, CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel) || !BlockEvents.DROPS.hasListeners()) return;
        BlockDrops.handle(serverLevel, pos, state, null, ItemStack.EMPTY, Block.getDrops(state, serverLevel, pos, blockEntity),
                () -> state.spawnAfterBreak(serverLevel, pos, ItemStack.EMPTY, true));
        ci.cancel();
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private static void coolcatlib$brokenDrops(BlockState state, Level level, BlockPos pos, @Nullable BlockEntity blockEntity, @Nullable Entity breaker, ItemStack tool, CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel) || !BlockEvents.DROPS.hasListeners()) return;
        BlockDrops.handle(serverLevel, pos, state, breaker, tool, Block.getDrops(state, serverLevel, pos, blockEntity, breaker, tool),
                () -> state.spawnAfterBreak(serverLevel, pos, tool, true));
        ci.cancel();
    }

    @Inject(method = "popExperience", at = @At("HEAD"), cancellable = true)
    private void coolcatlib$holdExperience(ServerLevel level, BlockPos pos, int amount, CallbackInfo ci) {
        if (BlockDrops.holdExperience(level, pos, amount)) ci.cancel();
    }
}
