#version 150

// A scene viewport's picture shown on the screen (SceneViewportWidget). It's premultiplied, and its glowing sprites add
// light where nothing of the scene is drawn, so their pixels have colour but no alpha: unlike the GUI's own textured
// shader, this one keeps pixels whose alpha is 0, so that light is added onto whatever is behind the widget.
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    fragColor = texture(Sampler0, texCoord0) * ColorModulator;
}
