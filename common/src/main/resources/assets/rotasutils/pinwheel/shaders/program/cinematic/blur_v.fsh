// A nine-tap gaussian, linear-sampled (five fetches), along one axis.
uniform sampler2D DiffuseSampler0;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 step = vec2(0.0, 1.0) / vec2(textureSize(DiffuseSampler0, 0));
    vec3 c = texture(DiffuseSampler0, texCoord).rgb * 0.2270270270;
    c += texture(DiffuseSampler0, texCoord + step * 1.3846153846).rgb * 0.3162162162;
    c += texture(DiffuseSampler0, texCoord - step * 1.3846153846).rgb * 0.3162162162;
    c += texture(DiffuseSampler0, texCoord + step * 3.2307692308).rgb * 0.0702702703;
    c += texture(DiffuseSampler0, texCoord - step * 3.2307692308).rgb * 0.0702702703;
    fragColor = vec4(c, 1.0);
}
