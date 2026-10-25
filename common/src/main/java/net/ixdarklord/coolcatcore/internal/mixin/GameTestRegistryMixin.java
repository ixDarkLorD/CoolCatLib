package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.gametest.GameTestRegistry;
import net.minecraft.gametest.framework.TestFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

// GameTests: a manual-only test isn't among the tests /test runall and test servers run, but /test run finds it by name.
@Mixin(net.minecraft.gametest.framework.GameTestRegistry.class)
public abstract class GameTestRegistryMixin {
    @Inject(method = "findTestFunction", at = @At("RETURN"), cancellable = true)
    private static void coolcatlib$findManualOnly(String testName, CallbackInfoReturnable<Optional<TestFunction>> cir) {
        if (cir.getReturnValue().isEmpty()) {
            Optional<TestFunction> test = GameTestRegistry.findManualOnly(testName);
            if (test.isPresent()) cir.setReturnValue(test);
        }
    }
}
