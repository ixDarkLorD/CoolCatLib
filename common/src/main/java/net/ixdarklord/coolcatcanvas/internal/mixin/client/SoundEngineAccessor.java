package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The sound engine's channels, to filter every sound playing again (SoundFilters.refresh).
@Mixin(SoundEngine.class)
public interface SoundEngineAccessor {
    @Accessor("channelAccess")
    ChannelAccess coolcatlib$channelAccess();
}
