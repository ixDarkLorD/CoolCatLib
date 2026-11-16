#version 150

#moj_import <coolcatcanvas:vfx.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ProjMat;

out vec2 texCoord;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * VfxViewMat * vec4(Position, 1.0);
    texCoord = UV0;
    vertexColor = Color;
}
