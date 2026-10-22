#version 330
#extension GL_ARB_separate_shader_objects : require

#include <coolcatlib:screen_effect.glsl>

// The built-in tint (coolcatlib:tint): mixes the frame towards Color.rgb by Color.a and the effect's strength.
uniform sampler2D InSampler;

layout(std140) uniform TintConfig {
    vec4 Color;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;
    fragColor = vec4(mix(color, Color.rgb, clamp(Color.a * Strength, 0.0, 1.0)), 1.0);
}
