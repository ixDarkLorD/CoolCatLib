package net.ixdarklord.coolcatcanvas.internal.client.sky;

import net.ixdarklord.coolcatcanvas.internal.client.effect.ShaderProgram;
import org.joml.Matrix4f;

// The uniforms coolcatcanvas:sky.glsl declares for one layer, rewritten every frame the layer draws. Each layer keeps
// its own instance, so a frame allocates nothing.
final class LayerUniform {
    final Matrix4f view = new Matrix4f();
    final Matrix4f model = new Matrix4f();
    final float[] color = new float[4];
    final float[] frame = new float[4];
    final float[] time = new float[4];
    final float[] env = new float[4];
    final float[] params = new float[16];

    /** Sets them on the program in use. */
    void upload(ShaderProgram program) {
        program.setMatrix("SkyViewMat", this.view);
        program.setMatrix("SkyModelMat", this.model);
        program.setVec4("SkyColor", this.color[0], this.color[1], this.color[2], this.color[3]);
        program.setVec4("SkyFrame", this.frame[0], this.frame[1], this.frame[2], this.frame[3]);
        program.setVec4("SkyTime", this.time[0], this.time[1], this.time[2], this.time[3]);
        program.setVec4("SkyEnv", this.env[0], this.env[1], this.env[2], this.env[3]);
        program.setVec4Array("SkyParams", this.params);
    }
}
