#version 330

#moj_import <coolcatcanvas:screen_effect.glsl>

uniform sampler2D InSampler;

layout(std140) uniform DesaturateConfig {
    float Amount;
    float Contrast;
    float Brightness;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;
    // Pushes brights up and darks down around a mid-grey sum.
    color *= 1.0 + (color.r + color.g + color.b - 1.0) * Contrast * Strength;
    color = mix(color, vec3(ce_luma(color)), clamp(Amount * Strength, 0.0, 1.0));
    color *= mix(1.0, Brightness, Strength);
    fragColor = vec4(color, 1.0);
}
