#version 150

#moj_import <coolcatcanvas:screen_effect.glsl>

uniform sampler2D DiffuseSampler;

uniform vec4 Color;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    fragColor = vec4(mix(color, Color.rgb, clamp(Color.a * Strength, 0.0, 1.0)), 1.0);
}
