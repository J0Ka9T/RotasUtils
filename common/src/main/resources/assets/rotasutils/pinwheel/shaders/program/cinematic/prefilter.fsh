// Halves the picture and keeps only what is bright: a soft knee, so light does not switch on at a hard line.
uniform sampler2D DiffuseSampler0;
uniform float Threshold;

in vec2 texCoord;
out vec4 fragColor;

vec3 tap(vec2 uv) {
    return texture(DiffuseSampler0, uv).rgb;
}

void main() {
    vec2 px = 1.0 / vec2(textureSize(DiffuseSampler0, 0));
    vec3 c = (tap(texCoord + px * vec2(-1.0, -1.0)) + tap(texCoord + px * vec2(1.0, -1.0))
            + tap(texCoord + px * vec2(-1.0, 1.0)) + tap(texCoord + px * vec2(1.0, 1.0))) * 0.25;
    float bright = max(c.r, max(c.g, c.b));
    float knee = 0.25;
    float soft = clamp(bright - Threshold + knee, 0.0, 2.0 * knee);
    soft = soft * soft / (4.0 * knee + 1.0e-4);
    float keep = max(soft, bright - Threshold) / max(bright, 1.0e-4);
    fragColor = vec4(c * keep, 1.0);
}
