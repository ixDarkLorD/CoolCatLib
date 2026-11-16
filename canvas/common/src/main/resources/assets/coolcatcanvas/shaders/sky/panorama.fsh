#version 150

#moj_import <coolcatcanvas:sky.glsl>

uniform sampler2D Sampler0;

in vec3 skyLocal;
in vec3 skyWorld;

out vec4 fragColor;

void main() {
    fragColor = sky_finish(sky_texture(Sampler0, sky_panorama_uv(skyLocal)), skyWorld);
}
