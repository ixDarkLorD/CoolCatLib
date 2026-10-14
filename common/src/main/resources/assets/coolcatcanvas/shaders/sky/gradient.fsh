#version 150

#moj_import <coolcatcanvas:sky.glsl>

in vec3 skyLocal;
in vec3 skyWorld;

out vec4 fragColor;

// SkyParams[0]: zenith color, [1]: horizon color, [2]: nadir color, [3].x: horizon sharpness (1 when 0).
void main() {
    float height = normalize(skyLocal).y;
    float sharpness = SkyParams[3].x > 0.0 ? SkyParams[3].x : 1.0;
    float t = pow(abs(height), 1.0 / sharpness);
    vec4 color = height >= 0.0 ? mix(SkyParams[1], SkyParams[0], t) : mix(SkyParams[1], SkyParams[2], t);
    fragColor = sky_finish(color, skyWorld);
}
