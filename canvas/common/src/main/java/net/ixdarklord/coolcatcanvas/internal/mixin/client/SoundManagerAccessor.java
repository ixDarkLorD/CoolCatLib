package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The sound manager's engine, to reach the channels playing (SoundFilters.refresh).
@Mixin(SoundManager.class)
public interface SoundManagerAccessor {
    @Accessor("soundEngine")
    SoundEngine coolcatlib$soundEngine();
}
