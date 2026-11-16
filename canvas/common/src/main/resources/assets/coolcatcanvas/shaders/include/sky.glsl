#version 150

// Set by CoolCatLib: Canvas for every sky layer. A layer's fragment shader imports it with
//     #moj_import <coolcatcanvas:sky.glsl>
// and receives from the vertex shader:
//     in vec3 skyLocal;  // on the layer's mesh: a direction around the camera for full-sky layers (any length, so
//                        // normalize it), [-1, 1] across x and z for sprites; in the layer's own orientation
//     in vec3 skyWorld;  // the same point around the camera, in world orientation (y is up)
// A textured layer also gets `uniform sampler2D Sampler0;`. End with `fragColor = sky_finish(color, skyWorld);`.

// The camera's rotation.
uniform mat4 SkyViewMat;
// The layer's rotation, orientation and size.
uniform mat4 SkyModelMat;
// The layer's tint. Its alpha is the layer's opacity this frame, every fade included.
uniform vec4 SkyColor;
// x: the animation frame, y: the next one, z: how far into the next (0 unless interpolated), w: the frame count.
uniform vec4 SkyFrame;
// x: seconds of real time, y: seconds of game time (both wrap every hour), z: the day from 0 to 1 (0 at sunrise),
// w: vanilla's sun angle in radians.
uniform vec4 SkyTime;
// x: rain level, y: thunder level, z: the layer's fog amount, w: the skybox's visibility.
uniform vec4 SkyEnv;
// The layer's params, from its definition or code.
uniform vec4 SkyParams[4];

// Vanilla's sky fog.
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform int FogShape;

const float SKY_PI = 3.14159265358979;

// How fogged vanilla's sky is in this direction: 0 overhead in clear air, towards 1 at the horizon, all of it in
// water. Vanilla's sky is a disc 16 blocks up; this finds where the direction meets it.
float sky_fog_value(vec3 world) {
    vec3 direction = normalize(world);
    float distance = min(16.0 / max(abs(direction.y), 1.0e-4), 512.0);
    vec3 point = direction * distance;
    float fogDistance = FogShape == 0 ? length(point) : max(length(point.xz), abs(point.y));
    if (fogDistance <= FogStart) return 0.0;
    return fogDistance < FogEnd ? smoothstep(FogStart, FogEnd, fogDistance) : 1.0;
}

// The texture coordinate of `uv` (0 to 1 within one frame) in `frame`, for textures holding a flipbook of frames
// stacked top to bottom. Stays half a texel inside the frame, so filtering never bleeds in its neighbours.
vec2 sky_frame_uv(sampler2D tex, vec2 uv, float frame) {
    float frames = max(SkyFrame.w, 1.0);
    float halfTexel = 0.5 / float(textureSize(tex, 0).y);
    float v = clamp((frame + uv.y) / frames, frame / frames + halfTexel, (frame + 1.0) / frames - halfTexel);
    return vec2(uv.x, v);
}

// Samples the layer's current animation frame, cross-faded into the next when interpolated.
vec4 sky_texture(sampler2D tex, vec2 uv) {
    vec4 color = texture(tex, sky_frame_uv(tex, uv, SkyFrame.x));
    if (SkyFrame.z > 0.0) {
        color = mix(color, texture(tex, sky_frame_uv(tex, uv, SkyFrame.y)), SkyFrame.z);
    }
    return color;
}

// A direction as panorama coordinates: north at u = 0.5, east to its right, the zenith at v = 0.
vec2 sky_panorama_uv(vec3 direction) {
    direction = normalize(direction);
    return vec2(atan(direction.x, -direction.z) / (2.0 * SKY_PI) + 0.5, acos(clamp(direction.y, -1.0, 1.0)) / SKY_PI);
}

// Applies the layer's tint, opacity and fog to a color, then shapes it for the layer's blend mode.
vec4 sky_finish(vec4 color, vec3 world) {
    color *= SkyColor;
    float fog = clamp(sky_fog_value(world) * SkyEnv.z * FogColor.a, 0.0, 1.0);
#if defined(SKY_BLEND_ALPHA)
    color.rgb = mix(color.rgb, FogColor.rgb, fog);
#else
    // A layer that adds to or scales the sky fades away into fog instead of adding fog to it.
    color.a *= 1.0 - fog;
#endif
#if defined(SKY_BLEND_MULTIPLY)
    return vec4(mix(vec3(1.0), color.rgb, color.a), 1.0);
#elif defined(SKY_BLEND_SCREEN)
    return vec4(color.rgb * color.a, 1.0);
#else
    return color;
#endif
}
