package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.internal.client.cutscene.CutsceneManager;
import net.ixdarklord.coolcatcanvas.internal.client.effect.ScreenEffectManager;
import net.ixdarklord.coolcatcanvas.internal.client.sky.SkyboxManager;
import net.ixdarklord.coolcatcanvas.internal.client.vfx.VfxManager;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

// Screen effects and skyboxes rebuild once the new shader sources are in place (not reached if vanilla's own shaders failed).
// Cutscene files are read again at the same point: it is the reload hook Canvas already has.
@Mixin(ShaderManager.class)
public abstract class ShaderManagerMixin {
    // The resources being reloaded, kept from the reload's start: applying the shaders is no longer handed them.
    @Unique
    private @Nullable ResourceManager coolcatcanvas$manager;

    @Inject(method = "reload", at = @At("HEAD"))
    private void coolcatcanvas$keepManager(PreparableReloadListener.SharedState state, Executor tasks, PreparableReloadListener.PreparationBarrier barrier,
                                    Executor reloads, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        this.coolcatcanvas$manager = state.resourceManager();
    }

    @Inject(method = "apply", at = @At("TAIL"))
    private void coolcatcanvas$reloadEffectsAndSkyboxes(CallbackInfo ci) {
        ResourceManager manager = this.coolcatcanvas$manager;
        if (manager == null) return;
        ScreenEffectManager.reload(manager);
        SkyboxManager.reload(manager);
        VfxManager.reload();
        CutsceneManager.reload(manager);
        net.ixdarklord.coolcatcanvas.internal.client.model.ObjModelCache.reload();
        net.ixdarklord.coolcatcanvas.internal.client.armature.ArmatureLibrary.reload();
    }
}
