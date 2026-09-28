#version 150

#moj_import <coolcatcanvas:sky.glsl>

in vec3 Position;

uniform mat4 ProjMat;

out vec3 skyLocal;
out vec3 skyWorld;

void main() {
    vec4 world = SkyModelMat * vec4(Position, 1.0);
    gl_Position = ProjMat * SkyViewMat * world;
    skyLocal = Position;
    skyWorld = world.xyz;
}
