#version 150

#moj_import <coolcatcanvas:screen_effect.glsl>

uniform sampler2D DiffuseSampler;

uniform float Amount;
uniform float PulseSpeed;
uniform float Radial;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 fromCenter = texCoord - 0.5;
    float dist = length(fromCenter);
    float pulse = PulseSpeed > 0.0 ? 0.5 + 0.5 * sin(Time * PulseSpeed * 6.2831853) : 1.0;
    float amount = Amount * Strength * pulse * mix(1.0, dist * 2.0, clamp(Radial, 0.0, 1.0));
    vec2 offset = dist > 0.0001 ? fromCenter / dist * amount : vec2(0.0);

    float red = texture(DiffuseSampler, texCoord + offset).r;
    float green = texture(DiffuseSampler, texCoord).g;
    float blue = texture(DiffuseSampler, texCoord - offset).b;
    fragColor = vec4(red, green, blue, 1.0);
}
