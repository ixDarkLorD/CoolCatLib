// GhostBlocks.drawEffect: a mod's own shader laid over the world where ghosts show. GHOST_MODE says where they show
// (0 through walls, 1 in the world, 2 always, 3 over their own blocks), as for core/ghost.
layout(std140) uniform GhostEffect {
    // From the screen (normalized device coordinates) back to the world, relative to the camera.
    mat4 GhostInverseViewProjection;
    // xyz: the camera's place in the world. w: how far towards the camera the ghosts were drawn (1 for not at all).
    vec4 GhostCamera;
    // The effect's own numbers.
    vec4 GhostParams0;
    vec4 GhostParams1;
    vec4 GhostParams2;
    vec4 GhostParams3;
};

uniform sampler2D GhostSampler;
uniform sampler2D GhostDepthSampler;
uniform sampler2D DepthSampler;
uniform sampler2D EffectSampler;

// The ghosts' depth at a pixel where one shows, or -1 where none does (nothing drawn, or the world in the way).
float ghost_depth(ivec2 pixel) {
    float ghostDepth = texelFetch(GhostDepthSampler, pixel, 0).r;
    if (ghostDepth >= 1.0) return -1.0;
    float worldDepth = texelFetch(DepthSampler, pixel, 0).r;
#if GHOST_MODE == 0
    if (worldDepth >= ghostDepth) return -1.0;
#elif GHOST_MODE == 1 || GHOST_MODE == 3
    if (worldDepth < ghostDepth) return -1.0;
#endif
    return ghostDepth;
}

// The ghost's own colour at a pixel.
vec4 ghost_color(ivec2 pixel) {
    return texelFetch(GhostSampler, pixel, 0);
}

// Where in the world a pixel of a ghost is (gl_FragCoord.xy, and its depth from ghost_depth).
vec3 ghost_world_position(vec2 fragCoord, float depth) {
    vec2 size = vec2(textureSize(GhostDepthSampler, 0));
    vec4 clip = vec4(fragCoord / size * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 relative = GhostInverseViewProjection * clip;
    return relative.xyz / relative.w / GhostCamera.w + GhostCamera.xyz;
}
