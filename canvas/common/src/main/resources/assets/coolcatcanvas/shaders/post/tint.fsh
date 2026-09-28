#version 330

#moj_import <coolcatcanvas:screen_effect.glsl>

uniform sampler2D InSampler;

layout(std140) uniform TintConfig {
    vec4 Color;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;
    fragColor = vec4(mix(color, Color.rgb, clamp(Color.a * Strength, 0.0, 1.0)), 1.0);
}
