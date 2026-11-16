package net.ixdarklord.coolcatcanvas.internal.client.vfx;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import net.minecraft.client.renderer.DynamicGpuDataStorage;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;

// The VfxInfo block of coolcatlib:vfx.glsl, one per batch drawn in a frame. Each batch keeps its own instance:
// the storage skips a write equal to the previous one, and these compare by identity.
final class VfxUniform implements DynamicGpuDataStorage.DynamicGpuData {
    static final String BLOCK = "VfxInfo";
    static final int SIZE = new Std140SizeCalculator().putMat4f().putVec4().putVec4().get();

    final Matrix4f view = new Matrix4f();
    float near;
    float far;
    float softness;
    float luminanceAlpha;
    float time;

    @Override
    public void write(ByteBuffer buffer) {
        Std140Builder.intoBuffer(buffer)
                .putMat4f(this.view)
                .putVec4(this.near, this.far, this.softness, this.luminanceAlpha)
                .putVec4(this.time, 0.0F, 0.0F, 0.0F);
    }
}
