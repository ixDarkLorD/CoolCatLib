package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.gametest.GameTestRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.RegistryLoadTask;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceManagerRegistryLoadTask;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

// GameTests: a server's test instance registry, loaded from its data packs, gets the registered tests before it's frozen
// (clients receive them with the other synced registries).
@Mixin(RegistryLoadTask.class)
public abstract class RegistryLoadTaskMixin<T> {
    @Shadow
    @Final
    private WritableRegistry<T> registry;

    @SuppressWarnings("unchecked")
    @Inject(method = "freezeRegistry", at = @At("HEAD"))
    private void coolcatlib$addGameTests(Map<ResourceKey<?>, Exception> loadingErrors, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ResourceManagerRegistryLoadTask<?>)) return;
        Object key = this.registry.key();
        if (key.equals(Registries.TEST_ENVIRONMENT)) GameTestRegistry.environments((Registry<TestEnvironmentDefinition<?>>) this.registry);
        else if (key.equals(Registries.TEST_INSTANCE)) GameTestRegistry.addInstances((WritableRegistry<GameTestInstance>) this.registry);
    }
}
