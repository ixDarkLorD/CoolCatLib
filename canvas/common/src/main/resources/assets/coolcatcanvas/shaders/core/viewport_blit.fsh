#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// A scene viewport's picture shown on the screen (SceneViewportWidget). It's premultiplied, and its glowing sprites add
// light where nothing of the scene is drawn, so their pixels have colour but no alpha: unlike the GUI's own textured
// shader, this one keeps pixels whose alpha is 0, so that light is added onto whatever is behind the widget.
uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    fragColor = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
}
