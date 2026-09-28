#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <coolcatcanvas:sky.glsl>

in vec3 Position;

out vec3 skyLocal;
out vec3 skyWorld;

void main() {
    vec4 world = SkyModelMat * vec4(Position, 1.0);
    gl_Position = ProjMat * SkyViewMat * world;
    skyLocal = Position;
    skyWorld = world.xyz;
}
