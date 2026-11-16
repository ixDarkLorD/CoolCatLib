#version 330

// Bloom, passes 2 to 5: a 9-tap Gaussian along BlurDir, its taps Radius texels apart (bilinear sampling fills in between).
uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BloomConfig {
    vec2 BlurDir;
    float Threshold;
    float Intensity;
    float Radius;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 stepUv = BlurDir * max(Radius, 0.5) / InSize;
    vec3 sum = texture(InSampler, texCoord).rgb * 0.2270270270;
    sum += (texture(InSampler, texCoord + stepUv * 1.3846153846).rgb + texture(InSampler, texCoord - stepUv * 1.3846153846).rgb) * 0.3162162162;
    sum += (texture(InSampler, texCoord + stepUv * 3.2307692308).rgb + texture(InSampler, texCoord - stepUv * 3.2307692308).rgb) * 0.0702702703;
    fragColor = vec4(sum, 1.0);
}
