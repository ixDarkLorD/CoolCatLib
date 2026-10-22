#version 330
#extension GL_ARB_separate_shader_objects : require

#include <coolcatlib:sky.glsl>

layout(location = 3) in vec3 skyLocal;
layout(location = 2) in vec3 skyWorld;

layout(location = 0) out vec4 fragColor;

// SkyParams[0]: zenith color, [1]: horizon color, [2]: nadir color, [3].x: horizon sharpness (1 when 0).
void main() {
    float height = normalize(skyLocal).y;
    float sharpness = SkyParams[3].x > 0.0 ? SkyParams[3].x : 1.0;
    float t = pow(abs(height), 1.0 / sharpness);
    vec4 color = height >= 0.0 ? mix(SkyParams[1], SkyParams[0], t) : mix(SkyParams[1], SkyParams[2], t);
    fragColor = sky_finish(color, skyWorld);
}
