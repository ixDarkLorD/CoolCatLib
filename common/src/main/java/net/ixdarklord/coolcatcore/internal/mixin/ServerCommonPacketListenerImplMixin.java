package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.block.BlockViewRegistry;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// BlockViews: block changes and block entities are sent to a player as their blocks look to them (BlockViewRegistry).
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("HEAD"), cancellable = true)
    private void coolcatlib$viewed(Packet<?> packet, @Nullable PacketSendListener listener, CallbackInfo ci) {
        if (!((Object) this instanceof ServerGamePacketListenerImpl game)) return;
        Packet<?> shown = BlockViewRegistry.forPlayer(game.player, packet);
        if (shown == packet) return;
        ci.cancel();
        if (shown != null) BlockViewRegistry.sendAsIs(() -> game.send(shown, listener));
    }
}
