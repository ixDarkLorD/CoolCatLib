#version 150

#moj_import <coolcatcanvas:screen_effect.glsl>

uniform sampler2D DiffuseSampler;

uniform vec2 OutSize;

uniform float Rate;
uniform float Zoom;
uniform vec4 Color;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    // Two thumps per beat: lub, then a softer dub.
    float t = fract(Time * Rate);
    float beat = exp(-t * 18.0) + 0.6 * step(0.18, t) * exp(-(t - 0.18) * 18.0);
    beat *= Strength;

    vec2 uv = 0.5 + (texCoord - 0.5) * (1.0 - Zoom * beat);
    vec3 color = texture(DiffuseSampler, uv).rgb;
    float edge = smoothstep(0.35, 1.0, ce_edge(texCoord, OutSize)) * (0.4 * Strength + 0.6 * beat);
    fragColor = vec4(mix(color, Color.rgb, clamp(edge * Color.a, 0.0, 1.0)), 1.0);
}
