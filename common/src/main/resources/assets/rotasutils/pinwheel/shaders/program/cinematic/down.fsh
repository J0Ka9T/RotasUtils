// Down to a sixth of the screen, averaging so nothing shimmers.
uniform sampler2D DiffuseSampler0;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 px = 1.0 / vec2(textureSize(DiffuseSampler0, 0));
    vec3 c = vec3(0.0);
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            c += texture(DiffuseSampler0, texCoord + px * vec2(x, y) * 1.5).rgb;
        }
    }
    fragColor = vec4(c / 9.0, 1.0);
}
