#version 330

#moj_import <coolcatcanvas:screen_effect.glsl>

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform VignetteConfig {
    vec4 Color;
    float Radius;
    float Softness;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;
    float edge = smoothstep(Radius - Softness, Radius, ce_edge(texCoord, OutSize));
    fragColor = vec4(mix(color, Color.rgb, clamp(edge * Color.a * Strength, 0.0, 1.0)), 1.0);
}
