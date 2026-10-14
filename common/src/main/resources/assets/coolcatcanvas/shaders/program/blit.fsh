#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;

out vec4 fragColor;

// Copies its input as it is, alpha included (vanilla's blit blends by it).
void main() {
    fragColor = texture(DiffuseSampler, texCoord);
}
