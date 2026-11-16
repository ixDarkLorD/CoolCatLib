package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Vanilla's record of the core shader it bound last: it skips glUseProgram when the same shader is applied again, so a
// CanvasProgram bound in between must clear it (see CanvasProgram#use).
@Mixin(ShaderInstance.class)
public interface ShaderInstanceAccessor {
    @Accessor("lastProgramId")
    static void coolcatcanvas$setLastProgramId(int programId) {
        throw new AssertionError();
    }

    @Accessor("lastAppliedShader")
    static void coolcatcanvas$setLastAppliedShader(ShaderInstance shader) {
        throw new AssertionError();
    }
}
