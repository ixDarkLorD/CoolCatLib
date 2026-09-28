package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.internal.client.effect.ScreenEffectManager;
import net.ixdarklord.coolcatcanvas.internal.client.sky.SkyboxManager;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Screen effects and skyboxes rebuild once the new shader sources are in place (not reached if vanilla's own shaders failed).
@Mixin(ShaderManager.class)
public abstract class ShaderManagerMixin {
    @Inject(method = "apply(Lnet/minecraft/client/renderer/ShaderManager$Configs;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("TAIL"))
    private void coolcatcanvas$reloadEffectsAndSkyboxes(ShaderManager.Configs preparations, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
        ScreenEffectManager.reload(manager);
        SkyboxManager.reload(manager);
    }
}
