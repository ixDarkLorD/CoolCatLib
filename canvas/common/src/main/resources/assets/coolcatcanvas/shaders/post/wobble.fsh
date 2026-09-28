#version 150

#moj_import <coolcatcanvas:screen_effect.glsl>

uniform sampler2D InSampler;

uniform float Amplitude;
uniform float Frequency;
uniform float Speed;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    float amplitude = Amplitude * Strength;
    vec2 uv = texCoord;
    uv.x += sin(texCoord.y * Frequency + Time * Speed) * amplitude;
    uv.y += cos(texCoord.x * Frequency * 0.8 + Time * Speed * 1.3) * amplitude;
    fragColor = vec4(texture(InSampler, uv).rgb, 1.0);
}
