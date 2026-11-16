package net.ixdarklord.coolcatcore.internal.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.ixdarklord.coolcatcore.api.event.v2.common.CommandEvents;
import net.ixdarklord.coolcatcore.internal.config.ConfigCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// CommandEvents.EXECUTE, before a parsed command runs, and the config commands, added once every mod registered its own
// commands, so a mod's command keeps its own permission when the config command joins it.
@Mixin(Commands.class)
public abstract class CommandsMixin {
    @Shadow @Final private CommandDispatcher<CommandSourceStack> dispatcher;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void coolcatlib$registerConfigCommands(CallbackInfo ci) {
        ConfigCommands.registerServer(this.dispatcher);
    }

    @Inject(method = "performCommand(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void coolcatlib$execute(ParseResults<CommandSourceStack> parse, String command, CallbackInfo ci) {
        if (CommandEvents.EXECUTE.hasListeners() && CommandEvents.EXECUTE.invoker().onExecute(parse, command).isInterrupt()) ci.cancel();
    }
}
