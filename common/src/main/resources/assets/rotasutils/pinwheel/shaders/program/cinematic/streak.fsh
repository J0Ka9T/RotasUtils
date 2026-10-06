// Anamorphic lens streak: the brightest light smeared far to either side, as a wide lens does, tinted cold.
uniform sampler2D DiffuseSampler0;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float px = 1.0 / float(textureSize(DiffuseSampler0, 0).x);
    vec3 c = vec3(0.0);
    float total = 0.0;
    for (int i = -24; i <= 24; i++) {
        float w = exp(-abs(float(i)) / 9.0);
        vec3 s = texture(DiffuseSampler0, texCoord + vec2(float(i) * px * 2.0, 0.0)).rgb;
        // only the hottest light makes a streak
        s *= smoothstep(0.35, 1.2, max(s.r, max(s.g, s.b)));
        c += s * w;
        total += w;
    }
    fragColor = vec4(c / total * 3.0 * vec3(0.55, 0.8, 1.2), 1.0);
}
