#version 150

// World effects (net.ixdarklord.coolcatcanvas.api.client.vfx): what every batch is drawn with. A material's own
// fragment shader imports it with
//     #moj_import <coolcatcanvas:vfx.glsl>

// The camera's rotation. Vertices arrive relative to the camera's position.
uniform mat4 VfxViewMat;
// x: near plane, y: far plane, z: softness in blocks (0: hard edges), w: 1 to take alpha from brightness.
uniform vec4 VfxDepth;
// x: seconds the game has run unpaused.
uniform vec4 VfxTime;

// A depth buffer value as a distance from the camera, in blocks.
float vfx_linear_depth(float depth) {
    float z = depth * 2.0 - 1.0;
    return 2.0 * VfxDepth.x * VfxDepth.y / (VfxDepth.y + VfxDepth.x - z * (VfxDepth.y - VfxDepth.x));
}

// How much of a fragment shows in front of the scene: 0 behind it, rising to 1 over the softness distance.
float vfx_depth_fade(float sceneDepth, float fragmentDepth) {
    if (fragmentDepth >= sceneDepth) return 0.0;
    if (VfxDepth.z <= 0.0) return 1.0;
    return clamp((vfx_linear_depth(sceneDepth) - vfx_linear_depth(fragmentDepth)) / VfxDepth.z, 0.0, 1.0);
}
