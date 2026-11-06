package net.ixdarklord.coolcatcore.internal.mixin;

import com.mojang.brigadier.ParseResults;
import net.ixdarklord.coolcatcore.api.event.v2.common.CommandEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// CommandEvents.EXECUTE, before a parsed command runs.
@Mixin(Commands.class)
public abstract class CommandsMixin {
    @Inject(method = "performCommand(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
    private void coolcatlib$execute(ParseResults<CommandSourceStack> parse, String command, CallbackInfoReturnable<Integer> cir) {
        if (CommandEvents.EXECUTE.hasListeners() && CommandEvents.EXECUTE.invoker().onExecute(parse, command).isInterrupt()) cir.setReturnValue(0);
    }
}
