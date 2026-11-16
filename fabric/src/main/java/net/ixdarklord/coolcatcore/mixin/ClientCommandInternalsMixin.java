package net.ixdarklord.coolcatcore.mixin;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.impl.command.client.ClientCommandInternals;
import net.ixdarklord.coolcatcore.internal.client.ClientCommandFallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Fabric runs any command whose name is a client command's on the client, and only an unknown command goes on to the
// server: a server command sharing that name loses its other parts. Those go to the server (ClientCommandFallback).
@Mixin(value = ClientCommandInternals.class, remap = false)
public abstract class ClientCommandInternalsMixin {
    @Inject(method = "executeCommand", at = @At("HEAD"), cancellable = true)
    private static void coolcatlib$serverParts(String command, CallbackInfoReturnable<Boolean> cir) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        // The suggestion provider is Fabric's client command source (a mixin of Fabric's implements it).
        FabricClientCommandSource source = (FabricClientCommandSource) connection.getSuggestionsProvider();
        if (ClientCommandFallback.forServer(command, ClientCommandInternals.getActiveDispatcher(), source)) cir.setReturnValue(false);
    }
}
