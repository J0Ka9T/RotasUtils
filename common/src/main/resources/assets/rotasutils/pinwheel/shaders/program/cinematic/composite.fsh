// The cinematic look, all in one pass over the finished frame:
//  - bloom: a tight glow and a wide one around everything bright,
//  - an anamorphic streak through the hottest light,
//  - shafts of light falling from the focus (a radial blur of the bright parts toward it),
//  - and what the plain post pass did before: a pull and heat shimmer round the focus, a colour fringe,
//    a tinted vignette, a dimming of the whole picture, and a flash.
uniform sampler2D DiffuseSampler0;
uniform sampler2D BloomSampler;
uniform sampler2D WideSampler;
uniform sampler2D StreakSampler;
uniform sampler2D DepthSampler;

uniform float Time;
uniform float CenterX;
uniform float CenterY;
uniform float Radius;
uniform float Strength;
uniform float Chroma;
uniform float Vignette;
uniform float Flash;
uniform float Darken;
uniform vec3 Tint;
uniform float Bloom;
uniform float Rays;
uniform float Streak;
uniform float Corona;
uniform int AftermathCount;
uniform vec4 ImpactFields[4];
uniform float AftermathAmounts[4];
uniform mat4 InverseViewProjection;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 size = vec2(textureSize(DiffuseSampler0, 0));
    float aspect = size.x / max(size.y, 1.0);
    vec2 uv = texCoord;
    vec2 centre = vec2(CenterX, CenterY);
    vec2 d = uv - centre;
    d.x *= aspect;
    float r = length(d);
    float falloff = 1.0 - smoothstep(0.0, max(Radius, 0.001), r);
    vec2 dir = r > 0.0001 ? d / r : vec2(0.0);

    // pull toward the focus, and shimmer
    float pull = Strength * 0.10 * falloff * falloff;
    float shimmer = sin(r * 70.0 - Time * 10.0) * 0.003 * Strength * falloff;
    vec2 offs = dir * (shimmer - pull);
    offs.x /= aspect;
    vec2 sampleUv = uv + offs;

    // colour fringe, stronger toward the edges
    float edge = smoothstep(0.35, 0.95, length(uv - vec2(0.5)));
    vec2 fringe = (uv - vec2(0.5)) * Chroma * 0.018 * (0.4 + edge) + dir * Chroma * 0.006 * falloff;
    vec3 col;
    col.r = texture(DiffuseSampler0, sampleUv + fringe).r;
    col.g = texture(DiffuseSampler0, sampleUv).g;
    col.b = texture(DiffuseSampler0, sampleUv - fringe).b;

    // Reconstruct the visible surface so the crimson field stays on the terrain as the player turns or walks.
    float redField = 0.0;
    float emberPulse = 1.0;
    if (AftermathCount > 0) {
        float depth = texture(DepthSampler, sampleUv).r;
        if (depth < 0.999999 && depth > 0.0) {
            vec4 surface = InverseViewProjection * vec4(sampleUv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
            vec3 position = surface.xyz / max(surface.w, 0.000001);
            for (int i = 0; i < 4; i++) {
                if (i >= AftermathCount) break;
                float distanceToImpact = length(position - ImpactFields[i].xyz);
                float radius = ImpactFields[i].w;
                float field = (1.0 - smoothstep(radius * 0.72, radius, distanceToImpact)) * AftermathAmounts[i];
                if (field > redField) {
                    redField = field;
                    emberPulse = 0.85 + 0.15 * sin(distanceToImpact * 0.08 - Time * 0.7);
                }
            }
            float luminance = dot(col, vec3(0.2126, 0.7152, 0.0722));
            vec3 crimson = vec3(max(col.r, luminance * 1.45), luminance * 0.13, luminance * 0.19);
            crimson += vec3(0.075, 0.004, 0.008) * emberPulse;
            col = mix(col, crimson, redField * 0.90);
        }
    }

    // the picture dims and takes the tint before light is added on top of it
    col *= 1.0 - Darken * (0.35 + 0.65 * edge);
    col = mix(col, col * (Tint * 0.45 + 0.55), edge * Vignette);

    // bloom: tight + wide
    vec3 bloom = texture(BloomSampler, sampleUv).rgb * 0.55 + texture(WideSampler, sampleUv).rgb * 0.6;
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    col += bloom * Bloom * (1.0 - 0.6 * smoothstep(0.55, 1.0, lum));

    // streak
    col += texture(StreakSampler, sampleUv).rgb * Streak * Bloom;

    // A faint diffraction crown appears only around the charged focal light.
    if (Corona > 0.001 && Radius > 0.001) {
        float angle = atan(d.y, d.x);
        float crown = pow(0.5 + 0.5 * cos(angle * 8.0 + Time * 0.15), 12.0);
        float halo = exp(-pow((r - Radius * 0.48) / max(0.008, Radius * 0.075), 2.0));
        float source = clamp(dot(texture(WideSampler, centre).rgb, vec3(0.333)), 0.0, 1.0);
        col += vec3(1.0, 0.69, 0.31) * halo * crown * Corona * source * 0.12;
    }

    // shafts of light from the focus
    if (Rays > 0.001 && CenterX > -2.0) {
        vec2 toward = centre - uv;
        vec3 shafts = vec3(0.0);
        float weight = 1.0, total = 0.0;
        const int N = 28;
        for (int i = 0; i < N; i++) {
            vec2 p = uv + toward * (float(i) / float(N)) * 0.92;
            shafts += texture(BloomSampler, p).rgb * weight;
            total += weight;
            weight *= 0.965;
        }
        shafts /= total;
        float near = 1.0 - smoothstep(0.0, 1.1, length(vec2(toward.x * aspect, toward.y)));
        col += shafts * Rays * (0.7 + 0.8 * near) * (Tint * 0.4 + 0.75);
    }

    col += Tint * 0.65 * edge * Vignette * 0.20;
    col += (Tint * 0.4 + 0.6) * Flash * 0.12 * falloff;

    // keep the hottest light from clipping flat: a soft shoulder above 0.8
    vec3 over = max(col - 0.8, 0.0);
    vec3 shoulder = min(col, 0.8) + over / (1.0 + over * 1.6);
    col = mix(col, shoulder, clamp(Bloom + redField + Flash + Strength, 0.0, 1.0));

    fragColor = vec4(col, 1.0);
}
