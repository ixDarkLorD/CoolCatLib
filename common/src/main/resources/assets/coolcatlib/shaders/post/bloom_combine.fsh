#version 330
#extension GL_ARB_separate_shader_objects : require

#include <coolcatlib:screen_effect.glsl>

// Bloom, last pass: the frame plus its blurred highlights.
uniform sampler2D InSampler;
uniform sampler2D BloomSampler;

layout(std140) uniform BloomConfig {
    vec2 BlurDir;
    float Threshold;
    float Intensity;
    float Radius;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;
    vec3 bloom = texture(BloomSampler, texCoord).rgb;
    fragColor = vec4(color + bloom * Intensity * clamp(Strength, 0.0, 1.0), 1.0);
}
