#version 150

// Set by CoolCatLib: Canvas for every pass of a screen effect. Import with:
//     #moj_import <coolcatcanvas:screen_effect.glsl>
// The effect's strength this frame, 0 to 1: its fade, manual strength and strength function combined.
uniform float Strength;
// Seconds of real time since the game started, wrapping every hour.
uniform float Time;
// Seconds since the effect last became visible.
uniform float Age;
// A fresh random number in [0, 1) every frame.
uniform float Seed;

float ce_luma(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

float ce_hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float ce_noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(ce_hash(i), ce_hash(i + vec2(1.0, 0.0)), u.x),
               mix(ce_hash(i + vec2(0.0, 1.0)), ce_hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

// Distance from the center, 0 there and 1 at the corners, corrected for the aspect ratio.
float ce_edge(vec2 uv, vec2 size) {
    vec2 d = (uv - 0.5) * vec2(size.x / size.y, 1.0);
    vec2 corner = vec2(0.5 * size.x / size.y, 0.5);
    return length(d) / length(corner);
}
