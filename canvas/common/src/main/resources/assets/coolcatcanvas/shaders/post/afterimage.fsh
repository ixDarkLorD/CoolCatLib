#version 150

#moj_import <coolcatcanvas:screen_effect.glsl>

uniform sampler2D InSampler;
uniform sampler2D HistorySampler;

uniform float Decay;

in vec2 texCoord;

out vec4 fragColor;

// Mixes in what was drawn last frame; that result becomes next frame's history.
void main() {
    vec3 current = texture(InSampler, texCoord).rgb;
    vec3 history = texture(HistorySampler, texCoord).rgb;
    fragColor = vec4(mix(current, history, clamp(Decay, 0.0, 0.98) * Strength), 1.0);
}
