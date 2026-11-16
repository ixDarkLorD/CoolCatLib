package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.internal.client.sound.FilterableChannel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

// Tells a sound's channel which sound it plays, before the game sets it up (the channel's tasks run in order on the
// sound thread), so the filter providers know what they are asked about.
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Shadow
    @Final
    private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

    @Inject(method = "play", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V", ordinal = 0))
    private void coolcatlib$nameChannel(SoundInstance instance, CallbackInfo ci) {
        ChannelAccess.ChannelHandle handle = this.instanceToChannel.get(instance);
        if (handle == null) return;
        ResourceLocation sound = instance.getLocation();
        SoundSource source = instance.getSource();
        handle.execute(channel -> ((FilterableChannel) channel).coolcatlib$setSound(sound, source));
    }
}
