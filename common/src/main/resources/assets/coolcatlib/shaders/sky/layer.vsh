#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <coolcatlib:sky.glsl>

layout(location = 0) in vec3 Position;

layout(location = 3) out vec3 skyLocal;
layout(location = 2) out vec3 skyWorld;

void main() {
    vec4 world = SkyModelMat * vec4(Position, 1.0);
    gl_Position = ProjMat * SkyViewMat * world;
    skyLocal = Position;
    skyWorld = world.xyz;
}
