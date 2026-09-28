#version 150

#moj_import <coolcatcanvas:screen_effect.glsl>

uniform sampler2D InSampler;

uniform vec2 OutSize;
uniform vec2 InSize;

uniform vec4 Color;
uniform float Radius;
uniform float Softness;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;
    float edge = smoothstep(Radius - Softness, Radius, ce_edge(texCoord, OutSize));
    fragColor = vec4(mix(color, Color.rgb, clamp(edge * Color.a * Strength, 0.0, 1.0)), 1.0);
}
