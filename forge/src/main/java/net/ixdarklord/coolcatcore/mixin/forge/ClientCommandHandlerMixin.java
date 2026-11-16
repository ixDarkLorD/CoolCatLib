package net.ixdarklord.coolcatcore.mixin.forge;

import net.ixdarklord.coolcatcore.internal.client.ClientCommandFallback;
import net.minecraftforge.client.ClientCommandHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Forge runs any command whose name is a client command's on the client, and only an unknown command or argument
// goes on to the server: a server command sharing that name can lose its other parts. Those go to the server
// (ClientCommandFallback).
@Mixin(value = ClientCommandHandler.class, remap = false)
public abstract class ClientCommandHandlerMixin {
    @Inject(method = "runCommand", at = @At("HEAD"), cancellable = true)
    private static void coolcatlib$serverParts(String command, CallbackInfoReturnable<Boolean> cir) {
        if (ClientCommandFallback.forServer(command, ClientCommandHandler.getDispatcher(), ClientCommandHandler.getSource())) cir.setReturnValue(false);
    }
}
