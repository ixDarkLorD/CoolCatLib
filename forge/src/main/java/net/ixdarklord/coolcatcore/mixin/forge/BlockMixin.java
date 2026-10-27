package net.ixdarklord.coolcatcore.mixin.forge;

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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// BlockEvents.DROPS (Forge has no drops event): each way a broken block drops its items holds them back, with the
// experience it pops, and drops them once they've been told about (BlockDrops). (No annotations on the handlers'
// parameters: Mixin trips over them on @Redirect.) A player's breaking goes through Forge's
// own dropResources, which takes whether to drop experience (Forge's own method, so not remapped).
@Mixin(Block.class)
public abstract class BlockMixin {
    @Redirect(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)Ljava/util/List;"))
    private static List<ItemStack> coolcatlib$holdDrops(BlockState state, ServerLevel level, BlockPos pos, BlockEntity blockEntity) {
        return BlockDrops.hold(level, pos, state, null, ItemStack.EMPTY, Block.getDrops(state, level, pos, blockEntity));
    }

    @Redirect(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)Ljava/util/List;"))
    private static List<ItemStack> coolcatlib$holdDropsOf(BlockState state, ServerLevel level, BlockPos pos, BlockEntity blockEntity) {
        return BlockDrops.hold(level, pos, state, null, ItemStack.EMPTY, Block.getDrops(state, level, pos, blockEntity));
    }

    @Redirect(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;Z)V", remap = false,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;", remap = true))
    private static List<ItemStack> coolcatlib$holdBrokenDrops(BlockState state, ServerLevel level, BlockPos pos, BlockEntity blockEntity, Entity breaker, ItemStack tool) {
        return BlockDrops.hold(level, pos, state, breaker, tool, Block.getDrops(state, level, pos, blockEntity, breaker, tool));
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V", at = @At("RETURN"))
    private static void coolcatlib$dropHeld(BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel) BlockDrops.release(serverLevel, pos);
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V", at = @At("RETURN"))
    private static void coolcatlib$dropHeldOf(BlockState state, LevelAccessor level, BlockPos pos, BlockEntity blockEntity, CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel) BlockDrops.release(serverLevel, pos);
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;Z)V", at = @At("RETURN"), remap = false)
    private static void coolcatlib$dropHeldBroken(BlockState state, Level level, BlockPos pos, BlockEntity blockEntity, Entity breaker, ItemStack tool,
                                                  boolean dropExperience, CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel) BlockDrops.release(serverLevel, pos);
    }

    @Inject(method = "popExperience", at = @At("HEAD"), cancellable = true)
    private void coolcatlib$holdExperience(ServerLevel level, BlockPos pos, int amount, CallbackInfo ci) {
        if (BlockDrops.holdExperience(level, pos, amount)) ci.cancel();
    }
}
