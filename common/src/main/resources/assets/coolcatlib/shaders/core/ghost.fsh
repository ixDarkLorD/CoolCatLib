#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

// GhostBlocks laid over the world: their picture, drawn opaque with the world's own camera, where the world's depth
// lets it show. GHOST_MODE 0: only where the world is in front of them (through walls); 1: only where it isn't (as real
// blocks); 2: everywhere. ColorModulator holds the tint (rgb) and the opacity (a).
uniform sampler2D GhostSampler;
uniform sampler2D GhostDepthSampler;
uniform sampler2D DepthSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    // Where a ghost was drawn its depth is less than the cleared 1; its colour's alpha can't tell, as the block
    // pipelines don't all write alpha.
    // Depth is reversed: cleared to 0, nearer is larger.
    float ghostDepth = texelFetch(GhostDepthSampler, pixel, 0).r;
    if (ghostDepth <= 0.0) discard;
    float worldDepth = texelFetch(DepthSampler, pixel, 0).r;
#if GHOST_MODE == 0
    if (worldDepth <= ghostDepth) discard;
#elif GHOST_MODE == 1
    if (worldDepth > ghostDepth) discard;
#endif
    vec3 ghost = texelFetch(GhostSampler, pixel, 0).rgb;
    fragColor = vec4(ghost * ColorModulator.rgb, ColorModulator.a);
}
