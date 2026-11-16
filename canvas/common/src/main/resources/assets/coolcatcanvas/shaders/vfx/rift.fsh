#version 150

#moj_import <coolcatcanvas:vfx.glsl>

// A rift (WorldRifts): a jagged slit with a dark, starry inside and a bright crawling edge, drawn on one quad.
// There are no uniforms to say how open it is. The slit always spans y from -1 to 1 and is 1 wide at its middle in
// UV space; RiftManager stretches the quad's UVs past that, which is what makes the slit narrow or short on screen.
uniform sampler2D Sampler0;
uniform sampler2D DepthSampler;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        sum += noise(p) * amp;
        p = p * 2.03 + vec2(17.1, 5.3);
        amp *= 0.5;
    }
    return sum;
}

void main() {
    vec2 p = texCoord;
    float t = VfxTime.x;
    float within = 1.0 - p.y * p.y;
    float body = max(within, 0.0);

    // The slit wanders off its line and its edge is ragged; both crawl with time.
    float wander = (fbm(vec2(p.y * 2.5, t * 0.45)) - 0.5) * 0.9 * body;
    float ragged = (fbm(vec2(p.y * 7.0 + 11.0, t * 1.3)) - 0.5) * 0.55;
    float halfWidth = pow(body, 0.75) * (1.0 + ragged);
    float x = p.x - wander;
    // Distance to the edge: negative inside. Past the tips it is measured to the tip, squeezed so the glow dies fast.
    float d = within > 0.0 ? abs(x) - halfWidth : length(vec2(x, (abs(p.y) - 1.0) * 3.0));

    float inside = within > 0.0 ? smoothstep(0.05, -0.05, d) : 0.0;

    // Inside: somewhere else. Slow clouds of the rift's colour, and a few hard stars sliding past.
    vec2 q = vec2(x * 0.7, p.y) * 2.2;
    float clouds = fbm(q * 1.4 + vec2(t * 0.13, -t * 0.21) + fbm(q + t * 0.08) * 1.5);
    vec3 beyond = vertexColor.rgb * (0.04 + 0.4 * clouds * clouds);
    vec2 grid = (q + vec2(0.0, t * 0.18)) * 24.0;
    vec2 cell = floor(grid);
    float star = step(0.972, hash(cell)) * smoothstep(0.42, 0.05, length(fract(grid) - 0.5));
    beyond += vec3(star) * (0.55 + 0.45 * sin(t * 3.0 + hash(cell + 3.0) * 6.28));

    // The edge burns white-hot at the line and falls off into the rift's colour; it crackles along its length.
    float rim = exp(-abs(d) * 9.0);
    float halo = exp(-max(d, 0.0) * 2.2) * 0.5;
    float crackle = 0.72 + 0.28 * sin(t * 9.0 + p.y * 14.0 + fbm(vec2(p.y * 5.0, t * 2.0)) * 6.0);
    vec3 glow = mix(vertexColor.rgb, vec3(1.0), rim * 0.75) * (rim * 1.5 * crackle + halo);

    float alpha = clamp(max(inside, rim * 1.2 + halo), 0.0, 1.0);
    vec3 color = (beyond * inside + glow) / max(alpha, 0.001);
    alpha *= vertexColor.a * vfx_depth_fade(texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r, gl_FragCoord.z);
    if (alpha < 0.003) discard;
    fragColor = vec4(min(color, vec3(1.0)), alpha);
}
