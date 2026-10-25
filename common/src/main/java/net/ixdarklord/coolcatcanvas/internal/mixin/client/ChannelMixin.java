package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import com.mojang.blaze3d.audio.Channel;
import net.ixdarklord.coolcatcanvas.api.client.sound.SoundContext;
import net.ixdarklord.coolcatcanvas.internal.client.sound.FilterableChannel;
import net.ixdarklord.coolcatcanvas.internal.client.sound.SoundFilterManager;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Filters each sound (SoundFilterManager) as it is placed: when it starts (the engine tells it which sound it plays,
// then the game sets its place, then whether it is relative to the listener) and each time it moves. Remembers all of
// it, to filter it again on refilter.
@Mixin(Channel.class)
public abstract class ChannelMixin implements FilterableChannel {
    @Shadow
    @Final
    private int source;
    @Unique
    private Vec3 coolcatlib$position = Vec3.ZERO;
    @Unique
    private boolean coolcatlib$relative;
    @Unique
    private @Nullable Identifier coolcatlib$sound;
    @Unique
    private SoundSource coolcatlib$source = SoundSource.MASTER;

    @Inject(method = "setSelfPosition", at = @At("TAIL"))
    private void coolcatlib$placed(Vec3 position, CallbackInfo ci) {
        this.coolcatlib$position = position;
        this.coolcatlib$refilter();
    }

    @Inject(method = "setRelative", at = @At("TAIL"))
    private void coolcatlib$setRelative(boolean relative, CallbackInfo ci) {
        this.coolcatlib$relative = relative;
        this.coolcatlib$refilter();
    }

    @Override
    public void coolcatlib$setSound(Identifier sound, SoundSource source) {
        this.coolcatlib$sound = sound;
        this.coolcatlib$source = source;
    }

    @Override
    public void coolcatlib$refilter() {
        SoundFilterManager.apply(this.source, new SoundContext(this.coolcatlib$position, this.coolcatlib$sound, this.coolcatlib$source, this.coolcatlib$relative));
    }
}
