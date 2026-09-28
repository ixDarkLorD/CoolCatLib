#version 150

#moj_import <coolcatcanvas:screen_effect.glsl>

uniform sampler2D DiffuseSampler;

uniform vec2 InSize;

uniform vec2 Direction;
uniform float Radius;

in vec2 texCoord;

out vec4 fragColor;

// One direction of a tent-weighted blur; the definition runs it horizontally, then vertically.
void main() {
    float radius = min(Radius * Strength, 64.0);
    if (radius < 0.5) {
        fragColor = texture(DiffuseSampler, texCoord);
        return;
    }
    vec2 texel = Direction / InSize;
    int steps = int(ceil(radius));
    vec4 sum = vec4(0.0);
    float total = 0.0;
    for (int i = -steps; i <= steps; i++) {
        float weight = 1.0 - abs(float(i)) / (radius + 1.0);
        if (weight <= 0.0) continue;
        sum += texture(DiffuseSampler, texCoord + texel * float(i)) * weight;
        total += weight;
    }
    fragColor = vec4((sum / total).rgb, 1.0);
}
