package net.ixdarklord.coolcatcanvas.internal.client.vfx;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

// The camera of the frame being drawn: where it is, and which ways are right and up for things that face it.
final class VfxCamera {
    double x, y, z;
    float rightX, rightY, rightZ;
    float upX, upY, upZ;
    final Matrix4f view = new Matrix4f();
    final Matrix4f projection = new Matrix4f();
    float far;

    void set(Camera camera, float far) {
        Vec3 position = camera.getPosition();
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
        // The level's view matrix: the camera's rotation, undone.
        this.view.rotation(camera.rotation().conjugate(new Quaternionf()));
        // The view matrix is a pure rotation, so its rows are the camera's axes in world space.
        this.rightX = this.view.m00();
        this.rightY = this.view.m10();
        this.rightZ = this.view.m20();
        this.upX = this.view.m01();
        this.upY = this.view.m11();
        this.upZ = this.view.m21();
        this.far = far;
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
