#version 150

#moj_import <coolcatcanvas:sky.glsl>

uniform sampler2D Sampler0;

in vec3 skyLocal;
in vec3 skyWorld;

out vec4 fragColor;

// Six faces in a 3x2 grid: north, east, south on top; west, up, down below. Side faces are upright as seen from
// inside; up and down are as seen while facing north.
void main() {
    vec3 d = skyLocal;
    vec3 a = abs(d);
    vec2 cell;
    vec2 face;
    if (a.x >= a.y && a.x >= a.z) {
        cell = d.x > 0.0 ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
        face = vec2(d.x > 0.0 ? d.z : -d.z, -d.y) / a.x;
    } else if (a.y >= a.z) {
        cell = d.y > 0.0 ? vec2(1.0, 1.0) : vec2(2.0, 1.0);
        face = vec2(d.x, d.y > 0.0 ? -d.z : d.z) / a.y;
    } else {
        cell = d.z < 0.0 ? vec2(0.0, 0.0) : vec2(2.0, 0.0);
        face = vec2(d.z < 0.0 ? d.x : -d.x, -d.y) / a.z;
    }

    // Half a texel in from each face's edges, so filtering never bleeds in the next face.
    vec2 size = vec2(textureSize(Sampler0, 0)) / vec2(3.0, 2.0 * max(SkyFrame.w, 1.0));
    vec2 inset = 0.5 / size;
    vec2 uv = clamp(face * 0.5 + 0.5, inset, 1.0 - inset);
    fragColor = sky_finish(sky_texture(Sampler0, (cell + uv) / vec2(3.0, 2.0)), skyWorld);
}
