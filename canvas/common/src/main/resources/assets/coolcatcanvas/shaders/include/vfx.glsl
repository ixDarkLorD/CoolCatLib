#version 330

// World effects (net.ixdarklord.coolcatcanvas.api.client.vfx): what every batch is drawn with.
layout(std140) uniform VfxInfo {
    // The camera's rotation. Vertices arrive relative to the camera's position.
    mat4 VfxViewMat;
    // x: near plane, y: far plane, z: softness in blocks (0: hard edges), w: 1 to take alpha from brightness.
    vec4 VfxDepth;
    // x: seconds since the client started.
    vec4 VfxTime;
};

// A depth buffer value as a distance from the camera, in blocks. Depth is reversed and zero to one: 1 at the near plane,
// 0 at the far one.
float vfx_linear_depth(float depth) {
    return VfxDepth.x * VfxDepth.y / (VfxDepth.x + depth * (VfxDepth.y - VfxDepth.x));
}

// How much of a fragment shows in front of the scene: 0 behind it, rising to 1 over the softness distance.
float vfx_depth_fade(float sceneDepth, float fragmentDepth) {
    // Reversed depth: nearer is larger.
    if (fragmentDepth <= sceneDepth) return 0.0;
    if (VfxDepth.z <= 0.0) return 1.0;
    return clamp((vfx_linear_depth(sceneDepth) - vfx_linear_depth(fragmentDepth)) / VfxDepth.z, 0.0, 1.0);
}
