#version 150

#moj_import <coolcatcanvas:screen_effect.glsl>

uniform sampler2D DiffuseSampler;

uniform vec2 OutSize;

uniform vec4 Color;
uniform float Radius;
uniform float Softness;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    float edge = smoothstep(Radius - Softness, Radius, ce_edge(texCoord, OutSize));
    fragColor = vec4(mix(color, Color.rgb, clamp(edge * Color.a * Strength, 0.0, 1.0)), 1.0);
}
