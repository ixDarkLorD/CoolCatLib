package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.minecraft.client.renderer.EffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The same record as ShaderInstanceAccessor's, kept apart by vanilla's post effect programs.
@Mixin(EffectInstance.class)
public interface EffectInstanceAccessor {
    @Accessor("lastProgramId")
    static void coolcatcanvas$setLastProgramId(int programId) {
        throw new AssertionError();
    }

    @Accessor("lastAppliedEffect")
    static void coolcatcanvas$setLastAppliedEffect(EffectInstance effect) {
        throw new AssertionError();
    }
}
