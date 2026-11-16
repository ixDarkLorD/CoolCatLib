#version 150

#moj_import <coolcatcanvas:sky.glsl>

uniform sampler2D Sampler0;

in vec3 skyLocal;
in vec3 skyWorld;

out vec4 fragColor;

// At the zenith, the image's top edge points north.
void main() {
    fragColor = sky_finish(sky_texture(Sampler0, skyLocal.xz * 0.5 + 0.5), skyWorld);
}
