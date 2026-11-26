package net.ixdarklord.coolcatcanvas.internal.client.vfx;

import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

// The camera of the frame being drawn: where it is, and which ways are right and up for things that face it.
final class VfxCamera {
    double x, y, z;
    float rightX, rightY, rightZ;
    float upX, upY, upZ;
    final Matrix4f view = new Matrix4f();
    final Matrix4f projection = new Matrix4f();
    private final Matrix4f levelView = new Matrix4f();
    private boolean hasLevelView;
    float far;

    void set(Camera camera, float far) {
        Vec3 position = camera.getPosition();
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
        // The level's view matrix as the level was drawn with it, or else the camera's own. They differ only when a shader
        // mod puts the view bobbing into the view: the effects have to bob with the level then.
        if (this.hasLevelView) this.view.set(this.levelView);
        else this.view.rotationX(camera.getXRot() * Mth.DEG_TO_RAD).rotateY((camera.getYRot() + 180.0F) * Mth.DEG_TO_RAD);
        this.hasLevelView = false;
        // Its rotation's rows are the camera's axes in world space (bobbing tilts them a little, as it does the level).
        this.rightX = this.view.m00();
        this.rightY = this.view.m10();
        this.rightZ = this.view.m20();
        this.upX = this.view.m01();
        this.upY = this.view.m11();
        this.upZ = this.view.m21();
        this.far = far;
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
