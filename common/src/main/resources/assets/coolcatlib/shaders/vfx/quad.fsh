#version 330
#extension GL_ARB_separate_shader_objects : require

#include <coolcatlib:vfx.glsl>

uniform sampler2D Sampler0;
// The scene's depth. The pass has no depth attachment of its own, so the test against it is done here, which is also
// what lets the edge be soft.
uniform sampler2D DepthSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 texel = texture(Sampler0, texCoord);
    float alpha = VfxDepth.w > 0.5 ? dot(texel.rgb, vec3(0.2126, 0.7152, 0.0722)) : texel.a;
    alpha *= vertexColor.a * vfx_depth_fade(texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r, gl_FragCoord.z);
    if (alpha < 0.002) discard;
    fragColor = vec4(texel.rgb * vertexColor.rgb, alpha);
}
