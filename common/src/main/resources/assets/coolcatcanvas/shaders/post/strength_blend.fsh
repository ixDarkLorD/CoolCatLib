#version 150

#moj_import <coolcatcanvas:screen_effect.glsl>

// Auto-blend: fades any effect by mixing the untouched frame with the processed one.
uniform sampler2D OriginalSampler;
uniform sampler2D ProcessedSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    fragColor = mix(texture(OriginalSampler, texCoord), texture(ProcessedSampler, texCoord), clamp(Strength, 0.0, 1.0));
}
