package net.ixdarklord.coolcatcore.internal.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ixdarklord.coolcatcore.internal.block.BlockViewRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// BlockViews: a section counts its block changes, and is written (and sized) as its blocks look to the player a chunk is
// being built for (BlockViewRegistry). Wrapped rather than redirected, so other mods can change the same calls too.
@Mixin(LevelChunkSection.class)
public abstract class LevelChunkSectionMixin implements BlockViewRegistry.Tracked {
    @Unique
    private int coolcatlib$changes;

    @Override
    public int coolcatlib$getChanges() {
        return this.coolcatlib$changes;
    }

    @Inject(method = "setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("HEAD"))
    private void coolcatlib$changed(int x, int y, int z, BlockState state, boolean checkThreading, CallbackInfoReturnable<BlockState> cir) {
        this.coolcatlib$changes++;
    }

    @WrapOperation(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/PalettedContainer;write(Lnet/minecraft/network/FriendlyByteBuf;)V", ordinal = 0))
    private void coolcatlib$writeViewed(PalettedContainer<BlockState> states, FriendlyByteBuf buffer, Operation<Void> write) {
        write.call(BlockViewRegistry.statesOf((LevelChunkSection) (Object) this, states), buffer);
    }

    @WrapOperation(method = "getSerializedSize", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/PalettedContainer;getSerializedSize()I", ordinal = 0))
    private int coolcatlib$sizeViewed(PalettedContainer<BlockState> states, Operation<Integer> size) {
        return size.call(BlockViewRegistry.statesOf((LevelChunkSection) (Object) this, states));
    }
}
