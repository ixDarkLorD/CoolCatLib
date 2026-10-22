#version 330
#extension GL_ARB_separate_shader_objects : require

#include <coolcatlib:sky.glsl>

uniform sampler2D Sampler0;

layout(location = 3) in vec3 skyLocal;
layout(location = 2) in vec3 skyWorld;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = sky_finish(sky_texture(Sampler0, sky_panorama_uv(skyLocal)), skyWorld);
}
