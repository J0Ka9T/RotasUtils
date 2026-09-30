#version 150

uniform sampler2D DiffuseSampler;

uniform vec2 InSize;
uniform vec2 OutSize;
uniform float Time;
uniform float CenterX;
uniform float CenterY;
uniform float Radius;
uniform float Strength;
uniform float Chroma;
uniform float Vignette;
uniform float Flash;
uniform float Aspect;
uniform vec3 Tint;
uniform float Darken;

in vec2 texCoord;

out vec4 fragColor;

// Bends the picture round the Red core: a pull toward it, a heat shimmer, a brief colour fringe, a red
// vignette and a red bloom. Everything scales with the uniforms, so with Strength 0 it is the identity.
void main() {
    vec2 uv = texCoord;
    vec2 d = uv - vec2(CenterX, CenterY);
    d.x *= Aspect;
    float r = length(d);
    float falloff = 1.0 - smoothstep(0.0, max(Radius, 0.001), r);
    vec2 dir = r > 0.0001 ? d / r : vec2(0.0);

    float pull = min(Strength * 0.10 * falloff * falloff, r * 0.6);
    float shimmer = sin(r * 70.0 - Time * 10.0) * 0.003 * Strength * falloff;
    vec2 offs = dir * (shimmer - pull);
    offs.x /= Aspect;
    vec2 sampleUv = uv + offs;

    vec2 fringe = dir * (Chroma * 0.011 * (0.35 + falloff));
    fringe.x /= Aspect;
    vec3 col;
    col.r = texture(DiffuseSampler, sampleUv + fringe).r;
    col.g = texture(DiffuseSampler, sampleUv).g;
    col.b = texture(DiffuseSampler, sampleUv - fringe).b;

    col += Tint * (Strength * 0.22 * pow(falloff, 3.0));

    float edge = smoothstep(0.35, 0.95, length(uv - vec2(0.5)));
    col = mix(col, col * (Tint * 0.45 + 0.55), edge * Vignette);
    col += Tint * 0.65 * edge * Vignette * 0.22;
    col *= 1.0 - Darken * (0.35 + 0.65 * edge);
    col += (Tint * 0.4 + 0.6) * Flash * 0.12 * falloff;

    fragColor = vec4(col, 1.0);
}
