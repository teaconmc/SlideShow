#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler3;

uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    float index = texture(Sampler0, texCoord0).r * 255.0;
    vec4 color = texture(Sampler3, vec2((index + 0.5) / 256.0, 0.5)) * vertexColor;
    if (color.a < 0.1) {
        discard;
    }
    fragColor = color * ColorModulator;
}
