package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.block.BlockViewRegistry;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// BlockViews: a section counts its block changes, and is written (and sized) as its blocks look to the player a chunk is
// being built for (BlockViewRegistry): the game's own write, with the blocks swapped. (Plain injects rather than
// redirects: the MixinExtras NeoForge ships misreads a compiled @Redirect's target.)
@Mixin(LevelChunkSection.class)
public abstract class LevelChunkSectionMixin implements BlockViewRegistry.Tracked {
    @Shadow
    private short nonEmptyBlockCount;
    @Shadow
    private short fluidCount;
    @Shadow
    @Final
    private PalettedContainer<BlockState> states;
    @Shadow
    private PalettedContainerRO<Holder<Biome>> biomes;
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

    @Inject(method = "write", at = @At("HEAD"), cancellable = true)
    private void coolcatlib$writeViewed(FriendlyByteBuf buffer, CallbackInfo ci) {
        if (BlockViewRegistry.buildingFor() == null) return;
        buffer.writeShort(this.nonEmptyBlockCount);
        buffer.writeShort(this.fluidCount);
        BlockViewRegistry.statesOf((LevelChunkSection) (Object) this, this.states).write(buffer);
        this.biomes.write(buffer);
        ci.cancel();
    }

    @Inject(method = "getSerializedSize", at = @At("HEAD"), cancellable = true)
    private void coolcatlib$sizeViewed(CallbackInfoReturnable<Integer> cir) {
        if (BlockViewRegistry.buildingFor() == null) return;
        cir.setReturnValue(4 + BlockViewRegistry.statesOf((LevelChunkSection) (Object) this, this.states).getSerializedSize() + this.biomes.getSerializedSize());
    }
}
