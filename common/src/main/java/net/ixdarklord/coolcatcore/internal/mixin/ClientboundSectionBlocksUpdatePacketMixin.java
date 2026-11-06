package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.block.BlockViewRegistry;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// BlockViews: the states a section's block changes carry, to be changed in a player's copy (BlockViewRegistry).
@Mixin(ClientboundSectionBlocksUpdatePacket.class)
public abstract class ClientboundSectionBlocksUpdatePacketMixin implements BlockViewRegistry.SectionUpdate {
    @Shadow
    @Final
    private BlockState[] states;

    @Override
    public BlockState[] coolcatlib$getStates() {
        return this.states;
    }
}
