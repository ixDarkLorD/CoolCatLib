#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// GhostBlocks laid over the world: their picture, drawn opaque with the world's own camera, where the world's depth
// lets it show. GHOST_MODE 0: only where the world is in front of them (through walls); 1: only where it isn't (as real
// blocks); 2: everywhere; 3: as 1, for ghosts drawn over the real blocks they cover. ColorModulator holds the tint (rgb)
// and the opacity (a). GHOST_ALPHA 1: the picture holds each ghost's own opacity too (groups drawn together).
uniform sampler2D GhostSampler;
uniform sampler2D GhostDepthSampler;
uniform sampler2D DepthSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    // Where a ghost was drawn its depth is less than the cleared 1; its colour's alpha can't tell, as the block
    // pipelines don't all write alpha.
    float ghostDepth = texelFetch(GhostDepthSampler, pixel, 0).r;
    if (ghostDepth >= 1.0) discard;
    float worldDepth = texelFetch(DepthSampler, pixel, 0).r;
#if GHOST_MODE == 0
    if (worldDepth >= ghostDepth) discard;
#elif GHOST_MODE == 1 || GHOST_MODE == 3
    if (worldDepth < ghostDepth) discard;
#endif
    vec4 ghost = texelFetch(GhostSampler, pixel, 0);
#if GHOST_ALPHA == 1
    float opacity = ColorModulator.a * ghost.a;
#else
    float opacity = ColorModulator.a;
#endif
    fragColor = vec4(ghost.rgb * ColorModulator.rgb, opacity);
}
