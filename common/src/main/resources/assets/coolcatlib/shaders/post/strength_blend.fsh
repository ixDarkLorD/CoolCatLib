#version 330
#extension GL_ARB_separate_shader_objects : require

#include <coolcatlib:screen_effect.glsl>

// Auto-blend: fades any effect by mixing the untouched frame with the processed one.
uniform sampler2D OriginalSampler;
uniform sampler2D ProcessedSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = mix(texture(OriginalSampler, texCoord), texture(ProcessedSampler, texCoord), clamp(Strength, 0.0, 1.0));
}
