package net.ixdarklord.coolcatcanvas.internal.client.sky;

import net.ixdarklord.coolcatcanvas.internal.client.render.CanvasProgram;
import org.joml.Matrix4f;

// The Sky* uniforms of coolcatcanvas:sky.glsl for one layer, filled every frame the layer draws, then uploaded to its
// program.
final class LayerUniform {
    final Matrix4f view = new Matrix4f();
    final Matrix4f model = new Matrix4f();
    final float[] color = new float[4];
    final float[] frame = new float[4];
    final float[] time = new float[4];
    final float[] env = new float[4];
    final float[] params = new float[16];

    void upload(CanvasProgram program) {
        program.setMat4("SkyViewMat", this.view);
        program.setMat4("SkyModelMat", this.model);
        program.setVec4("SkyColor", this.color);
        program.setVec4("SkyFrame", this.frame);
        program.setVec4("SkyTime", this.time);
        program.setVec4("SkyEnv", this.env);
        program.setVec4Array("SkyParams", this.params);
    }
}
