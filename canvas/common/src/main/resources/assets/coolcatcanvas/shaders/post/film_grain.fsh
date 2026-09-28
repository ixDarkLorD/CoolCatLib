#version 330

#moj_import <coolcatcanvas:screen_effect.glsl>

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform GrainConfig {
    float Amount;
    float Size;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;
    vec2 cell = floor(texCoord * OutSize / max(Size, 1.0));
    float grain = ce_hash(cell + vec2(Seed * 911.0, Seed * 577.0)) - 0.5;
    fragColor = vec4(color + grain * Amount * Strength, 1.0);
}
