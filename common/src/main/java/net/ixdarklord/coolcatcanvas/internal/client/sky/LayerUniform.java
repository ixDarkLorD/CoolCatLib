package net.ixdarklord.coolcatcanvas.internal.client.sky;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import net.minecraft.client.renderer.DynamicUniformStorage;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;

// The SkyLayerInfo block of coolcatcanvas:sky.glsl, one per layer, rewritten every frame the layer draws. Each layer
// keeps its own instance: DynamicUniformStorage skips a write equal to the previous one, and these compare by identity.
final class LayerUniform implements DynamicUniformStorage.DynamicUniform {
    static final String BLOCK = "SkyLayerInfo";
    static final int SIZE = new Std140SizeCalculator()
            .putMat4f().putMat4f()
            .putVec4().putVec4().putVec4().putVec4()
            .putVec4().putVec4().putVec4().putVec4()
            .get();

    final Matrix4f view = new Matrix4f();
    final Matrix4f model = new Matrix4f();
    final float[] color = new float[4];
    final float[] frame = new float[4];
    final float[] time = new float[4];
    final float[] env = new float[4];
    final float[] params = new float[16];

    @Override
    public void write(ByteBuffer buffer) {
        Std140Builder builder = Std140Builder.intoBuffer(buffer)
                .putMat4f(this.view)
                .putMat4f(this.model)
                .putVec4(this.color[0], this.color[1], this.color[2], this.color[3])
                .putVec4(this.frame[0], this.frame[1], this.frame[2], this.frame[3])
                .putVec4(this.time[0], this.time[1], this.time[2], this.time[3])
                .putVec4(this.env[0], this.env[1], this.env[2], this.env[3]);
        for (int i = 0; i < 16; i += 4) builder.putVec4(this.params[i], this.params[i + 1], this.params[i + 2], this.params[i + 3]);
    }
}
