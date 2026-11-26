package net.ixdarklord.coolcatcanvas.internal.client.vfx;

import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

// The camera of the frame being drawn: where it is, and which ways are right and up for things that face it.
final class VfxCamera {
    double x, y, z;
    float rightX, rightY, rightZ;
    float upX, upY, upZ;
    final Matrix4f view = new Matrix4f();
    private final Matrix4f levelView = new Matrix4f();
    private boolean hasLevelView;
    float far;

    void set(CameraRenderState camera) {
        this.x = camera.pos.x;
        this.y = camera.pos.y;
        this.z = camera.pos.z;
        // The level's view matrix as the level was drawn with it, or else the camera's own. They differ only when a shader
        // mod puts the view bobbing into the view: the effects have to bob with the level then.
        this.view.set(this.hasLevelView ? this.levelView : camera.viewRotationMatrix);
        this.hasLevelView = false;
        // Its rotation's rows are the camera's axes in world space (bobbing tilts them a little, as it does the level).
        this.rightX = this.view.m00();
        this.rightY = this.view.m10();
        this.rightZ = this.view.m20();
        this.upX = this.view.m01();
        this.upY = this.view.m11();
        this.upZ = this.view.m21();
        this.far = camera.depthFar;
    }

    void levelView(Matrix4fc view) {
        this.levelView.set(view);
        this.hasLevelView = true;
    }

    float relX(double worldX) {
        return (float) (worldX - this.x);
    }

    float relY(double worldY) {
        return (float) (worldY - this.y);
    }

    float relZ(double worldZ) {
        return (float) (worldZ - this.z);
    }
}
