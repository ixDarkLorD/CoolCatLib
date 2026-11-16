#version 330
#extension GL_ARB_separate_shader_objects : require

// Bloom, pass 1: keeps what is brighter than Threshold, with a soft knee so nothing pops in or out.
uniform sampler2D InSampler;

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
    float brightness = max(color.r, max(color.g, color.b));
    float knee = max(Threshold * 0.5, 0.0001);
    float soft = clamp(brightness - Threshold + knee, 0.0, 2.0 * knee);
    soft = soft * soft / (4.0 * knee);
    float keep = max(soft, brightness - Threshold) / max(brightness, 0.0001);
    fragColor = vec4(color * keep, 1.0);
}
